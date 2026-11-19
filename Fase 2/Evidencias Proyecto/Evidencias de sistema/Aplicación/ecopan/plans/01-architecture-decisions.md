# 01 — Architecture Decisions (binding)

Cross-cutting decisions all other plans and all implementation agents must follow. If a later plan appears to contradict this document, this document wins; flag the contradiction instead of improvising.

---

## 1. One shared Compose app, role-shaped at runtime

**Decision:** Keep the template's single `:composeApp` as the only Compose application module. Desktop POS and baker mobile are the *same binary family*, differing by which top-level destinations the logged-in session unlocks — not by separate app modules.

- `:jvm:app` packages the desktop build → a cashier logs in → POS destinations.
- `:android:app` / `iosApp` package mobile builds → a baker logs in → production destinations.
- `AppNavigation.kt` computes top-level destinations from `(session.role, tenant.enabledModules, platform capability)`. A manager logging into desktop could see both; a baker on desktop sees production. Role drives navigation; platform only gates hardware-dependent affordances (e.g. serial drivers exist only on JVM).
- Rationale: maximizes shared code (user requirement: ~99% shared on mobile), keeps the template's module shape, and turns "which app is this?" into configuration — consistent with the engine+config genericity thesis.

Feature modules are added per the template recipe (api/impl, Koin `includes`, polymorphic NavKey registration). New features:

| Module | Contents |
|---|---|
| `:feature:auth:{api,impl}` | Login screen (tenant/branch/user selection + PIN or password), session bootstrap. First destination when no session. Shared: PIN/session unlock applies on both desktop till and baker mobile. |
| `:jvm:feature:pos:{api,impl}` | POS screens — desktop till only, see `05` |
| `:mobile:feature:production:{api,impl}` | Baker batch logging + batch history — see `06` |
| `:feature:inventory:{api,impl}` | Raw-material stock view + manual adjustment (manager cut-line surface) |
| `:feature:settings` | Kept from template; grows device/driver configuration section (POS) |

`:feature:home` (Notes) is removed in Phase 1 once `:feature:auth` + one real feature compile. `:data:example` likewise.

**Platform-exclusive feature modules live under that platform's folder (DECIDED).** `:feature:*` is for code shared across every client target (PIN/session, or anything cross-platform). A feature module that only ever runs on one platform — e.g. `:feature:pos`, which is desktop-till-only (a phone/tablet doesn't have a scale or a cash drawer) — nests under that platform's existing folder instead: `:jvm:feature:pos:{api,impl}`, mirroring `:jvm:app`. A baker-mobile-only feature lives at `:mobile:feature:<name>:{api,impl}` — `:mobile:`, not `:android:`, because the baker client is Android *and* iOS. `:feature:production` has since been moved there. This is the existing `:feature:*` api/impl convention relocated, not a new module category — same recipe, same Koin/navigation wiring, just nested so the module tree states the client boundary instead of leaving it implicit in a comment.

**Superseded in one respect (see CLAUDE.md):** the boundary is no longer only a naming convention. `:jvm:feature:*` uses `com.f776.kotlinMultiplatformDesktop` (JVM target only) and `:mobile:feature:*` uses `com.f776.kotlinMultiplatformMobile` (Android + iOS, no JVM), so each client's exclusive modules produce no artifact the other client could resolve. Placing a module in the wrong tier is a build failure, not a review comment.

## 2. Client data modules

New `:data:*` pairs, all per the skill's repository pattern (interfaces in `api` under `domain.<name>`, impls `internal` in `impl` under `data.<name>`):

