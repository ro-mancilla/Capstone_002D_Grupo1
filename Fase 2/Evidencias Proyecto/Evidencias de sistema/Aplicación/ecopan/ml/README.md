# ecopan — ML demand forecasting

7-day demand forecasts per (branch, product_type), served to the Kotlin backend over HTTP.
LightGBM over Corporación Favorita retail data, with labelled naive baselines for cold start.

**Read [RESULTS.md](RESULTS.md) first** — it carries the evaluation-validity argument and every
quantitative claim. This file is how to run things.

---

## Quick start

```shell
cd ml
make install          # uv sync
make test             # 44 tests, no data or model required
make serve            # API on :8000 — works with no trained model (baseline-only mode)
```

`make help` lists every target.

### Python version

Pinned to **3.14** (`.python-version`). Plan 08 §1 originally specified 3.12 out of caution about
wheel availability; 3.14 was verified before adopting it — LightGBM 4.7, pandas 3.0, pyarrow 25,
scikit-learn 1.9, FastAPI 0.140 and numpy 2.5 all install and run, and LightGBM trains.

**macOS prerequisite:** LightGBM's wheel links against the OpenMP runtime, which Apple's toolchain
does not ship.

```shell
brew install libomp
```

Without it, `import lightgbm` fails with `Library not loaded: @rpath/libomp.dylib`. Linux wheels
bundle it; no action needed there.

---

## Layout

```
configs/favorita.yaml       every knob for a run: paths, splits, features, hyperparameters, threshold
src/ecopan_ml/
  config.py                 typed config loader; snapshotted next to each model artifact
  pipeline.py               CLI behind the make targets
  data/                     download.py (Kaggle), preprocess.py (raw CSV -> dense daily parquet)
  features/build.py         THE feature pipeline — used by training AND serving, no second copy
  baselines/naive.py        same-weekday-last-week, 7d moving average, cold-start ladder
  models/                   train.py (LightGBM + early stopping), predict.py (recursive 7-step)
  eval/                     metrics.py, rolling_origin.py, report.py, plots.py
  service/                  app.py (FastAPI), schemas.py (pydantic contract)
  demo/generate.py          synthetic demo data — QUARANTINED, never evaluated on
tests/                      44 tests: metrics, leakage, splits, baselines, service, quarantine
```

The source files carry the theory: why WAPE and not MAPE, why rolling-origin and not a random
split, why LightGBM and not ARIMA/Prophet/deep learning, why recursive multi-step and what it costs,
why the objective is `regression_l1`. They are written to be read.

---

## The pipeline, in order

The ordering is not a convenience — it is plan 08 §3's anti-target-shopping discipline.

```shell
make data        # Kaggle download + preprocess -> data/processed/favorita_daily.parquet
make baselines   # score BOTH baselines on VALIDATION, pick the reference
                 #   -> now record the numbers + threshold in RESULTS.md §3 and COMMIT
make train       # LightGBM, early stopping on validation
make evaluate    # score everything on the UNTOUCHED test window, print PASS/FAIL
make plots       # defence figures -> reports/plots/
make transfer    # unseen + rescaled series vs baseline-swlw, validation only (RESULTS.md §4.7)
```

The threshold is fixed after seeing *baseline* numbers and before seeing any *model* number, and the
commit timestamp is the evidence. `make evaluate` refuses to run if `threshold.reference_baseline`
was never recorded, rather than letting a reference be chosen after the fact.

### Data access

