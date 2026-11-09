# 03 — Offline-First Sync Engine (`:data:sync`)

Client-side sync: outbox, push/pull protocol, connectivity, status surface, scheduling, bootstrap. Server counterpart in `02-backend.md`. Policy authority: `01-architecture-decisions.md` §3 (ids/serverSeq) and §6 (conflict policy) — this doc restates them operationally, never overrides them. Zero production code here; all sketches are contracts for implementation agents, who must re-verify every library API via context7 before writing code.

**Status of `02-backend.md`:** RECONCILED. `02` §4 owns the wire format; this doc owns client behavior. The envelope below matches `02` (mutationId/entityType/opType/clientTimestamp/payload; APPLIED/DUPLICATE/REJECTED acks with serverSeq). Corrections applied from `02`: pull is `GET /sync/pull?since={serverSeq}&limit&entityTypes`, response `{changes, nextCursor, hasMore}` (persist `nextCursor` after applying the page); push request also carries a top-level `deviceId`; server cap is 200 mutations/call (client batch of 50 is fine); `SALE_LINE` is not a wire entityType (lines are embedded in `SALE`); the baker client also pushes `FORMULA_DECREMENT` stock movements with `sourceBatchId` (01 §8 client-primary decrement).

---

## 1. Module design — `:data:sync:{api,impl}`

Per the `writing-kmp-features` skill: interfaces + domain models in `api` (package `com.f776.ecopan.domain.sync`), everything in `impl` `internal` except the Koin `Module` val (`com.f776.ecopan.data.sync.di.SyncDataModule`).

Convention plugins: `api` → `f776.kotlinMultiplatform`; `impl` → `f776.kotlinMultiplatform` + `f776.androidKmpLibrary` + `f776.kotlinSerialization` (no Compose — the indicator composable lives in `:core:ui`, not here).

### 1.1 Public api surface (everything else internal)

```kotlin
// domain.sync — illustrative contract, not final code

enum class ConnectivityStatus { ONLINE, OFFLINE }

sealed interface SyncStatus {
    data object Idle : SyncStatus                  // nothing pending, last sync ok
    data object Syncing : SyncStatus               // push or pull in flight
    data class Pending(val count: Int) : SyncStatus // offline or backing off, N outbox rows waiting
    data class AuthRequired(val pendingCount: Int) : SyncStatus // refresh failed; keep queueing, need re-login
    data class Error(val pendingCount: Int, val lastError: SyncError) : SyncStatus

    val connectivity: ConnectivityStatus           // always present alongside the phase
}

interface SyncEngine {
    val status: Flow<SyncStatus>                   // hot, replay-1; consumed by :core:ui indicator
    suspend fun syncNow(): SyncResult              // manual trigger (pull-to-refresh, indicator tap)
    suspend fun bootstrap(): BootstrapResult       // first-login full pull (§8)
}

interface SyncScheduler {                          // lifecycle hooks, called from :composeApp
    fun start()                                    // app foregrounded / session established
    fun stop()                                     // session ended (logout wipes outbox? — see Open questions)
}

interface OutboxWriter {                           // used by other :data:* impl modules
    /** Enqueue a mutation. MUST be called inside the same Room transaction that writes
     *  the domain row (§3.2). mutationId is the domain row's client-minted Uuid (01 §3/§6.3). */
    suspend fun enqueue(
        mutationId: Uuid,
        entityType: SyncEntityType,                // enum: SALE, SALE_LINE, PRODUCTION_BATCH, STOCK_MOVEMENT, ...
        opType: SyncOpType,                        // enum: CREATE (append-only facts), UPSERT (rare, config never — read-only cache)
        payloadJson: String,                       // kotlinx-serialization of the wire DTO
    )
}

fun interface ConnectivityMonitor {                // implemented per-platform in impl (§6)
    fun observe(): Flow<ConnectivityStatus>
}
```

