# 08 — ML Demand Forecasting (Python `ml/`)

Plan for the Python ML deliverable. Zero production code here. Binding constraints: `01-architecture-decisions.md` §8 (backend proxies/reads forecasts), §9 (Python `ml/`, Favorita, FastAPI, Azure). Spec source: `spec/SPECS.md` "ML — Demand Forecasting", `spec/bakery_system_scope_spec.md` §9.

**The heart of this plan is the evaluation-validity argument** (spec §9): the reference client has *no sales history*, so model quality is proven on **real public retail data** with a legitimate holdout and a baseline that must be beaten. **Synthetic data is demo-only, never evaluated on.** Cold-start ("tenant day one has no history") is a deployment story, not an evaluation problem — and it is answered honestly: baseline-only forecasts until the tenant's own synced sales accumulate. The success threshold is deferred per spec, but its *setting procedure* is fixed below to prevent target-shopping.

---

## 1. Directory layout & tooling

```
ml/
  pyproject.toml          # uv-managed; requires-python = ">=3.12,<3.13"; deps: lightgbm, pandas,
                          #   pyarrow, scikit-learn (metrics/splits only), fastapi, uvicorn,
                          #   pydantic, httpx, matplotlib (plots); dev: ruff, pytest
  .python-version         # 3.12 (pinned)
  uv.lock                 # committed
  Makefile                # (or justfile — agent picks one, records choice) targets:
                          #   data / features / baselines / train / evaluate / plots / serve /
                          #   demo-data / lint / test
  README.md               # how to reproduce RESULTS.md end-to-end
  RESULTS.md              # baseline-vs-model table, threshold record — defense artifact (§7)
  configs/
    favorita.yaml         # single config file: paths, split dates, feature list, LightGBM params
  src/ecopan_ml/          # the package (src layout)
    data/                 #   download.py, preprocess.py (raw → aggregated parquet)
    features/             #   build.py (lags/rollings/calendar — one pipeline, used by train AND serve)
    baselines/            #   naive.py (same-weekday-last-week, 7d moving average)
    models/               #   train.py, predict.py (LightGBM only)
    eval/                 #   metrics.py (MAE/RMSE/WAPE), rolling_origin.py, report.py, plots.py
    service/              #   app.py (FastAPI), schemas.py (pydantic)
    demo/                 #   generate.py (synthetic seed generator — §6)
  notebooks/              # optional EDA only; nothing pipeline-critical lives in a notebook
  tests/                  # pytest — feature pipeline + metrics (§8)
  data/                   # gitignored (raw/, processed/); artifacts/ (model.txt, metrics.json) also gitignored
```

Rules: everything reproducible via `make <target>` from a clean checkout + Kaggle credentials; `uv run` for all invocations; no code duplicated between training and serving feature computation — `features/build.py` is the single source. Python 3.12 pinned (LightGBM/FastAPI both support it; do not drift to 3.13 without re-verifying wheels).

## 2. Dataset plan

**Primary: Corporación Favorita** (Kaggle competition `favorita-grocery-sales-forecasting`). Justification: daily grain, store × item, Ecuadorian *grocery* retail — the closest public domain match to per-(branch, product_type) bakery demand; real seasonality, real holidays, promotions, ~125M train rows, 54 stores, 2013–2017.

Files/columns (verified against competition writeups; implementation agent must re-confirm exact names/dtypes against the downloaded files before coding):

| File | Columns |
|---|---|
| `train.csv` | `id, date, store_nbr, item_nbr, unit_sales, onpromotion` (`onpromotion` has nulls; `unit_sales` can be negative = returns) |
| `items.csv` | `item_nbr, family, class, perishable` |
| `stores.csv` | `store_nbr, city, state, type, cluster` |
| `holidays_events.csv` | `date, type, locale, locale_name, description, transferred` |
| `transactions.csv`, `oil.csv` | auxiliary; optional features |

- **Download:** `kaggle competitions download -c favorita-grocery-sales-forecasting` (requires Kaggle account + accepted rules). `make data` wraps it; raw zips land in `ml/data/raw/`, never committed.
- **Licensing (to-verify):** Kaggle competition data is governed by the competition rules — typically permitted for academic/non-commercial use but **must be read and its terms recorded in `ml/README.md`**. Data is never redistributed in the repo (gitignored) — only derived metrics/plots are committed, which is safe regardless.
- **Fallback: M5 (Walmart)** (`m5-forecasting-accuracy`): daily unit sales per item × store, `dept_id`/`cat_id` as the family analog. Use only if Favorita access/licensing blocks; record the switch and re-map below accordingly.

**Domain mapping** (the argument that Favorita results transfer):

