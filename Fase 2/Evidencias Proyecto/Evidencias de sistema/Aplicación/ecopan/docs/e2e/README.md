# End-to-end tests

Every suite drives a real frontend against the real backend, on the **throwaway acceptance stack**: its own compose project
(`ecopan-acc`), host ports (18080 API, 15432, 15673, 11000, dashboard on 14173) and volumes. They never touch the developer's
stack, its ports or its volumes (product photos live there). Spec: #145. What each test covers: [COVERAGE.md](COVERAGE.md).

```shell
scripts/e2e/run.sh web              # dashboard, Playwright: Chromium (+ WebKit for the two-tab refresh)
scripts/e2e/run.sh till             # desktop till, Compose UI tests on the JVM
scripts/e2e/run.sh android          # baker app on an emulator (Compose UI tests from mobile/e2e, adb reverse)
scripts/e2e/run.sh ios              # same baker journeys on the simulator
scripts/e2e/run.sh all              # web, till, android and ios in turn, each on its own fresh stack
  --no-build   reuse the stack's images        --keep   leave the stack up afterwards
  -- <args>    passed to the test runner (e.g. -- session.spec  for Playwright)
```

| Suite | Needs |
|---|---|
| `web` | Docker, node, python3, curl; downloads Chromium and WebKit on first run |
| `till` | Docker, python3, a JDK 17 toolchain (Gradle) |
| `android` | Docker, python3, Android SDK with an AVD (`ECOPAN_E2E_AVD`, default `Medium_Phone`; an already-running emulator is reused) |
| `ios` | Docker, python3, full Xcode with a simulator |

Each run starts from the reference seed plus the demo steps (opening stock, forecasting on). The runner refuses to start if a port is
taken by something that is not its own stack, and tears down **only the volumes its project created**. On failure it keeps what the
user would have seen under `build/e2e-artifacts/` (`ECOPAN_E2E_ARTIFACTS`): Playwright screenshots, traces and report; the till's
screenshot and semantics tree; the phone's screenshot and UI hierarchy; and the stack's service logs.

## How the suites are built

- **Dashboard** (`e2e/web`): Playwright finds elements by Spanish text and roles. `e2e/shared/api.ts` plays the other clients (push a
  batch as the baker, record a sale as the cashier, change a price, revoke a session, toggle a module) and `e2e/shared/stack.ts`
  stops and starts containers. The stack runs one-minute access tokens (`scripts/e2e/compose.yml`) so a suite crosses a real refresh.