`make data` needs a Kaggle account with the **competition rules accepted** on the
[Favorita competition page](https://www.kaggle.com/c/favorita-grocery-sales-forecasting), plus an
API token at `~/.kaggle/kaggle.json`. Install the client with `uv sync --extra data` (kept out of
the default install so CI and the service image never pull it).

**Licensing.** Kaggle competition data is governed by that competition's rules, which must be read
before use — record the terms as they applied here once the download is done. Raw and derived data
are gitignored and never redistributed; only metrics and plots enter the repository, which is safe
under any plausible reading of the terms. If the terms turn out to prohibit this use, plan 08 §2
nominates **M5 (Walmart)** as the fallback, with `dept_id`/`cat_id` replacing `family`.

---

## The service

```shell
make serve        # fastapi dev, reload, :8000  -> /docs for the OpenAPI UI
make serve-prod   # fastapi run, what the container runs
```

Two endpoints (plan 08 §5):

| | |
|---|---|
| `GET /health` | liveness + `model_loaded` + `model_version` |
| `POST /forecast` | the entire external contract: per-series history in, 7-day predictions out |

```shell
curl -s localhost:8000/forecast -H 'content-type: application/json' -d '{
  "start_day": "2026-03-01",
  "horizon_days": 7,
  "series": [{
    "branch_id": "Sucursal Principal",
    "product_type": "Pan",
    "history": [{"day": "2026-01-01", "demand": 310}]
  }]
}' | jq
```

**Who calls it.** Only `backend:worker`. The flow is `POST /forecasts/refresh` on the Kotlin backend
→ `ForecastRefreshRequested` on the broker → worker reads that tenant's daily aggregates from
Postgres → posts them here → upserts the response into the `forecasts` table. Clients and the
dashboard read the cached rows through the backend (`GET /forecasts?branchId&from&to`) and never
talk to this service — architecture decision 01 §8. That keeps tenant authorisation entirely in the
Kotlin backend and leaves this service stateless.

**Auth.** Shared secret in `ML_API_KEY`, sent as `x-api-key`. If the variable is unset the check is
skipped, which is what makes local development and tests frictionless — **set it in any deployment.**

**Degraded mode.** With no `data/artifacts/model.txt` the service still starts and still forecasts,
using the cold-start baselines, and `/health` reports `model_loaded: false` and
`model_version: "baseline-only"` (deployed images refuse to build into this state unless asked to,
see "Shipping the model"). A baseline forecast
beats a 503, and it means the service is useful on day one of a deployment, before any model exists.

| Env var | Default | Meaning |
|---|---|---|
| `ML_ARTIFACT_DIR` | `data/artifacts` | where `model.txt` / `metadata.json` / `encoding.json` live |
| `ML_API_KEY` | *(unset — auth disabled)* | shared secret for `backend:worker` |

### Cold start

Per series, by observed history length:

| History | Estimator | `model_version` |
|---|---|---|
| ≥ 28 days | LightGBM | `lgbm-<dataset>` |
| 7–27 days | same-weekday-last-week | `baseline-swlw` |
| 1–6 days | trailing mean | `baseline-trailing-mean` |
| 0 days | tenant default | `baseline-default` |

28 = the feature set's longest lookback (`FeatureConfig.max_lookback`), i.e. the point where every
feature is computable from real observations rather than warm-up NaNs. It is derived rather than
chosen, so changing `lags`/`rolling_windows` moves it. The threshold was 42 until `make coldstart`
measured the curve and found it flat from 28 onward (RESULTS.md §4.6) — and that the model is
*worse* than the baseline at 7 days, which is what makes the fallback load-bearing. Routing
is **per series, not per request**, so a tenant's established product types keep model forecasts
while a new one is served a baseline. The `model_version` on every row is what lets the dashboard
say "estimate based on last week" instead of implying a trained model.

**Unseen series are baselines too.** A series whose `branch_id` or `product_type` is absent from
the artifact's `encoding.json` is served by the baseline however long its history is
(`ROUTE_UNSEEN_TO_BASELINE`). That is the consequence of the pre-registered transfer experiment
(`make transfer`, RESULTS.md §4.7): on unseen, gram-scale series the model scored WAPE 0.975 against
the baseline's 0.192. Until the bakery has its own training data, every bakery series is served by
`baseline-swlw`.

### Shipping the model

The container image **bakes in** the trained model (`data/artifacts/{model.txt, metadata.json,
encoding.json}`, ~24 MB; `model.txt` alone is ~23 MB). The artifact is gitignored, so the source of
truth is: **build the image on a machine that has run `make train`** (or has the artifact copied
into `ml/data/artifacts`). `infra/deploy.sh` uploads it with the build context; a GitHub-release
asset was considered and not chosen, to avoid a second distribution channel for a file that changes
a few times per project (see `infra/README.md` "ML model").

- **Fail loud.** `docker build ml` stops, with instructions, if the three files are missing or
  `model.txt` does not load with the image's own LightGBM. Without that, the service degrades to
  baselines without a word — which is how Azure came to serve baselines only.
- **Opt-out.** `docker build --build-arg ALLOW_BASELINE_ONLY=true ml` (deploy.sh:
  `--ml-baseline-only`) builds a deliberate baseline-only image. `docker compose` sets it, because
  compose bind-mounts `ml/data/artifacts` at runtime instead.
- **Check.** `GET /health` returns `model_loaded` and `model_version` (`lgbm-favorita`, or
  `baseline-only` when no model loaded). When `deploy.sh` builds the ML image it polls this after
  rollout and fails the deploy unless the model is loaded (CI never builds it; see `infra/README.md`).
- **Opt-out is absolute.** With `ALLOW_BASELINE_ONLY=true` no artifact is copied, even if present.

---

## Demo data

```shell
make demo-data    # -> data/demo/demo_sales.json
```

Synthetic, seeded, and **quarantined**: it exists so the live demo has populated charts, and it is
never read by any training or evaluation path. Enforced by `tests/test_demo_quarantine.py`, not by
convention — evaluating on data whose seasonality we programmed would be circular and would
invalidate everything in RESULTS.md.

---

## Development

```shell
make check    # lint + test, what CI runs
make fmt      # ruff autofix + format
```

Tests cover metrics (against hand-computed fixtures *and* sklearn), the feature pipeline's
no-leakage property, training/serving feature identity, the rolling-origin splitter's boundaries,
the baselines, the service contract, and the demo quarantine.

Deliberately **not** tested: model quality. Plan 08 §8 — the tests must stay green whatever the
metrics say, because a test that fails on a disappointing result is pressure to fudge the result.
Quality lives in RESULTS.md, where it can be reported honestly either way.