| Favorita | Ours |
|---|---|
| `store_nbr` | `branch` |
| `family` (aggregated over items) | `product_type` |
| Σ `unit_sales` per (store, family, date) | daily demand per (branch, product_type) |
| `onpromotion` share | promo flag (analog of our day-old discount-resell) |
| `holidays_events` | holiday calendar |

**Preprocessing** (`preprocess.py` → one parquet):
1. Optionally subset to a manageable slice for iteration speed (e.g. N stores × the food-adjacent families — `BREAD/BAKERY` family exists and is the flagship series; full config-driven, the final run uses the configured full scope). Record scope in `RESULTS.md`.
2. Clip negative `unit_sales` to 0 (returns are not demand), fill `onpromotion` nulls as False.
3. Aggregate to daily per `(store_nbr, family)`: `demand = Σ unit_sales`, `promo_share = mean(onpromotion)`. Reindex to a dense daily calendar per series, missing days = 0 (store closed vs. no-sale ambiguity: note it; Favorita convention is absent row = 0 sales).
4. Join national/regional holiday flags (respect `transferred`).
5. **Temporal splits** (dates in `configs/favorita.yaml`): train ≤ T₀, validation = next ~4 weeks (early-stopping/tuning), **test = final ~8 weeks (touched once, at the end)**. No shuffling, no leakage: every feature uses only data strictly before the target date.

## 3. Baselines FIRST, metrics, evaluation protocol, threshold procedure

**Implement and run baselines before any model code exists.** Two baselines:
1. **Same-weekday-last-week:** ŷ(d) = y(d − 7).
2. **7-day moving average:** ŷ(d) = mean(y(d−7 … d−1)).

**Metrics** (`eval/metrics.py`, own implementation, unit-tested):
- MAE, RMSE (per series and pooled),
- **WAPE** = Σ|y−ŷ| / Σy (scale-free — lets us pool across families of very different volume; report alongside MAE/RMSE since MAE alone is dominated by high-volume series).

**Protocol: rolling-origin evaluation.** Within the test window, forecast horizon = 7 days, step origin forward 7 days at a time, refit-free (model trained once on train+val for the final run; baselines recompute trivially). Aggregate metrics across all origins × series. Same protocol, same code path for baselines and model — never compare numbers produced by different evaluation code.

**Success-threshold procedure (fixed now; value deferred per spec):**
1. Run both baselines on the *validation* window. The better one (per pooled WAPE) becomes **the reference baseline**.
2. **Before any model tuning begins**, write into `ml/RESULTS.md`: the reference baseline's val/test-protocol metrics and the chosen threshold — of the form "model must beat the reference baseline's pooled MAE and WAPE by ≥ X% on the untouched test window", X picked once from the baseline run (guidance: 10–15% is typical for GBM-over-naive on retail daily data; agent picks and justifies). Commit that edit *before* the first tuning run — the git timestamp is the anti-target-shopping evidence.
3. Test window is evaluated once, after tuning freezes. If the model misses the threshold: report honestly, keep the baseline as the serving default, and say so at the defense — per spec, an unbeaten baseline means the ML is decorative, and pretending otherwise fails the validity argument.

## 4. Model plan — LightGBM, one model class, no detours

Single global LightGBM regressor over all (store, family) series (pooled training — standard for this data; per-series models are explicitly out of scope). No deep learning, no Prophet/ARIMA side-quests, no ensembling.

**Features** (`features/build.py`; exact list lives in `configs/favorita.yaml`):
- Lags: demand at t−1, t−7, t−14, t−28.
- Rolling means: 7d and 28d (computed over data ≤ t−1).
- Calendar: day-of-week, month, day-of-month, is-weekend.
- Holiday flag (and day-before-holiday flag).
- `promo_share` lag/current if available for the horizon (else drop — never use future-unknowable values as features).
- Categorical: `store_nbr`, `family` (LightGBM native categorical support).

**Training** (`models/train.py`): objective `regression_l1` (matches MAE; agent may compare `tweedie` for the zero-inflated series and record the choice), `lgb.train` with `valid_sets` + `lgb.early_stopping(stopping_rounds=…)` callback, then `bst.save_model('model.txt', num_iteration=bst.best_iteration)` (current stable API — re-verify via context7 at implementation). Multi-horizon: recursive 7-step prediction reusing the same feature builder (predict day 1, append, rebuild lags, predict day 2 …) — one model, honest about compounding error.

Everything driven by the one YAML config; `train.py` writes `data/artifacts/model.txt` + `metrics.json` + the config snapshot used, so any RESULTS.md number is reproducible from a git SHA + config.

## 5. Inference service (FastAPI) — batch-first