- **Till** (`composeApp/src/jvmTest/.../e2e`): `runComposeUiTest` on the real app root, with real Koin, Room and sync engine, in a
  throwaway `user.home` (the database and token store never come from the developer's). The backend address is a build-time constant,
  so the runner passes `-Pecopan.backendUrl`; the tests are excluded from a plain `:composeApp:jvmTest` unless `-Pecopan.e2e=true`.
  Faults are real: containers stop and start, a row is deleted, a refresh token is revoked.
- **Baker (Android + iOS)** (`mobile/e2e`, `:mobile:e2e`): the journeys are written once, in `commonTest`, with Compose UI tests on the real
  app root (real Koin, Room and sync engine). Only `startAppKoin`, `e2eApi` and `sleep` differ per platform. The Android actuals run as an
  `androidDeviceTest` on an emulator (`scripts/e2e/run.sh android`): the phone's `localhost:8080` is `adb reverse`d to the throwaway stack.
  Device-only on purpose: a Robolectric host run was tried and dropped (the UI never advanced under its paused looper), and the product has
  no platform-specific integrations that would need a host-side tier. One app graph serves the whole run, like a real install, and the
  first journey enrolls the phone. Faults (stop/start the API, SQL) go through `scripts/e2e/control.py`, a localhost control server the
  runner starts and the emulator reaches over `adb reverse`; it only touches the `ecopan-acc` stack.

Tests create the data they mutate (their own product, batch, user) and never depend on each other's order.

## iOS notes

**State: the suite passes on the simulator (`scripts/e2e/run.sh ios`) and is part of `all`.** Settings cannot be opened in a test
process (see below), so stories 82 and 83 return early on iOS; everything else runs.
Four things differ from a real app launch, all contained in `mobile/e2e` and `scripts/e2e`:

- gitlive's Crashlytics klib asks the linker for the Firebase frameworks, which only the `iosApp` host provides. The test binary links
  against empty stand-ins built by `scripts/e2e/ios-stubs.sh`; the journeys never call Crashlytics.
- A test process started by `simctl` has no app container, so the harness creates the Documents directory the database and preferences use.
  It deletes it first: the simulator keeps it between runs, and a previous run's session (tokens of a stack that is gone) made every
  push and refresh answer 401, so batches stayed "Pendiente".
- The Settings screen's notification row asks `UNUserNotificationCenter`, which aborts a process with no app bundle, so the Settings
  journeys (82, 83) do nothing on iOS (`settingsAvailable`).
- The app's Compose resources reach a Kotlin/Native test executable only as resources of a main dependency, so `:mobile:e2e` has
  `:composeApp` in `commonMain` as well as `commonTest`.

## What verifies the #126 hardening (#127-#143)

Three layers, each run by its own command; none of them replaces the others.

| Ticket | Unit / integration (`./gradlew :backend:*:test`, `npm test`) | Real stack |
|---|---|---|
| #127 branch-bound reads | `BranchScopeRouterTest` | `scripts/acceptance/run.sh` (403 on another branch, 200 on its own) |
| #128 no dev JWT secret | `JwtSecretGuardTest` | none |
| #129 rate-limit key | `ClientAddressTest`, `AuthRateLimitTest` | `scripts/acceptance/run.sh` (IPv6 /64, ports, Retry-After); the baker and till throttle journeys |
| #130 Android backup rules | none | `scripts/acceptance/run.sh --release-artifacts` (`verify-release.sh` rejects an APK without them) |
| #131/#140/#132/#134 clients | vitest, `:data:*` tests | web stories 11-18, the till and baker sign-in journeys |
| #133 transient refresh | `PushWorkerTest` | till and baker throttle journeys (the baker one counts the backend's `/auth/refresh -> 429`) |
| #135 infra failure is a 503 | `SyncRouterTest` | `e2e/web/tests/api/pipelines.spec.ts` (database stopped) |
| #136/#142 outbox | `OutboxRelayTest` | `scripts/acceptance/run.sh` (broker down, DUPLICATE, announced once); the batch journeys |
| #137 booked at `batchAt` | `FormulaDecrementProcessorImplTest` | `pipelines.spec.ts` (a batch from three days ago) |
| #138 photo confirm | `CatalogRouterTest` (in-memory store) | `e2e/web/tests/api/photos.spec.ts` (Azurite: types, 5 MB, blob deleted) |
| #139 one reporting timezone | the bucketing tests in both services | none |
| #141 atomic rotation | `RefreshRotationTest` | web story 17 |
| #143 all-or-nothing decrement | `CompleteDecrementTest`, `FormulaDecrementProcessorImplTest` (retries, dead letter) | the decrement journeys (success only) |
| broker reconnect, worker bootstrap | none | `pipelines.spec.ts` (restart the broker; restart the API and worker together) |

The `api` Playwright project (`e2e/web/tests/api`) has no browser and runs after the web suite in `scripts/e2e/run.sh web`; alone:
`scripts/e2e/run.sh web -- --project=api --no-deps`. Not verified on a real stack: #128, #139, and #143's retry and dead-letter paths
(a transient failure inside the worker is hard to make deterministically from outside; the unit tests cover it).

## Not yet covered

What the table still marks partial or "not yet" is a product gap or a driver limit, listed in [COVERAGE.md](COVERAGE.md) next to the
story: no screen locks the till or the phone (so no PIN journey on the baker phone, story 73), the simulated scale's Agregar, the wedge
scanner (55), printer and drawer (70, 61), the tenant page being read-only (40), and "batch ready" from a phone's own batch (80).
