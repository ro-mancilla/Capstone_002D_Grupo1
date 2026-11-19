# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# ecopan — agent guidance

An offline-first **bakery management system**, not a generic template. Five stacks in one repo:

| Stack | Path | What it is |
|---|---|---|
| KMP clients | `composeApp`, `core/*`, `data/*`, `feature/*`, `mobile/*`, `jvm/*`, `android/app`, `iosApp` | Compose Multiplatform — desktop POS till + baker's mobile client |
| Backend | `backend/*` | Ktor multi-module (`app`, `worker`, `notifier`, `data`). **Not** KMP, shares no code with the clients |
| Web dashboard | `webApp` | React 19 + Vite + TanStack Router/Query + shadcn/ui. Manager-only, Spanish-only |
| ML service | `ml` | Python 3.14 + FastAPI + LightGBM demand forecasting |
| Infra | `docker-compose.yml`, `infra/` | Postgres, RabbitMQ, and all three backend services + `ml` |

`plans/` holds the design documents the implementation follows (`00`–`11`); code comments cite them
as e.g. "plan 07 §4". `spec/SPECS.md` is the product spec. **`CODING_STYLE.md` is the authoritative
style guide for KMP code** (1200+ lines: visibility, DI, Room, UDF, navigation) — read it before
writing client code rather than inferring conventions.

`PROGRESS.md` tracks what is built vs. what the plans still promise — read it before
starting work, and update it when a gap closes.

## Commands

Each stack has its own toolchain; there is no unified build.

```shell
# --- KMP clients (JDK 17 toolchain) --------------------------------------------------
./gradlew :android:app:assembleDebug          # Android APK
./gradlew :jvm:app:run                        # desktop POS till
./gradlew :<module>:compileKotlin<Ios|Android|Jvm>   # after any change, for touched targets
./gradlew test                                # JVM/Android unit tests — see caveat below
./gradlew :<module>:jvmTest --tests "com.f776.Foo.bar"   # a single test
# iOS: open iosApp/ in Xcode. Compilation works headless; *linking* needs full Xcode.

# --- Backend (JDK 21 toolchain) ------------------------------------------------------
./gradlew :backend:app:build :backend:worker:build :backend:notifier:build
./gradlew :backend:data:test                  # needs Docker — Testcontainers spins up Postgres
./gradlew :backend:app:test                   # needs the compose db+rabbitmq UP on 5432/5672 —
                                              # ServerTest boots the real module against
                                              # application.yaml's localhost defaults (override
                                              # with DB_PORT=/RABBITMQ_PORT= if remapped)
./gradlew :backend:worker:test --tests "*ForecastRefresh*"

# --- Web dashboard (cd webApp) -------------------------------------------------------
npm run dev                                   # Vite dev server, expects the backend on :8080
npm run build                                 # vite build && tsc -b (vite generates routeTree.gen.ts)
npm run lint                                  # oxlint
npm test                                      # vitest run
npm test -- src/path/to/file.test.tsx         # a single test file

# --- ML (cd ml, uv-based) ------------------------------------------------------------
make install                                  # venv + deps
make check                                    # lint + test (what CI runs)
uv run pytest tests/test_x.py::test_y         # a single test
make serve                                    # inference API on :8000
make data baselines train evaluate            # the pipeline, in order (see ml/README.md)

# --- End-to-end (throwaway stack ecopan-acc, never the dev stack; see docs/e2e/README.md) ------
scripts/e2e/run.sh web                        # dashboard in a real browser (Playwright)
scripts/e2e/run.sh till                       # desktop till, Compose UI tests against the real backend
scripts/e2e/run.sh android                    # baker app on an emulator (:mobile:e2e commonTest journeys, adb reverse)
scripts/e2e/run.sh ios                        # the same journeys on the simulator (needs full Xcode and a simulator)
scripts/e2e/run.sh all                        # web, till, android and ios in turn

# --- Full stack ----------------------------------------------------------------------
docker compose up -d --build                  # db, rabbitmq, web:8080, worker×2, notifier, ml:8000
```

**`./gradlew test` does not reach KMP test tasks** — the root lifecycle task only covers plain
JVM/Android modules. For a touched KMP module also run `:<module>:jvmTest`. **A `:mobile:*` module
has no `jvmTest`**: its `commonTest` runs on the Android host target via
`:<module>:testAndroidHostTest`, which `AndroidLibraryConventionPlugin` enables automatically for
any module with `commonTest` and no JVM target. Kotlin/Native also rejects commas in backtick-quoted
test names.