**Recommended pattern: batch precompute, DB-backed reads — driven from the backend worker.** Per `02-backend.md` (§3 F2, §7 work item 7), refresh is initiated backend-side: `POST /forecasts/refresh` (F2, 202) publishes a `ForecastRefreshRequested` message; `backend:worker` consumes it, reads the tenant's daily sales aggregates per (branch, product_type) straight from Postgres (the worker already has DB access — no history-export endpoint needed), calls this service's `POST /forecast` with the per-series history, and upserts the returned 7-day predictions into `02`'s `forecasts` table. Clients and the dashboard read the cache via `GET /forecasts?branchId&from&to` (F1) and compare via K8 — they never call the Python service directly (per `01` §8). This service builds features with the *same* `features/build.py` used in training.

> **Reconciled with `02-backend.md`:** the internal endpoints this plan originally proposed (`GET /internal/ml/sales-history`, `PUT /internal/ml/forecasts`, client-facing `GET /forecast/{branchId}?days=7`) are **superseded** — `02` inverted the flow (worker pulls history from its own DB and pushes to the ML service), so no backend internal ML endpoints exist. The adopted contract: `02`'s `forecasts` table is `(id, tenant_id, branch_id, product_type, forecast_date, predicted_grams NULL, predicted_units NULL, model_version, generated_at)` with upsert key `(tenant_id, branch_id, product_type, forecast_date)`; client read = F1 `GET /forecasts?branchId&from&to` (guarded `FORECAST_READ` + `DEMAND_FORECASTING`); refresh trigger = F2 `POST /forecasts/refresh` → `ForecastRefreshRequested`. The worker reaches this service via `ML_BASE_URL`/`ML_API_KEY` (`02` §7 env table); `POST /forecast` authenticates that key.

**FastAPI app** (`service/app.py`) — deliberately small:
- `GET /health` — liveness + loaded `model_version`.
- `POST /forecast` — **the whole external contract**: request body = per-series daily history (+ optional holiday flags), response = 7-day predictions per series (each labeled with `model_version`); called by `backend:worker` on `ForecastRefreshRequested` (auth: `ML_API_KEY` header per `02` §7), by tests, and by the demo. Pydantic models in `schemas.py`. The worker maps predictions onto `02`'s `predicted_grams`/`predicted_units` columns by the product type's `soldBy`.
- No admin/recompute endpoint here — the recompute trigger is backend-side (`02` F2 `POST /forecasts/refresh`); this service stays stateless per request.
- Model loaded once at startup via the **lifespan** context manager (current FastAPI pattern: `@asynccontextmanager async def lifespan(app)` … load `model.txt` … `yield` … cleanup; passed as `FastAPI(lifespan=…)`). CPU-bound predict runs in a threadpool (plain `def` endpoint or `run_in_executor`).

**Cold-start (honest deployment story, spec §9):** a series with < 14 days of history gets **baseline-only** forecasts — same-weekday-last-week where ≥ 7 days exist, else the trailing mean, else the tenant-configured default. Forecast rows carry `model_version` = `"baseline-swlw"` / `"lgbm-<sha>"` so the dashboard can label them. The pitch: the system's contribution is that it *starts collecting* the type-level sales data this bakery never recorded; the LightGBM model takes over per-series as history accumulates (switchover rule: series length ≥ max lag + margin, e.g. ≥ 42 days — record final value in config).

## 6. Demo/synthetic data — quarantined

`demo/generate.py`: seeded (`--seed`) generator producing plausible per-(branch, product_type) daily sales for the **two demo tenants** of `11-seeds-and-demo.md`: base volume per product_type × branch, weekday/weekend multiplier, mild weekly seasonality, Poisson/negative-binomial noise, a couple of holiday dips. Output format = whatever `11`'s seed loader ingests (reconcile there; likely SQL/JSON seed files consumed by the backend seeder). Hard rules: output lands under `data/demo/` or the seeds directory, is **never read by any `eval/` or `train.py` path** (enforced by a test asserting the training config's paths are disjoint from demo paths), and every generated file carries a `# DEMO DATA — synthetic, never for evaluation` header. Purpose: the stage demo shows plumbing (sales → sync → batch → forecast in dashboard), not model quality.

## 7. Defense deliverables

- **`ml/RESULTS.md`** — the artifact shown to the committee:
  1. Dataset scope + split dates.
  2. Threshold record (procedure §3, committed pre-tuning).
  3. Table: reference baseline vs. second baseline vs. LightGBM — MAE / RMSE / WAPE on the test window, pooled + a per-family breakdown for the bread/bakery family.
  4. Plots (from `eval/plots.py`, PNGs committed): predicted-vs-actual overlays for ~3 representative series (high/medium/low volume) across the test window.
  5. **The one-paragraph evaluation-validity argument**, verbatim-ready: real public data with real seasonality provides the legitimate test set our client cannot; the model beats (or does not beat — reported either way) a deliberately strong naive baseline under rolling-origin evaluation; synthetic data appears only in the live demo to exercise plumbing; the deployed system's cold-start is served by the same baseline and improves as the tenant's own history — which this system is the first to record — accumulates.