| Module | Owns |
|---|---|
| `:data:tenant:{api,impl}` | Session (current tenant/branch/user/role), tenant config cache, entitlements (`EntitlementsProvider`), users/roles cache |
| `:data:catalog:{api,impl}` | Products (name, type, `soldBy`, price), formulas (baker's %), day-old policies |
| `:data:sales:{api,impl}` | Sale + SaleLine recording and local history (append-only) |
| `:data:production:{api,impl}` | ProductionBatch logging, batch-ready feed (local + SSE-received) |
| `:data:inventory:{api,impl}` | Ingredients, stock movements (append-only delta ledger), derived stock levels |
| `:data:sync:{api,impl}` | Outbox, sync engine, connectivity monitor, sync status flow — see `03` |
| `:data:hardware:{api,impl}` | Device driver interfaces + drivers — see `04` |

`:data:reminder` and `:data:preferences` stay as-is (reminders are unused by the domain initially; keep the module, it costs nothing).

All persistence stays in `:core:database` (single `AppDatabase`, entities/DAOs `internal`, exposed to `:data:*:impl` per the template's DatabaseModule pattern). Schema evolution follows the CLAUDE.md Room recipe (version bumps + migrations; `migrate` is non-suspend in Room 2.8.x KMP).

## 3. Identity, time, and ordering

- **IDs:** client-generated UUID v4 (`kotlin.uuid.Uuid`, as the template already uses) for *all* domain rows. Offline clients must mint IDs locally; server never assigns domain IDs. Idempotency and sync depend on this.
- **Timestamps:** `kotlinx.datetime.Instant`, stored as epoch-millis (Room) / `timestamptz` (Postgres). Wall-clock is recorded for domain meaning (sale time), never used for conflict resolution ordering (see §6).
- **Server sequence:** every synced row gets a server-assigned monotonic `serverSeq` (global per tenant) on ingest. Clients pull by cursor `> lastSeenSeq`. This is the ordering primitive for sync.

## 4. Multi-tenancy, auth, roles

- **Schema-level tenancy:** `tenant_id` column on every domain table, both sides. No separate schemas/databases. Backend enforces scoping in one place: an authenticated principal carries `(tenantId, branchId?, role)`; repositories always filter by principal tenant — never trust a tenant id from a request body.
- **Auth:** username + password per user (users provisioned via seed/admin — no signup). Ktor JWT (`auth-jwt`): access token (~15 min) + refresh token. Offline clients cache the session and keep operating with an expired token; sync retries auth on reconnect. Password hashing: bcrypt/argon2 on the backend (verify library via context7).
- **Offline user model (device session + PIN unlock — DECIDED):** on the offline clients, server authentication is **per device, not per shift**. An online login binds a long-lived *till/device session* to `(tenant, branch, deviceId)`. Individual staff then unlock the device with a short numeric **PIN** verified locally against hashes synced via the `USER` pull — the PIN **attributes** actions (sales record the cashier, batches the baker) but does not authenticate against the server; the device session does. Offline user-switching therefore works; PINs are distinct from web passwords, hashed with a slow KDF, rate-limited locally, and valid only for local unlock. Fresh install with zero connectivity still can't enroll — one online login per device is required (honest limitation).
  - **Local PIN verification (DECIDED):** `:core:crypto`'s `PinVerifier` interface, bcrypt-backed to match server-side hashing (02 §2). JVM (desktop POS) and Android (baker mobile) share one actual via `at.favre.lib:bcrypt` — pure JVM/Kotlin, no Android Framework dependency, so no expect/actual divergence needed between them. **iOS PIN unlock is scoped out of v1 (DECIDED):** a real iOS implementation needs bcrypt via cinterop against vendored C source — a pattern with no precedent in this codebase and not verifiable without a real iOS simulator/device — so rather than ship unreviewed cinterop crypto code, `core/crypto/src/iosMain/.../PinVerifier.ios.kt` throws deliberately and `:feature:auth` must gate the PIN lock screen off on iOS (fall back to online per-shift login, or hide the feature) until this is revisited with real device testing available.
- **User admin is always-on (DECIDED):** basic user CRUD — create user, set password/PIN, activate/deactivate, assign one of the fixed roles — is part of the always-on core, NOT gated by `MULTI_BRANCH_ROLES`. That module gates the *multi-branch structure*: creating additional branches, branch-scoped role assignment, and branch-oversight views. Rationale: an entitlement toggle must remove a coherent capability, never break basic operation (password resets).
- **Roles (seeded, not user-definable):** `CASHIER` (branch-scoped: record sales), `BAKER` (branch-scoped: log batches), `BRANCH_MANAGER` (one branch: + inventory adjust, local reports), `GENERAL_MANAGER` (tenant-wide: everything + admin + config), `ADMIN` (superuser for seeding/demo). Permission checks are role → capability lookups in one table-driven place, not scattered `if role ==`.
- `branch_id` nullable on `User` — null means tenant-wide (general roles), per spec.

## 5. Units and money (no floats in ledgers)

- **Mass:** integer **grams** everywhere (`weightGrams: Long`). UI converts to kg for display/entry. Scale drivers report grams.
- **Money:** integer **minor units** (`amountMinor: Long`) + tenant-level `currencyCode`. Reference tenant is CLP (zero-decimal); the type still carries minor units generically.
- **Prices:** `soldBy = WEIGHT` → `pricePerKgMinor` (per 1000 g); `soldBy = UNIT` → `priceMinor` per piece. Line total for weight sales: `round(weightGrams * pricePerKgMinor / 1000)` — rounding rule: half-up, computed once at sale time and *stored* (the stored total is authoritative; never recompute historical totals).
- **Formulas (baker's %):** stored as **basis points relative to flour weight** (`Int`, 10000 bp = 100% = the flour itself). The baker logs *total dough* weight (`mixWeightGrams`); flour grams = `mixWeightGrams * 10000 / totalBp` where `totalBp = Σ bp over all ingredients including flour's 10000`; each other ingredient = `mixWeightGrams * bpᵢ / totalBp`. Integer math, largest-remainder rounding so per-ingredient grams sum exactly to `mixWeightGrams`. Worked example and edge cases in `06-baker-mobile.md`.

## 6. Offline-first sync & conflict policy (the required design decision)

Full protocol in `03-sync.md`; the *policy* is fixed here because backend, POS, and baker plans all depend on it:

1. **Facts are append-only; state is derived.** Sales, sale lines, production batches, and raw-material **stock movements** are immutable events. The contended resource — raw-material stock — is *never stored as a mutable quantity that clients write*. Clients append signed delta movements (`+25000 g purchase`, `-8300 g formula decrement`, `-1200 g manual adjustment`); current stock = Σ movements. Concurrent offline decrements from POS-desktop and baker-mobile **commute** — the spec's headline conflict case is designed away rather than resolved.
2. **Config is server-authoritative, last-write-wins.** Catalog, formulas, day-old policies, users, entitlements are edited (rarely) via the online-only web admin; offline clients hold a read-only cache refreshed on pull. In the marginal case of two admin writes, LWW by server receipt order (`serverSeq`), with the losing write discarded — acceptable because config writes are online, low-frequency, human-reviewed. Documented rationale for the defense: LWW is safe *only* where writes are online and rare; it is deliberately **not** used for stock, where offline concurrency is the norm.
3. **Idempotent ingest.** Every outbox mutation has a client-minted UUID; server upserts by ID — re-pushing after a dropped ACK is harmless.
4. **Negative stock is allowed and surfaced, not rejected.** A decrement that would go below zero still applies (the physical bread was baked); the dashboard flags it as a reconciliation warning. Rejecting it would block offline operation.

## 7. Module entitlements (enforcement points)

`tenant.enabled_modules` (set of: `POS_SALES` always-on, `INVENTORY_FORMULAS`, `DEMAND_FORECASTING`, `MULTI_BRANCH_ROLES`, `ANALYTICS_DASHBOARD`). Exactly three enforcement points:

1. **Client navigation:** `AppNavigation` filters top-level destinations through `EntitlementsProvider` (from `:data:tenant:api`). Disabled module → destination absent, feature Koin module still loads (cheap) but unreachable.
2. **Backend routes:** a Ktor route-scoped guard (`requireModule(INVENTORY_FORMULAS)`) → 403 with a typed error body.
3. **Web dashboard:** route guards + hidden nav sections driven by the tenant profile fetched at login.

No billing, provisioning, or plan UI — toggling is done by editing the tenant row (admin/seed), which *is* the demo.

**Seeding (DECIDED):** boot-time only. `SEED_PROFILE` env var accepts a **comma-separated list** of profile names; each loads idempotently at backend startup. No admin seed endpoint, no CLI path. Demo reset = backend restart (rehearse once so its duration is known).

## 8. Backend shape

- Keep the template's four backend modules and their responsibilities, repurposed: `backend:app` (REST + SSE + auth), `backend:worker` (async processors — formula decrement from synced batches, forecast refresh triggers), `backend:notifier` (aggregates events → SSE push fanout), `backend:data` (Exposed repositories + RabbitMQ brokers). Exposed + PostgreSQL + Kourier/RabbitMQ stay.
- **Production event flow** (replaces ExampleJob): baker sync pushes `ProductionBatch` (+ its client-minted stock movements) → `backend:app` persists + publishes batch message → `backend:worker` post-processes → publishes completion → `backend:notifier` → `SsePushBroker` → SSE (`GET /events/sse`) → online POS/web clients show "batch ready".
- **Formula decrement is computed client-side** (the baker device mints the negative stock movements in the same local transaction as the batch — required for offline stock visibility; see `06`). The batch carries a `movementsMinted` flag; `backend:worker`'s decrement processor acts only as *fallback* for batches synced without movements (e.g. formula existed server-side but not in the client cache). Both paths use the identical basis-point math (§5). Cut-line preserved: disable both minting strategy and worker processor → batches and events still flow, manual adjustment still works, nothing downstream depends on movements existing.
- ML service is a separate Python deployment (`ml/`); `backend:app` proxies/reads forecasts (details `08`).

## 9. Stack decisions (from user confirmation)

- **Web:** React + TypeScript, generated with Vite, in-repo at `webApp/`. **TanStack Query** for all server state (queries + mutations). Tailwind v4 with `spec/index.css` as the token source, shadcn/ui components. Router: TanStack Router (pairs with Query; agent may fall back to React Router only if a hard blocker appears — record why).
- **Theme:** single visual identity across all clients. `spec/index.css` is the canonical token set; `09-theming.md` defines the Compose Material3 mapping (replaces the template's MaterialKolor seed scheme).
- **Mobile:** Android + iOS both first-class; all feature/data code in `commonMain`; platform actuals only where the template already has the pattern (DB driver, DataStore path, notifications). No new native-only capabilities.
- **ML:** Python (`ml/` directory): training pipeline on Corporación Favorita + FastAPI inference/forecast service on Azure.
- **Serial:** jSerialComm for the JVM scale/printer drivers (verify via context7; it's the de-facto pure-Java serial lib — no native driver installs).

## 10. Testing baseline

- Every ViewModel: `jvmTest` with fakes (template pattern: `FakeNoteRepository` style).
- Sync engine: pure-JVM tests simulating interleaved offline mutations + reconnect (the conflict-policy tests are defense material — name them accordingly).
- Formula math + money rounding: exhaustive unit tests (integer math, property-style where cheap).
- Hardware: driver contract tests run against mock drivers; `SerialScale` gets a replay-based test fed by a captured byte log from the Phase-0 spike.
- Backend: Ktor `testApplication` per router; repository tests against H2-in-PG-mode or Testcontainers (agent decides in `02`, records choice).