Notes:
- `OutboxWriter` is the **only** way other data modules touch sync. They never see the outbox table, transport, or engine. Dependency direction: `:data:sales:impl` → `:data:sync:api` (interface only).
- `ConnectivityMonitor` is public in `api` (the engine and, optionally, features consume it); its actuals are `internal` in `impl` via the `internal expect val PlatformModule: Module` pattern.
- `SyncEntityType` / `SyncOpType` are `api` enums, serialized by name on the wire. Payload DTOs (`@Serializable`) live in `api` too — they are the client half of the wire contract shared conceptually with `02`.
- Internal-only components in `impl`: `SyncEngineImpl`, `SyncSchedulerImpl`, `OutboxWriterImpl`, `PushWorker`, `PullWorker`, `SyncTransport` (internal interface over Ktor client — the seam for fake-transport tests, §9), `CursorStore` (DataStore-backed serverSeq cursor), mappers under `data.sync.local.mapper`.

### 1.2 DI wiring

```kotlin
val SyncDataModule = module {
    includes(DatabaseModule, PlatformModule)       // PlatformModule binds ConnectivityMonitor actual
    single { SyncEngineImpl(...) }.bind<SyncEngine>()   // single: owns long-lived state
    single { SyncSchedulerImpl(...) }.bind<SyncScheduler>()
    factoryOf(::OutboxWriterImpl).bind<OutboxWriter>()
    factoryOf(::KtorSyncTransport).bind<SyncTransport>()
}
```