**`npm test` must go through the npm script**, not bare `vitest` — it sets
`NODE_OPTIONS=--no-experimental-webstorage`, without which `window.localStorage` is undefined and
every test touching storage or theme fails confusingly.

## Client module tiers — enforced by build targets, not convention

The two KMP clients are kept apart by **which Gradle variants a module publishes**, so a wrong
dependency fails to resolve instead of merely violating a rule:

- `:feature:*` — shared by *every* client (e.g. `:feature:auth`'s PIN/session unlock).
- `:jvm:feature:<name>:{api,impl}` — desktop till only (e.g. `:jvm:feature:pos`). Uses
  `com.f776.kotlinMultiplatformDesktop`, does **not** apply `androidKmpLibrary` → JVM artifact only.
- `:mobile:feature:<name>:{api,impl}` — baker mobile only, Android + iOS (e.g.
  `:mobile:feature:production`). Uses `com.f776.kotlinMultiplatformMobile` (standard KMP minus `jvm()`).

Same `api`/`impl` split as `:feature:*`, just relocated — don't invent a new shape. Two consequences
when moving a module into a client tier: an `api` module must now apply `androidKmpLibrary`
explicitly (it used to resolve its `jvm` variant on Android as a silent fallback), and its Compose
Resources package changes with the path (`ecopan.mobile.feature.production.impl.generated.resources`).

**Which client sees which screens** lives in `composeApp/src/*/kotlin/.../navigation/TopLevelDestinations.*.kt`
— an `expect val` of nav-suite tabs plus `platformRoutes()` registering that platform's `NavKey`s.
`composeApp` has a `mobileMain` source set (androidMain + iosMain both `dependsOn` it) so the two
baker builds share one copy. Per-client DI splits the same way: `PosModule` from the `jvmMain`
`PlatformModule`, `MobileModule` from the Android/iOS ones.

`:data:example` (Notes) and `:feature:home` are leftover **template reference code**, not live
features — useful as shape examples, not as domain.

## Client architecture

- **Compose UI uses `:core:designsystem`.** Colours, type, spacing and shapes come from `EcopanTheme`
  (`EcopanTheme.colors`, `.typography`, `EcopanSpacing`, `EcopanShapes`) and screens are built from its
  components (`EcopanButton`, `EcopanCard`, `EcopanAlert`, `StatusPill`, `SyncStatusIndicator`, ...), not
  from raw `MaterialTheme` colours or hardcoded dp/sp sizes. If a component or token is missing, add it to
  the design system (and its gallery entry and README line) rather than styling around it in a feature
  module. `SyncIndicatorState` lives there too; `:core:ui` keeps only the domain-free helpers.

- **DI: Koin.** Platform deps use `expect val PlatformModule: Module`, with an actual in every
  `androidMain`/`iosMain`/`jvmMain`. Koin's "last module loaded wins" is how per-client overrides work.
- **No hardcoded dispatchers.** Inject via `named("ioDispatcher")` / `named("defaultDispatcher")` /
  `named("mainDispatcher")`.
- **Room is KMP** (`:core:database`, package root `com.f776.unigrid.core.database` — a known,
  intentional inconsistency with the `com.f776.ecopan` namespace; don't "fix" it). `BundledSQLiteDriver`
  on iOS. Schemas exported to `$projectDir/schemas` and version-controlled.
- **Navigation3** (adaptive) with Koin integration; top-level destinations are per client (above).
- **Backend host: one BuildKonfig constant.** `BACKEND_BASE_URL` is generated in `:data:sync:impl` and
  bound once in `SyncDataModule` as `named("backendBaseUrl")`. Every target defaults to
  `http://localhost:8080`; Android reaches the host via `adb reverse tcp:8080 tcp:8080` (README §4),
  which is why there are no per-platform URL constants — do not reintroduce them.
  Override with `-Pecopan.backendHost=<host>`.
- `-Xexpect-actual-classes` is enabled across all source sets; `jvmToolchain(17)`; namespaces derive
  from module path via the convention plugins; common test utilities in `:core:test`.

## Backend

Three services over one shared `:backend:data` (Exposed repositories + broker clients). The broker is
swappable at **runtime**, not build time: `BrokerBackend` selects RabbitMQ or Azure Service Bus, and
both families are always on the classpath (`messagebroker/rabbitmq` and `messagebroker/servicebus`).

Two live async pipelines, both worth reading before adding a third:

1. **Production batch** — `POST` a batch → `ProductionBatchMessage` → `worker`'s
   `FormulaDecrementProcessorImpl` (largest-remainder ingredient decrement, idempotent via a
   `(sourceBatchId, ingredientId)` unique guard) → `BatchProcessed` → `notifier` aggregates →
   `SsePushEvent` → `app` fans out to browsers over `GET /events/sse`.
2. **Forecast refresh** — F2 `POST /forecasts/refresh` → `ForecastRefreshRequested` → `worker`'s
   `ForecastRefreshProcessorImpl` reads 90 days of sale lines, buckets them into
   reporting-timezone demand days, calls the ML service's `POST /forecast` (`X-API-Key`), and upserts
   `forecasts`. Read via F1 `GET /forecasts`.

To add a pipeline, mirror those: entity + repository in `backend/data/database/`, message model in
`backend/data/messagebroker/model/`, publish from a router in `backend/app/feature/<x>/controller/`,
consume in `backend/worker/domain/` (registered in `WorkerModule`), aggregate in `backend/notifier/`
if it feeds a batch progress UI.

The reporting timezone must agree between `backend:app` and `backend:worker`, and they read it under
**different key styles**: the app takes `reporting.tz` from its Ktor application config, the worker
takes the `REPORTING_TZ` env var. Both default to `America/Santiago`. Analytics bucketing and
forecast day-bucketing both use it, so a mismatch silently misaligns K8's forecast-vs-actual join
rather than failing.

## Web dashboard (`webApp`)

React 19, TanStack Router (file-based, `routeTree.gen.ts` is generated) + Query, Tailwind v4,
shadcn/ui on the **`base-nova` style**, MSW-backed tests. **Zero hardcoded UI strings** — everything
a user reads comes from `src/lib/i18n.ts` (`es-CL`), and wire enums/ids go through `src/lib/labels.ts`.

**shadcn here is Base UI, not Radix.** The APIs differ in ways that type-check fine and fail
silently at runtime — two bugs shipped this way:

- `<Select.Value>` renders the selected **value**, not the item's text. Every `<Select>` root must be
  passed an `items={[{value, label}]}` map or the trigger shows `__all__` / a raw UUID / `WEIGHT`.
- `Menu.Item` (so `DropdownMenuItem`) has **`onClick`**, not `onSelect`. React binds a stray
  `onSelect` as the DOM text-selection event, so the handler simply never runs.

When in doubt about a Base UI prop, check the docs via context7 — do not assume the Radix shape.

Charts use shadcn's `ChartContainer` over recharts. `--chart-1..5` is a **validated categorical
palette** (CVD-checked in both modes, ember/petrol first because that pair carries forecast-vs-actual)
— it is assigned in fixed order and **never cycled**: past five categories, fold the tail into one
"Otros" bucket (see `SalesPage`'s `MAX_STACK_SERIES`) rather than reusing a hue.

## ML service (`ml`)

Stateless FastAPI: history travels in the request body, so a forecast is a pure function and the
service holds no tenant state or DB. `POST /forecast` is the whole external surface; `backend:worker`
is its only caller. Per-series routing: ≥ `MIN_HISTORY_FOR_MODEL` (28 days, derived from the feature set's
longest lookback) uses LightGBM, anything
shorter falls back to a labelled cold-start baseline — and `model_version` on every row records which,
so the dashboard never implies a trained model produced a baseline number. Series whose branch/product
key is not in the artifact's encoding (every bakery series) also go to the baseline, whatever their
history (`ROUTE_UNSEEN_TO_BASELINE`; the pre-registered `make transfer` failed, RESULTS §4.7). A missing
artifact is not fatal at runtime: the service starts in baseline-only mode and says so on `/health`
(`model_loaded`, `model_version`). But the **image build** now fails without `ml/data/artifacts`
(opt out with `--build-arg ALLOW_BASELINE_ONLY=true` / `deploy.sh --ml-baseline-only`; compose sets
it and bind-mounts the artifact), and `deploy.sh` checks `/health` after rollout.

`RESULTS.md` is the evaluation record and is written under pre-registration discipline — the test
window's scoring is stated *and committed* before `make evaluate` runs. If you re-score it, say so
there and publish the number whatever it is.

## Tools

- **Use context7** (`.mcp.json` also wires the shadcn MCP) to check API signatures for any library
  before writing code — Kotlin/Native `platform.*` interop, Base UI, Room's KMP API (`migrate` is a
  plain `fun`, not `suspend`, in androidx.room 2.8.x), Exposed, recharts. Never search `.def`/`.jar`
  files or the Kotlin/Native filesystem for API definitions.
- The `writing-kmp-features` skill in `.claude/skills/` covers the client feature recipe end to end.

## Agent skills

### Issue tracker

GitHub Issues on `GreatTusk/ecopan`, via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical roles, unrenamed. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