## 8. Repo/tooling quality bar

- **ruff** (lint + format) and **pytest** wired into `make lint` / `make test`; CI runs both (see `10`).
- Tests cover: metric implementations (hand-computed fixtures for MAE/RMSE/WAPE incl. zero-demand edge cases), feature pipeline (lag/rolling correctness on a toy frame; **no-leakage test**: feature values at date d unchanged when future rows are perturbed), rolling-origin splitter (window boundaries), baseline implementations, demo-vs-eval path disjointness (§6), FastAPI schema round-trip via `TestClient` with a stub model. **Explicitly not tested: model quality** — that is what `RESULTS.md` is for; tests must stay green regardless of metric values.

## 9. Work items (ordered)

1. **Scaffold `ml/`** — uv project, pyproject, `.python-version` 3.12, ruff+pytest config, Makefile with stub targets, gitignore for `data/`. *Accept:* `uv sync && make lint test` passes from clean checkout.
2. **Data download + preprocess** — Kaggle download target; `preprocess.py` → dense daily `(store_nbr, family)` parquet with holiday flags; column names re-verified against real files; license terms recorded in README. *Accept:* `make data` produces the parquet; row/series counts logged; splits defined in `configs/favorita.yaml`.
3. **Metrics + rolling-origin harness** — `eval/` with tests. *Accept:* metric unit tests pass against hand-computed fixtures.
4. **Baselines + threshold record** — both baselines through the harness on validation; reference baseline chosen; threshold committed to `RESULTS.md`. *Accept:* `RESULTS.md` contains baseline numbers + threshold, committed **before** any `models/` code runs.
5. **Feature pipeline** — `features/build.py` per §4, config-driven, leakage test green. *Accept:* feature frame builds for train and for a serving-shaped history slice from the same code path.
6. **LightGBM train/tune** — early stopping on validation; artifact + metrics + config snapshot persisted. *Accept:* `make train` reproducible; tuning touched only train/val.
7. **Final evaluation + plots + RESULTS.md** — one pass over test window, table + plots + validity paragraph. *Accept:* `RESULTS.md` complete; verdict vs. threshold stated plainly.
8. **FastAPI service** — health, `POST /forecast`, lifespan model load, cold-start baseline fallback, schemas + TestClient tests. *Accept:* `make serve` + smoke test returns 7-day predictions for a sample payload; short-history payload returns baseline-labeled predictions.
9. **Backend-integration contract** — freeze the `POST /forecast` request/response schema against `02`'s worker flow (`ForecastRefreshRequested` → worker → this service → `forecasts` upsert); `ML_API_KEY` auth; `model_version` labeling. *Accept:* the `02` worker (or a stub of it) round-trips: seeded tenant history in → forecast rows appear in the `forecasts` table and via F1.
10. **Demo generator** — `demo/generate.py` per §6, wired into `11`'s seed flow; disjointness test. *Accept:* seeded output identical across runs with same seed; eval paths provably untouched.
11. **Deploy hooks** — container/entrypoint expectations documented for `10-azure-deployment.md` (model artifact baked into image vs. mounted — decide there).

## Open questions

- **Favorita competition-rules wording** — exact license terms for use in an academic capstone: to-verify at download time; if restrictive, fall back to M5 and update §2 mapping.
- **`02-backend.md` reconciliation: done** — §5 now adopts `02`'s `forecasts` table, F1/F2 endpoints, and the `ForecastRefreshRequested` worker→ML flow; the previously proposed internal backend endpoints are superseded. Remaining detail to pin during item 9: the exact `POST /forecast` request/response JSON schema (history granularity, holiday flags, per-series ids) — owned here, consumed by `02`'s worker; and whether the worker or this service decides grams-vs-units (current answer: worker, by `soldBy`).
- **Objective choice** (`regression_l1` vs `tweedie`) and final training scope (families/stores subset vs full) — decided during item 6, recorded in `RESULTS.md`.
- **Batch scheduling mechanism on Azure** (Container Apps job vs cron in the service) — defer to `10`.
- **Day-old policy coupling** (spec: discard-loss vs discount-resell pairs with forecasts) — dashboard-side derived view (`07`) reading forecast + policy; no ML-side work beyond serving `predicted_qty`; confirm in `07`.
- **Model retraining cadence on tenant data** — out of scope for the capstone beyond the stated story (collect now, retrain later); confirm nothing in `02` promises per-tenant retraining.