- Dispatchers/scopes only via existing named qualifiers from `AppModule` (`named("ioDispatcher")`, `named("ioCoroutineScope")` — the engine's loop runs in an injected scope, never `GlobalScope`, never hardcoded `Dispatchers.*`).
- Depends on `:data:tenant:api` for session (tenantId, tokens) — auth headers come from a `SessionProvider`-style interface there, not from sync owning auth.
- Register `SyncDataModule` in `composeApp/.../di/AppModule.kt` `includes(...)`.

---

## 2. Outbox — Room table

Entity/DAO live in `:core:database` (single `AppDatabase`, per 01 §2), `internal`, exposed to `:data:sync:impl` through `DatabaseModule` DAO binding (template pattern: `single { get<AppDatabase>().outboxDao() }`).

```
outbox
  rowid        INTEGER PK AUTOINCREMENT   -- local FIFO order; never sent on the wire
  mutation_id  TEXT NOT NULL UNIQUE       -- client-minted UUID v4 = domain row id (01 §3)
  entity_type  TEXT NOT NULL              -- SyncEntityType.name
  op_type      TEXT NOT NULL              -- SyncOpType.name
  payload      TEXT NOT NULL              -- kotlinx-serialization JSON of wire DTO
  created_at   INTEGER NOT NULL           -- epoch millis, Instant
  state        TEXT NOT NULL              -- PENDING | IN_FLIGHT | ACKED | FAILED
  attempts     INTEGER NOT NULL DEFAULT 0
  last_error   TEXT NULL                  -- truncated message for diagnostics
  INDEX (state), UNIQUE INDEX (mutation_id)
```

State machine:

```
PENDING ──push batch──▶ IN_FLIGHT ──server ack──▶ ACKED (delete or keep-N for debugging)
   ▲                        │
   │  transient error /     │ non-retriable (4xx validation, malformed payload)
   └── app restart ◀────────┴──────────────────▶ FAILED (dead-letter; surfaced in SyncStatus.Error,
        (IN_FLIGHT rows revert to PENDING            manual retry/discard via settings screen — see 05)
         on engine start: crash recovery)
```

- ACKED rows are deleted on ack (keep the table small); the domain row is the durable record. (Alternative keep-last-N is an open question.)
- `attempts` increments per failed push of that row's batch; FAILED only for *permanent* errors — auth expiry and connectivity are never FAILED (§4.5).

### 2.1 Atomic domain-write + outbox-enqueue

Rule for every offline-mutating repository (`:data:sales:impl`, `:data:production:impl`, `:data:inventory:impl`): the domain insert and the outbox insert happen in **one Room transaction** — a crash between them must be impossible.

Room 2.8.x KMP transaction API (verified via context7, room-runtime KMP surface — implementation agents re-verify current signatures before coding): the KMP-compatible primitive is `db.useWriterConnection { transactor -> transactor.immediateTransaction { ... } }`; the older `androidx.room.withTransaction` extension is Android-only room-ktx and must not be used in `commonMain`. Alternatively (preferred where it fits): a single `@Transaction`-annotated DAO method taking both entities. Pattern:

```kotlin
// inside e.g. DbSaleDataSource (illustrative)
db.useWriterConnection { tx ->
    tx.immediateTransaction {
        saleDao.insert(saleEntity)                 // DAO calls inside the block join the transaction
        outboxDao.insert(outboxEntity(...))
    }
}
```

Consequence for `OutboxWriter`: `OutboxWriterImpl.enqueue` must be callable *inside* an ambient transaction — i.e. it does plain DAO inserts and performs no transaction/dispatch of its own (no `withContext` inside; the caller owns dispatcher + transaction). Document this on the interface KDoc; test it (§9 T8).

---

## 3. Push protocol

### 3.1 Batching & ordering

- Batch = up to `N=50` outbox rows in `rowid` ascending order, single `POST /sync/push`. One batch in flight at a time per client — **per-client FIFO by outbox rowid** is the ordering guarantee. No cross-client ordering is promised or needed (facts commute, 01 §6.1; server assigns `serverSeq` on ingest).
- Rows in a batch: `PENDING → IN_FLIGHT` before the request; on full ack `→ ACKED`; on transport failure all revert `→ PENDING` (idempotency makes replays safe).
- FIFO matters within a client for referential order (a `SALE` before its `SALE_LINE`s, a `PRODUCTION_BATCH` before its derived local rows). Server must tolerate but not require this (02 concern).

### 3.2 Wire envelope (reconciled with `02-backend.md` §4.1 — that doc is authoritative; note it adds a top-level `deviceId`)

Derived strictly from 01 §3 (client UUIDs, serverSeq) and §6.3 (idempotent ingest by client-minted UUID):

```
POST /sync/push        Authorization: Bearer <access>
{ "clientId": "<device-install uuid>",
  "mutations": [ { "mutationId": "<uuid>", "entityType": "STOCK_MOVEMENT",
                   "opType": "CREATE", "payload": { ...DTO... },
                   "clientCreatedAt": "<ISO instant>" }, ... ] }
→ 200
{ "results": [ { "mutationId": "<uuid>", "status": "APPLIED" | "DUPLICATE" | "REJECTED",
                 "error": null | { code, message } } ],
  "maxServerSeq": 12345 }
```

- Server **upserts by `mutationId`** (= the domain row's id): `APPLIED` and `DUPLICATE` are both treated as ack by the client — re-push after a dropped ack is harmless by design (01 §6.3).
- `REJECTED` (validation) → that row `FAILED`, rest of batch unaffected.
- TenantId is **never** in the body — it comes from the JWT principal (01 §4).

### 3.3 Retry/backoff

- Trigger points: connectivity `OFFLINE→ONLINE` edge, new outbox row while online, periodic tick (§7), manual `syncNow()`.
- Transient failure (IO, 5xx, timeout): exponential backoff `5s · 2^n`, cap 5 min, ±20% jitter; counter resets on success or connectivity edge. While backing off with rows queued → `SyncStatus.Pending(count)`.
- No retry ceiling for transient errors — offline is the normal state, not an error (spec: "must keep selling").

### 3.4 Auth expiry (01 §4)

- 401 on push/pull → attempt refresh-token exchange once. Success → retry immediately.
- Refresh also fails → status `AuthRequired(pendingCount)`; engine parks (no hammering). **Local operation continues unimpeded** — outbox keeps accepting writes; nothing is dropped. Per 01 §4 the credential being refreshed is the **device session** (long-lived, bound to tenant/branch/deviceId — 02 §2), not a per-user login: staff PIN switches never touch the engine, and recovery from a dead device session is one online re-enrollment login on that device (any user with a password), after which the engine resumes and drains. Sync never blocks the till.

---

## 4. Pull protocol

### 4.1 Cursor

- Per 01 §3: server assigns global-per-tenant monotonic `serverSeq`. Client stores one cursor `lastSeenSeq` (DataStore, keyed by `(tenantId, branchId?)`; via `:data:sync:impl` `CursorStore` using the `named("datastore")` single and the mandatory `IOException → emptyPreferences()` catch).
- `GET /sync/pull?since=<lastSeenSeq>&limit=500&entityTypes=<csv?>` (per 02 §4.2) → `{ changes: [{serverSeq, entityType, opType, payload}], nextCursor, hasMore }`. Cursor advances to `nextCursor` only after a page is fully applied (one Room transaction per page) — crash mid-page re-pulls the page; appliers are idempotent so that's safe.
- Pull runs after every successful push (to pick up own rows' server state and everyone else's), on connectivity edge, and on the periodic tick.

### 4.2 What each client pulls

Server filters by principal `(tenantId, branchId, role)`; client also declares interest (`entityTypes` query param) to keep payloads small:

| Client / role | Pulls |
|---|---|
| POS desktop (CASHIER) | catalog + config (products, prices, day-old policies), production events (batch-ready feed for its branch), stock movements (branch) |
| Baker mobile (BAKER) | catalog (products + formulas), own-branch data (own batches echo, branch stock movements) |
| Both | tenant profile + entitlements, users/roles cache incl. `pinHash` for same-branch users (local PIN-unlock verification on shared devices — 01 §4, 02 §4.2; never `passwordHash`) |

Sales history is **not** pulled to other clients (POS is origin; dashboard reads server-side). Manager-role sessions on either client widen the branch filter per 01 §4.

### 4.3 Applying pulled rows

Two applier families, dispatched by `entityType` (appliers are registered by the owning `:data:*:impl` modules via an internal `SyncApplier` binding set — sync stays domain-agnostic; see Open questions Q5 for the binding mechanism):

1. **Config (server-authoritative, LWW — 01 §6.2):** products, formulas, policies, users, entitlements, tenant profile → **overwrite** the local cache row by id (`@Upsert`), deletes as tombstone rows in the feed. Local edits to config from offline clients don't exist by construction (read-only cache), so overwrite is always safe.
2. **Append-only facts (01 §6.1):** stock movements, production batches, (echoed) sales → **insert if unseen** (`INSERT OR IGNORE` by client-minted UUID PK). Never update, never delete. The client's own pushed rows come back in the feed and are ignored by the same rule — no echo-suppression logic needed.

Derived state (current stock = Σ movements) recomputes reactively via Room `Flow` queries in `:data:inventory` — the sync layer never computes stock.

---

## 5. Conflict policy, operationally (restating 01 §6 — binding)

| Case | Mechanism | Resolution |
|---|---|---|
| Both clients offline decrement same ingredient stock | Facts are signed delta movements, append-only | Both movements apply; Σ commutes; **no conflict exists to resolve** |
| Two config edits | Online-only web admin, LWW by `serverSeq` receipt order | Loser discarded; acceptable: online, rare, human-reviewed. LWW deliberately NOT used for stock |
| Duplicate push after dropped ack | Server upsert by client-minted UUID | `DUPLICATE` ack; no double-apply |
| Decrement below zero | Allowed, applied, flagged server-side | Never rejected — rejecting would block offline operation |

**Three named tests that prove it (defense material — 01 §10; keep these exact names):**

1. `bothOfflineStockDecrementsCommute` — POS client A appends `-1200 g` and baker client B appends `-8300 g` to the same ingredient while both offline; sync in either order (run both orders); assert final Σ movements identical on both clients and the fake server, equal to `initial - 9500`.
2. `configLastWriteWinsByServerSeq` — two admin writes to the same product price reach the fake server in order (w1, w2); assert offline clients that pull after reconnect all converge on w2 regardless of the writes' wall-clock timestamps (explicitly set w1's client timestamp *later* than w2's to prove wall-clock is ignored, 01 §3).
3. `duplicatePushAfterDroppedAckIsIdempotent` — client pushes a batch, fake transport delivers to server but drops the ack; client retries the same batch; assert server applied each mutation exactly once, second response is all-`DUPLICATE`, client ends `ACKED` with no duplicated domain rows after pull.

---

## 6. Connectivity monitoring — `expect/actual ConnectivityMonitor`

`internal expect class ConnectivityMonitorImpl : ConnectivityMonitor` in `:data:sync:impl` `commonMain`, bound via `internal expect val PlatformModule: Module` (skill pattern). Emitted as `Flow<ConnectivityStatus>` (`callbackFlow` wrapping platform callbacks, `distinctUntilChanged`).

- **Android** (`androidMain`): `ConnectivityManager.registerDefaultNetworkCallback` (needs `Context` from Koin, `ACCESS_NETWORK_STATE` permission in `:android:app` manifest). Validate with `NET_CAPABILITY_VALIDATED` to avoid captive-portal false positives.
- **iOS** (`iosMain`): Network framework path monitor. **Important for the implementation agent:** Swift's `NWPathMonitor` class is Swift-only sugar; from Kotlin/Native you use the pre-imported C API in `platform.Network.*` — `nw_path_monitor_create()`, `nw_path_monitor_set_update_handler` (block receiving `nw_path_t`), `nw_path_monitor_set_queue` (a `dispatch_queue_t` from `platform.darwin`), `nw_path_monitor_start`, status via `nw_path_get_status(path) == nw_path_status_satisfied`. Context7 lookup done at planning time confirmed only the general `platform.*` interop mechanism, not these exact symbols — **the implementation agent MUST re-verify the `nw_path_monitor_*` names/signatures via context7 before coding** (per CLAUDE.md: never grep `.def` files). `@OptIn(ExperimentalForeignApi::class)` at file level.
- **JVM desktop** (`jvmMain`): no OS callback worth depending on — periodic reachability probe: lightweight `HEAD`/`GET /health` against the backend every 30 s (probe interval backs off to 2 min while offline), via the same injected Ktor client. Honest tradeoff: desktop "ONLINE" means "backend reachable", which is actually the more useful signal for sync anyway.

Engine treats connectivity as a *hint* (triggers attempts, drives the indicator); truth is the actual request outcome — a push failing while "ONLINE" still backs off normally.

### 6.1 Network-state indicator (spec REQUIRES it on both offline clients)

- Small composable in `:core:ui` (domain-free module, so it takes plain parameters — **not** `SyncStatus` from `:data:sync:api`): `SyncStatusIndicator(state: SyncIndicatorState, onClick: () -> Unit)` where `SyncIndicatorState` is a tiny UI-level enum+count model defined in `:core:ui` (`ONLINE_IDLE`, `SYNCING`, `OFFLINE_PENDING(n)`, `ATTENTION(n)` for AuthRequired/Error).
- Mapping `SyncStatus → SyncIndicatorState` lives in `:composeApp` (`AppViewModel` collects `SyncEngine.status`) and the indicator is placed in the persistent top-app-bar/status area of POS (`05`) and baker (`06`) scaffolds. Tap → detail popup (pending count, last sync time, manual "Sync now" → `syncNow()`, re-login CTA on `AuthRequired`).
- Visual language per `09-theming.md` tokens; must be visible at all times on POS sale screen and baker logging screen (spec §4.1/§4.2: "visible network-state indicators").

---

## 7. Sync scheduling

Keep it simple; promise **no** background-OS sync guarantees — sync happens while the app is alive, which is the honest capability on iOS/desktop anyway.

- **Common (all platforms):** `SyncSchedulerImpl.start()` (called from `:composeApp` on session-ready / foreground) launches one loop in the injected `named("ioCoroutineScope")`: react to (a) connectivity `ONLINE` edges, (b) outbox-non-empty signal (Room `Flow` on `outbox WHERE state='PENDING'` count), (c) periodic tick every 5 min while alive. `stop()` cancels. Single-flight guarded by a `Mutex`.
- **Android (optional hardening, copy existing template wiring):** an expedited `OneTimeWorkRequest` + `CoroutineWorker` with `NetworkType.CONNECTED` constraint to flush the outbox when the app is backgrounded with pending rows — mirror `data/reminder/impl/androidMain` (`BootSchedulerWorker` + `workerOf(::...)` Koin `workmanager` DSL + `OneTimeWorkRequestBuilder` in `BootReceiver`). This is a nice-to-have work item, not a correctness dependency.
- **iOS / desktop:** the common coroutine loop only. No BGTaskScheduler (not worth the review/complexity budget; document as a limitation).

---

## 8. Bootstrap / first-login flow

- **First login (online, required — enrolls the device):** after auth succeeds (login carries `deviceId`/`deviceName`, so the issued refresh chain is a long-lived **device session** per 01 §4 / 02 §2) → `SyncEngine.bootstrap()`: full pull from `after=0` (paged) for the session's entity set (§4.2) inside a "Preparing your store…" blocking screen (`:feature:auth`). POS is **not usable** until bootstrap completes — selling requires catalog + prices. Cursor lands at `maxServerSeq`; thereafter incremental.
- **Fresh install, offline:** **blocked, by design.** No cached tenant/catalog/users exist, so there is nothing to sell against and no device session to restore. Login screen shows an explicit "First sign-in needs a connection" state — stated honestly in UI and in the defense. After the one online enrollment login, everything works offline: subsequent app starts restore the device session, and staff unlock/switch via local PIN check against the pulled `pinHash` cache (01 §4).
- **Returning device, offline:** device session + session cache in `:data:tenant` restore; app fully usable behind PIN unlock; outbox queues; indicator shows `OFFLINE_PENDING`.
- **Re-bootstrap:** tenant switch or explicit "reset local data" (settings) → wipe domain tables + outbox (warn if pending > 0) + cursor, bootstrap again.

---

## 9. Testing plan (01 §10 — pure-JVM, defense material)

All in `:data:sync:impl` `jvmTest`, headless, no server: `FakeSyncTransport` (implements internal `SyncTransport`; scriptable per-call: succeed / fail-transport / deliver-but-drop-ack / 401 / reject) + `FakeServer` (in-memory map keyed by mutationId, assigns serverSeq, serves pull pages) + in-memory Room (JVM driver) + `kotlinx-coroutines-test` virtual time. Fakes follow the template's `FakeNoteRepository` style; common utils in `:core:test`.

Named cases:

- **T1 `bothOfflineStockDecrementsCommute`** — §5 test 1.
- **T2 `configLastWriteWinsByServerSeq`** — §5 test 2.
- **T3 `duplicatePushAfterDroppedAckIsIdempotent`** — §5 test 3.
- T4 `outboxDrainsFifoByRowid` — 3 mutations enqueued out of wall-clock order push in rowid order, single batch, all ACKED.
- T5 `transientFailureBacksOffExponentiallyAndRecovers` — scripted 5xx ×3 then success; virtual-time asserts 5s/10s/20s (±jitter bounds) and final drain; status transitions Idle→Syncing→Pending→…→Idle observed.
- T6 `authExpiredKeepsQueueingAndResumesAfterRefresh` — 401 + failed refresh → `AuthRequired`; new enqueues accepted meanwhile; simulated re-login → drain.
- T7 `crashRecoveryRevertsInFlightToPending` — rows left IN_FLIGHT, engine restarted, rows revert and re-push (server sees DUPLICATE at worst).
- T8 `domainWriteAndOutboxEnqueueAreAtomic` — transaction-abort test: force failure after domain insert, assert neither row persisted; success path persists both.
- T9 `pullPageAppliesAtomicallyAndAdvancesCursor` — crash between page-apply and… (actually: apply+cursor same transaction) — kill mid-multi-page pull, restart, assert no gaps/dupes, cursor == last fully applied page.
- T10 `pulledConfigOverwritesCacheAndFactsInsertIfUnseen` — one pull page mixing a product update and an already-seen stock movement; cache overwritten, movement not duplicated.
- T11 `rejectedMutationDeadLettersWithoutBlockingBatch` — one REJECTED among APPLIED; row FAILED, others ACKED, engine continues.
- ViewModel-level indicator mapping tested in `:composeApp` jvmTest (status → `SyncIndicatorState`).

Run: `./gradlew :data:sync:impl:jvmTest` plus root `test` (CLAUDE.md — root `test` doesn't reach KMP `jvmTest`).

---

## 10. Work items (ordered)

1. **Outbox schema** — `core/database/.../entity/OutboxEntity.kt`, `dao/OutboxDao.kt`, version bump + migration per CLAUDE.md Room recipe, DAO bound in `di/DatabaseModule.kt`. *Accept:* migration test passes on all three targets' compile; `INSERT`+`Flow` count query work in jvmTest.
2. **`:data:sync:api`** — `data/sync/api`: interfaces/enums/DTOs of §1.1, registered in `settings.gradle.kts`. *Accept:* compiles for jvm/android/ios; zero non-public-contract symbols.
3. **`:data:sync:impl` skeleton + DI** — `data/sync/impl`: `OutboxWriterImpl`, `CursorStore`, `SyncDataModule`, `PlatformModule` stubs; wired into `composeApp/.../di/AppModule.kt`. *Accept:* app boots on all targets with module loaded; T8 passes.
4. **Transport + push** — `KtorSyncTransport`, `PushWorker`, backoff, auth-expiry handling (needs `:data:tenant` session interface). *Accept:* T3, T4, T5, T6, T7, T11 pass against fakes.
5. **Pull + appliers** — `PullWorker`, cursor advance, applier dispatch, config-overwrite + insert-if-unseen appliers registered from `:data:catalog`/`:data:inventory`/`:data:production` impls. *Accept:* T1, T2, T9, T10 pass.
6. **`ConnectivityMonitor` actuals** — androidMain (ConnectivityManager), iosMain (`nw_path_monitor_*` — context7-verify first), jvmMain (health probe). *Accept:* `compileKotlin{Android,Ios,Jvm}` green; manual smoke on each platform (airplane-mode toggle).
7. **Scheduler + status flow** — `SyncSchedulerImpl`, `SyncStatus` emission, single-flight. *Accept:* status-transition assertions inside T5/T6; start/stop idempotent.
8. **Indicator** — `core/ui/.../SyncStatusIndicator.kt` + `SyncIndicatorState`; mapping in `AppViewModel`; placed in POS/baker scaffolds (coordinates with 05/06). *Accept:* previews for all states; mapping jvmTest; visible in both clients.
9. **Bootstrap** — `bootstrap()`, blocking first-login UI in `:feature:auth`, offline-fresh-install message. *Accept:* scripted fake-server bootstrap test (paged, resumable); offline fresh install shows blocked state, no crash.
10. **(Optional) Android WorkManager flush** — mirror `data/reminder` wiring. *Accept:* backgrounding with pending rows enqueues work; flushes on connectivity.
11. **Cross-check against `02-backend.md`** once it exists — envelope field names, endpoints, status codes, pull paging. *Accept:* diff reviewed; this doc's §3.2/§4.1 updated or confirmed.

---

## Open questions

1. **ACKED retention** — delete immediately (planned) vs keep last N for a sync-debug screen? Decide at item 4; delete-immediately unless the debug screen is cheap.
2. **Batch size / tick interval tuning** (50 / 5 min / probe 30 s) — placeholders; tune during Phase 4 field testing.
3. **Logout with pending outbox** — block logout, warn-and-keep (drain on next login by same user), or discard? Leaning warn-and-keep; interacts with shared-device POS reality. Decide with `:feature:auth`.
4. **Offline login on shared devices — RESOLVED (01 §4 device session + PIN unlock).** The device session authenticates; staff PINs (slow-KDF `pinHash` in the `USER` pull, 02 §4.2) attribute actions and unlock locally; password hashes never ship. Sync's only job is transporting `pinHash` in USER rows — done in §4.2. Rate limiting and the lock/switch UI belong to 05/06.
5. **Applier registration mechanism** — Koin multibinding is not first-class; options: named `Map<SyncEntityType, SyncApplier>` assembled in `AppModule`, or each data module contributes a `SyncApplier` binding collected via `getAll<SyncApplier>()`. Verify `getAll` behavior via context7 at item 5.
6. **Own-echo suppression optimization** — pull returns the client's own pushed rows (harmless via insert-if-unseen). If bandwidth matters, 02 could add `excludeClientId`; not needed for correctness.
7. **`02-backend.md` divergence** — any mismatch found at item 11 that would change 01 §3/§6 semantics must be escalated, not silently absorbed (01 is binding).
