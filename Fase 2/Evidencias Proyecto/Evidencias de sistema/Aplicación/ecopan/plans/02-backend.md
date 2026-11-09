# 02 — Backend (Ktor): Schema, API, Auth, Sync, Pipeline

Implements the server side of the system on the existing `backend/*` template. Binding context: `plans/01-architecture-decisions.md` (§3 IDs/seq, §4 tenancy/auth, §5 units, §6 sync policy, §7 entitlements, §8 backend shape). The sync **wire contract is defined here** (§4); `plans/03-sync.md` references it, never redefines it.

Existing template anatomy this plan repurposes (all paths under `backend/`):

| Module | Today | Becomes |
|---|---|---|
| `app` | Ktor Netty, Koin, ContentNegotiation, SSE, Swagger, `JobsRouter`/`EventsRouter`, `EventsConsumer` (SSE fanout bridge) | REST API + auth + sync + SSE, same plumbing |
| `worker` | consumes `ExampleJobMessage`, reverses payload | consumes `ProductionBatchMessage`, computes formula decrement (amputable) |
| `notifier` | aggregates `JobCompleted` → debounced `SsePushEvent` | aggregates `BatchProcessed` → "batch-ready" SSE push |
| `data` | Exposed entities/repos, `RabbitMqService`/`JobEventBroker`/`SsePushBroker` (kourier), `BaseSettings` | domain entities/repos + renamed broker message types; broker impls unchanged |

Kotlin/JVM 21, Exposed v1 (`org.jetbrains.exposed.v1.*`, `suspendTransaction`), kourier AMQP, Koin — all stay. `connectToDb(init = true)` + `SchemaUtils.create` remains the schema mechanism (no Flyway for capstone scope; tables are created once, seed loads after).

---

## 1. PostgreSQL / Exposed schema

Conventions (binding, from 01 §3/§5/§6):
- All domain PKs: `uuid` — **client-minted** for synced rows (server never assigns domain IDs); server-minted only for server-born rows (refresh_tokens, forecasts).
- `tenant_id uuid` on every domain table; every repository query filters by principal tenant.
- Time: `timestamptz` (Exposed `timestamp` / kotlinx `Instant`). `received_at` = server receipt; domain wall-clock (`sold_at`, `batch_at`) is client-supplied and never used for ordering.
- Mass `bigint` grams; money `bigint` minor units; baker's % `int` basis points. No floats.
- Facts (sales, sale_lines, production_batches, stock_movements) are **append-only**: no UPDATE/DELETE endpoints exist for them. Config rows are mutable (LWW by `server_seq`).
- **`server_seq`**: one global PostgreSQL sequence `server_seq` (`CREATE SEQUENCE server_seq`). Every insert/update of a synced row assigns `nextval('server_seq')` inside the writing transaction (Exposed: custom `nextval` expression or explicit `SELECT nextval(...)` in the repo). Global monotonic ⇒ monotonic per tenant, which is all 01 §3 requires; pull cursor is `(tenant_id, server_seq > cursor)`.

New entity files under `backend/data/src/main/kotlin/com/f776/ecopan/data/database/entity/` (one file per aggregate), following `ExampleJobTable`'s `UuidTable` style but **plain DSL tables preferred over DAO entities** for the new domain (repos already wrap everything; DAO classes optional).

### Tables

`tenants`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| slug | varchar(64) UNIQUE | login discriminator (`/auth/login` carries it) |
| name | text | |
| currency_code | varchar(3) | e.g. `CLP` |
| enabled_modules | text | JSON array of module names (kotlinx-serialized); parsed into `Set<Module>` in repo |
| created_at | timestamptz default now | |

`branches` — id uuid PK, tenant_id FK, name text, created_at, server_seq bigint. IDX `(tenant_id, server_seq)`.

`users`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| tenant_id | uuid FK | |
| branch_id | uuid FK NULL | null = tenant-wide (01 §4) |
| username | varchar(64) | UNIQUE `(tenant_id, username)` |
| password_hash | text | bcrypt (§5) — web/enrollment credential |
| pin_hash | text NULL | numeric unlock PIN, bcrypt same as passwords (slow KDF — 01 §4); distinct from the password; synced to devices for local unlock |
| display_name | text | |
| role | varchar(24) | enum name: CASHIER, BAKER, BRANCH_MANAGER, GENERAL_MANAGER, ADMIN |
| active | bool default true | |
| server_seq | bigint | users sync to clients (offline unlock cache) — **password_hash never serialized to pull**; `pin_hash` IS (§4.2) |
| created_at | timestamptz | |

IDX `(tenant_id, server_seq)`.

`role_permissions` — table-driven role → capability map (01 §4 "not scattered `if role ==`"). PK `(role varchar(24), permission varchar(48))`. **Seeded, not user-editable.** Permissions (initial set): `SALES_RECORD`, `BATCH_RECORD`, `STOCK_ADJUST`, `STOCK_READ`, `CATALOG_READ`, `CATALOG_WRITE`, `ANALYTICS_READ`, `FORECAST_READ`, `USERS_MANAGE`, `MODULES_TOGGLE`, `SYNC`. Global (not per-tenant); loaded once into an in-memory map at startup.

`refresh_tokens` — doubles as the **device-session** store (01 §4 device session + PIN unlock). Decision: extend this table with device columns rather than add a separate `device_sessions` table — a device session *is* a long-lived refresh chain, so one table, one rotation flow, one revocation path (recorded choice; the variant table bought nothing but a join).
| column | type | notes |
|---|---|---|
| id | uuid PK | server-minted |
| user_id | uuid FK | IDX — the enrolling user; per-action attribution comes from the payload's cashier/baker id, not this |
| token_hash | text UNIQUE | SHA-256 of the opaque token; raw token never stored |
| device_id | varchar(64) NULL | set when login carried `deviceId` (POS/baker enrollment); null = web session |
| device_name | text NULL | human label for admin display ("Till 1") |
| branch_id | uuid NULL | branch binding of the device session (from the enrolling principal) |
| issued_at / expires_at | timestamptz | web sessions ~30 d; device sessions long-lived (~180 d, `DEVICE_SESSION_TTL_DAYS`) |
| revoked_at | timestamptz NULL | set on rotation/logout |

`products`
| column | type | notes |
|---|---|---|
| id | uuid PK | |
| tenant_id | uuid FK | |
| name | text | |
| product_type | varchar(48) | forecasting group key |
| sold_by | varchar(8) | `WEIGHT` \| `UNIT` |
| price_per_kg_minor | bigint NULL | required iff WEIGHT (per 1000 g) |
| price_minor | bigint NULL | required iff UNIT |
| barcode | varchar(64) NULL | resale products; unique per tenant (partial index where not null); drives POS scan lookup (05) |
| active | bool | soft delete |
| server_seq | bigint | |
| created_at / updated_at | timestamptz | |

IDX `(tenant_id, server_seq)`, `(tenant_id, active)`.

`ingredients` — id uuid PK, tenant_id, name text, low_stock_threshold_grams bigint NULL, active bool, server_seq, created_at/updated_at. IDX `(tenant_id, server_seq)`. Unit is always grams (01 §5) — no unit column.

`formulas` — id uuid PK, tenant_id, product_id FK UNIQUE `(tenant_id, product_id)`, active bool, server_seq, updated_at. One active formula per product.
`formula_lines` — id uuid PK, tenant_id, formula_id FK, ingredient_id FK, basis_points int (flour line = 10000). UNIQUE `(formula_id, ingredient_id)`. Lines replaced wholesale on formula PUT (delete+insert in one tx; formula row gets new server_seq — lines ride along in the formula's pull payload, they have no own server_seq).

`day_old_policies` — id uuid PK, tenant_id, product_id FK UNIQUE `(tenant_id, product_id)`, mode varchar(12) `DISCARD`|`DISCOUNT`, discount_bp int NULL (required iff DISCOUNT), server_seq, updated_at.

`sales` (append-only fact)
| column | type | notes |
|---|---|---|
| id | uuid PK | client-minted |
| tenant_id / branch_id | uuid | |
| cashier_id | uuid FK users | |
| sold_at | timestamptz | client wall-clock (domain meaning only) |
| payment_method | varchar(16) | `CASH`, `CARD`, `TRANSFER`, `OTHER` |
| total_minor | bigint | stored authoritative sum of lines (01 §5 — never recomputed) |
| device_id | varchar(64) | originating client install |
| server_seq | bigint | |
| received_at | timestamptz default now | |

IDX `(tenant_id, server_seq)` (pull cursor), `(tenant_id, branch_id, sold_at)` (sales-by-branch-by-day KPI).

`sale_lines` (append-only, ingested atomically with parent sale)
| column | type | notes |
|---|---|---|
| id | uuid PK | client-minted |
| tenant_id | uuid | denormalized for scoped queries |
| sale_id | uuid FK | IDX `(tenant_id, sale_id)` |
| product_id | uuid FK | IDX `(tenant_id, product_id)` |
| sold_by | varchar(8) | snapshot at sale time |
| weight_grams | bigint NULL | iff WEIGHT |
| quantity | int NULL | iff UNIT |
| unit_price_minor | bigint | snapshot: price_per_kg_minor or price_minor |
| is_day_old | bool | |
| day_old_discount_bp | int NULL | snapshot of applied discount |
| line_total_minor | bigint | client-computed half-up (01 §5), stored authoritative |

`production_batches` (append-only fact)
| column | type | notes |
|---|---|---|
| id | uuid PK | client-minted |
| tenant_id / branch_id | uuid | |
| baker_id | uuid FK users | |
| product_id | uuid FK | carries product_type via join |
| mix_weight_grams | bigint | the single baker input |
| batch_at | timestamptz | client wall-clock |
| movements_minted | bool | client minted the decrement movements locally (01 §8; default path) |
| decrement_status | varchar(12) | `PENDING` → `APPLIED` \| `SKIPPED` (no formula / module off) \| `FAILED` — written by worker (fallback path, §6) |
| device_id | varchar(64) | |
| server_seq | bigint | |
| received_at | timestamptz | |

IDX `(tenant_id, server_seq)`, `(tenant_id, branch_id, batch_at)`.

`stock_movements` (append-only delta ledger — 01 §6.1; current stock = Σ deltas, never a stored mutable qty)
| column | type | notes |
|---|---|---|
| id | uuid PK | client-minted (manual/purchase) or worker-minted (decrement) |
| tenant_id / branch_id | uuid | |
| ingredient_id | uuid FK | IDX `(tenant_id, ingredient_id)` |
| delta_grams | bigint | signed; negative stock allowed (01 §6.4) |
| kind | varchar(20) | `PURCHASE`, `FORMULA_DECREMENT`, `MANUAL_ADJUSTMENT` |
| source_batch_id | uuid FK NULL | set iff FORMULA_DECREMENT; UNIQUE `(source_batch_id, ingredient_id)` — idempotency key for the worker |
| note | text NULL | |
| created_by | uuid NULL | user; null for worker rows |
| occurred_at | timestamptz | |
| server_seq | bigint | |
| received_at | timestamptz | |

IDX `(tenant_id, server_seq)`.

`forecasts` (server-side cache, written by worker from ML service; not synced to offline clients)
| column | type | notes |
|---|---|---|
| id | uuid PK | server-minted |
| tenant_id / branch_id | uuid | |
| product_type | varchar(48) | forecast key is (branch, product_type) per spec §ML |
| forecast_date | date | |
| predicted_grams | bigint NULL | for WEIGHT types |
| predicted_units | int NULL | for UNIT types |
| model_version | varchar(64) | |
| generated_at | timestamptz | |

UNIQUE `(tenant_id, branch_id, product_type, forecast_date)` (upsert on refresh). IDX `(tenant_id, branch_id, forecast_date)`.

**Sync bookkeeping decision:** no separate `applied_mutations` table. Every push op is a row upsert keyed by client UUID (facts: insert-if-absent; config: not pushed at all — web admin is online CRUD). Idempotency = `INSERT ... ON CONFLICT (id) DO NOTHING` (facts) — a re-push after a dropped ACK returns status `DUPLICATE` with the existing row's `server_seq`. This is why repository tests need real PG semantics (§8).

Legacy `example_job` / `batch` tables and their entities/repos are deleted in the same change that lands the new pipeline (§6).

---

## 2. Auth implementation (`backend/app/.../api/core/`)

Verified against Ktor docs (context7 `/websites/ktor_io`, server-jwt): `install(Authentication) { jwt("auth-jwt") { realm; verifier(JWTVerifier); validate { credential -> JWTPrincipal(payload) | null }; challenge { ... } } }`, routes wrapped in `authenticate("auth-jwt")`, claims via `call.principal<JWTPrincipal>()!!.payload.getClaim(...)`. HS256: token minted with `JWT.create().withAudience(...).withIssuer(...).withClaim(...).withExpiresAt(...).sign(Algorithm.HMAC256(secret))` (lib `com.auth0:java-jwt`, pulled in by `ktorLibs.server.auth.jwt` / `ktor-server-auth-jwt`).

- **Algorithm:** HS256, single shared secret `JWT_SECRET` (env). Issuer/audience from env. Access token TTL 15 min.
- **Claims:** `sub` = userId, `tenantId`, `branchId` (omitted when null), `role`, `username`. Entitlements are NOT in the token (toggling must take effect without re-login) — `requireModule` reads a cached tenant row (in-memory cache, ~30 s TTL, in `TenantRepository`).
- **Principal carrying:** new `backend/app/.../api/core/Auth.kt`:
  - `configureAuth()` — installs the plugin as above; `validate` parses claims into checks (non-blank sub/tenantId, known role) and returns `JWTPrincipal`; `challenge` responds 401 with typed body `{ "code": "TOKEN_INVALID" }`.
  - `data class PrincipalContext(val userId: Uuid, val tenantId: Uuid, val branchId: Uuid?, val role: Role)` + `fun ApplicationCall.principalContext(): PrincipalContext` (parses the JWTPrincipal once). **Replaces and deletes** `CallExtensions.userId()`.
  - SSE exception: browsers' `EventSource` can't set headers (template comment already notes this) — the JWT auth provider is configured with `authHeader { call -> call.request.headers[Authorization] ?: call.request.queryParameters["access_token"]?.let { HttpAuthHeader.Single("Bearer", it) } }` so `GET /events/sse?access_token=...` authenticates through the same provider. (Implementation agent: verify `authHeader` hook name via context7 before coding.)
- **Route guards** (`Auth.kt`): two route-scoped helpers built with `createRouteScopedPlugin`:
  - `Route.requirePermission(p: Permission)` — looks up `role_permissions` map for `principalContext().role`; miss → 403 `{ "code": "FORBIDDEN" }`. ADMIN implicitly has all.
  - `Route.requireModule(m: Module)` — tenant entitlement check via cached `TenantRepository.enabledModules(tenantId)`; miss → 403 `{ "code": "MODULE_DISABLED", "module": "..." }` (01 §7 enforcement point 2).
  - Branch scoping: branch-scoped roles (`CASHIER`, `BAKER`, `BRANCH_MANAGER`) may only write/read rows for their `branchId`; enforced in services (compare request branch to principal branch), not per-route.
- **Passwords:** bcrypt via `at.favre.lib:bcrypt` (pure-JVM, no native deps; cost 12). Verify artifact coordinates via context7 at implementation time. Argon2 rejected only because JVM impls need native bindings — record as accepted trade-off.
- **Refresh flow:** login → access JWT + opaque 256-bit random refresh token (returned raw once, stored SHA-256-hashed in `refresh_tokens`). `POST /auth/refresh` rotates: validates hash + expiry + `revoked_at IS NULL`, revokes old row, issues new pair. Logout revokes. Offline clients keep operating on cached session with an expired access token; only `/sync/*` calls fail-and-retry on reconnect (01 §4).
- **Device sessions + PIN unlock (01 §4 — DECIDED):** POS/baker installs authenticate **per device, not per shift**. `POST /auth/login` accepts optional `deviceId`/`deviceName`; when present, the issued refresh chain is a **device session** — `refresh_tokens` row carries `device_id` + `branch_id` binding, long TTL (§1). Staff unlock/switch on the device via numeric **PINs verified locally** against `pin_hash` values synced in the `USER` pull (§4.2) — the PIN only *attributes* actions (payloads carry cashier/baker ids); the device session is what authenticates every request. PINs never travel to the server for verification; there is no PIN-login endpoint. PINs are set/reset via T4 (`pin?` field, server hashes with the same bcrypt cost as passwords), rate-limited locally on the client (05/06). Fresh offline install still needs one online login to enroll — honest limitation, stated in 03 §8.

---

## 3. REST API surface

All routes under `authenticate("auth-jwt")` except `POST /auth/login`, `POST /auth/refresh`, `GET /` (health). All responses JSON; errors use one typed envelope `{ "code": string, "message": string, "details"?: object }`. Router files under `backend/app/.../api/feature/<area>/controller/`, services under `.../domain/`, DTOs under `.../controller/dto/` — same layout as the existing `events` feature.

Role column = minimum permission (checked via `requirePermission`); Module column = `requireModule` guard (— = none; POS_SALES is always-on, never guarded).

Per 01 §4 (user admin always-on — DECIDED): `MULTI_BRANCH_ROLES` gates **branch structure** (T5 branch CRUD, branch-scoped assignment, oversight views), **never basic user CRUD** — T3/T4 carry only the `USERS_MANAGE` permission guard, no module guard. (With the module off, T4's `branchId` must reference the tenant's single branch or be null.)

| # | Method + path | Permission | Module | Request → Response (sketch) |
|---|---|---|---|---|
| A1 | POST `/auth/login` | public | — | `{tenantSlug, username, password, deviceId?, deviceName?}` → `{accessToken, refreshToken, user{id,displayName,role,branchId}, tenant{id,name,currencyCode,enabledModules[]}, branches[]}` — `deviceId` present ⇒ refresh chain becomes a device session (§2) |
| A2 | POST `/auth/refresh` | public | — | `{refreshToken}` → `{accessToken, refreshToken}` |
| A3 | POST `/auth/logout` | any | — | `{refreshToken}` → 204 |
| T1 | GET `/tenant` | any | — | → `{id,name,slug,currencyCode,enabledModules[],branches[]}` |
| T2 | PUT `/tenant/modules` | MODULES_TOGGLE | — | `{enabledModules[]}` → tenant profile (the entitlement-toggle demo) |
| T3 | GET `/tenant/users` | USERS_MANAGE | — | → `[{id,username,displayName,role,branchId,active}]` |
| T4 | POST `/tenant/users` / PUT `/tenant/users/{id}` | USERS_MANAGE | — | `{username,password?,pin?,displayName,role,branchId?,active}` → user (server hashes `password`/`pin`; neither hash ever returned) |
| T5 | POST `/tenant/branches` / PUT `/tenant/branches/{id}` | MODULES_TOGGLE (GM/ADMIN) | MULTI_BRANCH_ROLES | `{name,active}` → branch (web admin branch CRUD; deletes soft via `active=false`) |
| C1 | GET `/catalog/products` | CATALOG_READ | — | → `[Product]` (incl. inactive for admins via `?includeInactive`) |
| C2 | POST `/catalog/products` | CATALOG_WRITE | — | `{id(client uuid ok, else server rejects missing),name,productType,soldBy,pricePerKgMinor?,priceMinor?}` → Product |
| C3 | PUT `/catalog/products/{id}` | CATALOG_WRITE | — | same body → Product (LWW; new server_seq) |
| C4 | DELETE `/catalog/products/{id}` | CATALOG_WRITE | — | soft: sets `active=false` → 204 |
| C5 | GET `/catalog/formulas` | CATALOG_READ | INVENTORY_FORMULAS | → `[{id,productId,lines:[{ingredientId,basisPoints}]}]` |
| C6 | PUT `/catalog/products/{id}/formula` | CATALOG_WRITE | INVENTORY_FORMULAS | `{id,lines:[{id,ingredientId,basisPoints}]}` (replace-all) → Formula |
| C7 | GET/PUT `/catalog/products/{id}/day-old-policy` | CATALOG_READ / CATALOG_WRITE | — | `{mode:"DISCARD"\|"DISCOUNT",discountBp?}` |
| I1 | GET `/inventory/ingredients` | CATALOG_READ | INVENTORY_FORMULAS | → `[{id,name,lowStockThresholdGrams,active}]` |
| I2 | POST/PUT `/inventory/ingredients(/{id})` | CATALOG_WRITE | INVENTORY_FORMULAS | ingredient body → Ingredient |
| I3 | GET `/inventory/stock?branchId` | STOCK_READ | INVENTORY_FORMULAS | → `[{ingredientId,name,currentGrams,lowStock:bool}]` (Σ movements, SQL `SUM(delta_grams) GROUP BY ingredient_id`) |
| I4 | GET `/inventory/movements?branchId&ingredientId&from&to` | STOCK_READ | INVENTORY_FORMULAS | → paged `[StockMovement]` |
| I5 | POST `/inventory/movements` | STOCK_ADJUST | INVENTORY_FORMULAS | `{id,branchId,ingredientId,deltaGrams,kind:"PURCHASE"\|"MANUAL_ADJUSTMENT",note?,occurredAt}` → `{serverSeq}` (web admin purchase/adjust path — the cut-line fallback surface) |
| S1 | POST `/sync/push` | SYNC | — | §4 envelope → §4 acks |
| S2 | GET `/sync/pull?since&limit&entityTypes?` | SYNC | — | §4 change feed |
| K1 | GET `/analytics/sales-by-day?branchId?&from&to&groupBy=productType?` | ANALYTICS_READ | ANALYTICS_DASHBOARD | → `[{date,branchId,productType?,salesCount,weightGrams,units,revenueMinor}]` |
| K2 | GET `/analytics/revenue?granularity=day\|week\|month&branchId?&from&to` | ANALYTICS_READ | ANALYTICS_DASHBOARD | → `[{period,revenueMinor,salesCount}]` |
| K3 | GET `/analytics/product-mix?branchId?&from&to` | ANALYTICS_READ | ANALYTICS_DASHBOARD | → `[{productType,revenueMinor,shareBp}]` |
| K4 | GET `/analytics/day-old-loss?branchId?&from&to` | ANALYTICS_READ | ANALYTICS_DASHBOARD | → `[{date,discountedRevenueMinor,foregoneMinor}]` (from sale_lines where is_day_old; DISCARD loss estimated from production minus sales is out of scope — approximate shelf, spec §5) |
| K5 | GET `/analytics/stock-levels?branchId?` | ANALYTICS_READ | ANALYTICS_DASHBOARD + INVENTORY_FORMULAS | → I3 shape + `negativeStock:bool` reconciliation warnings (01 §6.4 surfacing) |
| K6 | GET `/analytics/production-summary?branchId?&from&to` | ANALYTICS_READ | ANALYTICS_DASHBOARD | → `[{date,branchId,productId,productName,productType,batchCount,mixWeightGrams}]`, one row per reporting-tz day × branch × product (HU-ANA-0003). `mixWeightGrams` is **dough** weight, not finished bread |
| K7 | GET `/analytics/payment-methods?branchId?&from&to` | ANALYTICS_READ | ANALYTICS_DASHBOARD | → `[{paymentMethod,salesCount,revenueMinor}]` |
| K8 | GET `/analytics/forecast-vs-actual?branchId&productType?&from&to` | ANALYTICS_READ | ANALYTICS_DASHBOARD + DEMAND_FORECASTING | → `[{date,productType,predictedGrams?,predictedUnits?,actualGrams,actualUnits}]` |
| F1 | GET `/forecasts?branchId&from&to` | FORECAST_READ | DEMAND_FORECASTING | → `[Forecast]` (cache read) |
| F2 | POST `/forecasts/refresh` | MODULES_TOGGLE (GM/ADMIN) | DEMAND_FORECASTING | 202; publishes a `ForecastRefreshRequested` message → worker calls ML FastAPI, upserts `forecasts` (details in `08-ml-forecasting.md`) |
| E1 | GET (SSE) `/events/sse?access_token=` | SYNC | — | §7 event stream |

Analytics queries are straight SQL aggregates over `sales`/`sale_lines`/`production_batches`/`stock_movements` using `(tenant_id, branch_id, sold_at)` indexes; "day" is computed in the tenant's timezone — capstone simplification: fixed `America/Santiago` per deployment env var `REPORTING_TZ` (open question below).

Router file plan: `AuthRouter.kt`, `TenantRouter.kt`, `CatalogRouter.kt`, `InventoryRouter.kt`, `SyncRouter.kt`, `AnalyticsRouter.kt`, `ForecastsRouter.kt`, `EventsRouter.kt` (kept, re-authed). `Routing.kt` wires them all.

---

## 4. Sync wire contract (authoritative — 03 references this)

### 4.1 Push — `POST /sync/push`

Request (batched outbox drain; ≤ 200 mutations per call):

```json
{
  "deviceId": "pos-7f3a…",
  "mutations": [
    {
      "mutationId": "uuid",            // == entity id for CREATEs (facts are the only pushed ops)
      "entityType": "SALE" | "PRODUCTION_BATCH" | "STOCK_MOVEMENT",
      "opType": "CREATE",              // facts are append-only; only CREATE exists on the wire
      "clientTimestamp": 1753142400000, // epoch millis, informational
      "payload": { …entity-specific, below… }
    }
  ]
}
```

Payloads (field names = camelCase of §1 columns; ids client-minted UUIDs):
- `SALE`: `{id, branchId, cashierId, soldAt, paymentMethod, totalMinor, lines:[{id, productId, soldBy, weightGrams?, quantity?, unitPriceMinor, isDayOld, dayOldDiscountBp?, lineTotalMinor}]}` — sale + lines ingested **atomically in one transaction**; `SALE_LINE` is not a wire entityType.
- `PRODUCTION_BATCH`: `{id, branchId, bakerId, productId, mixWeightGrams, batchAt, movementsMinted}` — on APPLIED insert, app publishes `ProductionBatchMessage` (§6) after commit.
- `STOCK_MOVEMENT`: `{id, branchId, ingredientId, deltaGrams, kind, note?, occurredAt, sourceBatchId?}` — clients push `PURCHASE`/`MANUAL_ADJUSTMENT`, and the baker client pushes `FORMULA_DECREMENT` with `sourceBatchId` set (01 §8 client-primary decrement; server validates `sourceBatchId` references a batch of the same tenant/branch — else `REJECTED`). Worker-minted decrements exist only on the fallback path (§6).

`tenant_id` is **never** in the payload — always taken from the principal (01 §4). Branch mismatch with a branch-scoped principal → `REJECTED`.

Response (one result per mutation, same order):

```json
{
  "serverTime": 1753142401234,
  "results": [
    { "mutationId": "uuid", "status": "APPLIED",   "serverSeq": 48211 },
    { "mutationId": "uuid", "status": "DUPLICATE", "serverSeq": 48102 },   // ON CONFLICT DO NOTHING hit; ack as success
    { "mutationId": "uuid", "status": "REJECTED",  "error": { "code": "BRANCH_FORBIDDEN" } }
  ]
}
```

Client treats `APPLIED` and `DUPLICATE` identically (clear outbox row); `REJECTED` is terminal (park + surface, see 03). Whole-request failures (401/5xx/network) leave the outbox untouched — safe because ingest is idempotent (01 §6.3).

### 4.2 Pull — `GET /sync/pull?since={serverSeq}&limit={≤500}&entityTypes={csv?}`

Cursor-based over `(tenant_id, server_seq)`:

```json
{
  "changes": [
    { "entityType": "PRODUCT", "opType": "UPSERT", "serverSeq": 48210, "payload": { … } },
    { "entityType": "SALE",    "opType": "UPSERT", "serverSeq": 48211, "payload": { …incl. lines… } }
  ],
  "nextCursor": 48211,
  "hasMore": false
}
```

- Pulled entityTypes: config — `TENANT` (profile+modules), `BRANCH`, `USER` (**with `pinHash` for same-branch users** — the local-unlock verifier, 01 §4; **never `passwordHash`**; tenant-wide users' pinHash rides to every branch's devices), `PRODUCT`, `INGREDIENT`, `FORMULA` (with lines embedded), `DAY_OLD_POLICY`; facts — `SALE`, `PRODUCTION_BATCH`, `STOCK_MOVEMENT` (incl. worker-appended decrements, which is how offline clients converge on stock).
- Config deletes are soft (`active=false` rides the UPSERT) ⇒ no `DELETE` opType needed; facts are never deleted.
- Ordering: strictly ascending `server_seq`; client persists `nextCursor` only after applying the page (03's job).
- Server implementation: one `UNION ALL`-style query is unnecessary — iterate the per-table queries `WHERE tenant_id=? AND server_seq>? ORDER BY server_seq LIMIT ?`, merge-sort by seq, trim to limit. Each synced table's `(tenant_id, server_seq)` index makes this cheap.
- No branch filtering in v1 (tenants are small); every client gets the whole tenant.

---

## 5. SSE — `GET /events/sse`

Keep the template's exact mechanics (verified in code: `install(SSE)`, `sse("events/sse")`, `heartbeat { period=15s }`, `SseConnectionRegistry`, `startEventsConsumer` bridging `SsePushBroker` fanout): per-instance registry + RabbitMQ fanout exchange, snapshot-on-connect, best-effort live push (durable truth = DB via pull).

Changes:
- Registry key changes from `userId` to **`tenantId`** (batch-ready is a tenant/branch broadcast, not per-user): `SseConnectionRegistry.register(tenantId, channel)`; `SsePushEvent.userId` → `tenantId` + optional `branchId` (connections filter by their principal's branch when branch-scoped).
- Auth via `access_token` query param (§2).
- Snapshot-on-connect: replace the batch-progress snapshot with "production batches from the last 24 h" (`event: batch-ready`, one frame per batch) so a reconnecting POS repaints its kitchen feed.
- Event types: `heartbeat`, `batch-ready` (`data` = `{batchId, branchId, productId, productType, mixWeightGrams, batchAt, decrementStatus}`), `sync-available` (optional nudge: "new server_seq exists, pull now" — cheap because the notifier already sees every ingest event; lets online clients pull promptly without polling).

---

## 6. Repurposing the ExampleJob pipeline (exact file mapping)

| Template file (delete/rename) | Replacement | Notes |
|---|---|---|
| `data/.../entity/ExampleJobEntity.kt`, `BatchEntity.kt` | `entity/` domain tables (§1) | old tables dropped with the example |
| `data/.../repository/ExampleJobRepository(+Impl).kt`, `BatchRepository(+Impl).kt`, `model/BatchModels.kt` | `TenantRepository`, `UserRepository`, `RefreshTokenRepository`, `CatalogRepository` (products/formulas/day-old), `IngredientRepository`, `SaleRepository`, `ProductionBatchRepository`, `StockMovementRepository`, `SyncPullRepository`, `AnalyticsRepository`, `ForecastRepository` (+Impls, all `internal`, bound in `DatabaseModule`) | same `suspendTransaction` style |
| `data/.../messagebroker/model/ExampleJobMessage.kt` | `ProductionBatchMessage(batchId, tenantId, branchId, productId, mixWeightGrams)` | published by app after batch commit |
| `data/.../messagebroker/model/JobCompleted.kt` | `BatchProcessed(batchId, tenantId, branchId, status /* APPLIED\|SKIPPED\|FAILED */)` | worker → notifier wake-up; notifier re-reads DB (idempotent, same pattern) |
| `data/.../messagebroker/model/SsePushEvent.kt` | keep, re-key: `(tenantId, branchId?, type, payload)` | |
| `RabbitMqService(+Impl)` | keep; generic over `ProductionBatchMessage` (rename queue/exchange defaults `jobs` → `production`) | topology (DLX, quorum, prefetch, manual ack) unchanged |
| `JobEventBroker(+Impl)` | rename `BatchEventBroker`; payload `BatchProcessed`; exchange default `batch.events` | |
| `SsePushBroker(+Impl)`, `AmqpConfigFactory` | keep as-is | |
| `app/.../feature/events/controller/JobsRouter.kt`, `dto/JobDtos.kt`, `domain/JobService(+Impl).kt` | `feature/sync/controller/SyncRouter.kt` + `domain/SyncIngestService`, `SyncPullService` (production ingest lives inside push handling; no standalone jobs endpoint) | |
| `app/.../feature/events/controller/EventsRouter.kt`, `EventsConsumer.kt`, `domain/SseConnectionRegistry.kt`, `di/EventsModule.kt` | keep files, apply §5 changes | |
| `app/.../api/core/CallExtensions.kt` | delete → `core/Auth.kt` (`PrincipalContext`) | |
| `worker/.../domain/ExampleJobProcessor(+Impl).kt` | `FormulaDecrementProcessor(+Impl)` | see below |
| `notifier/.../domain/NotificationProcessor(+Impl).kt` | keep names; consumes `BatchProcessed`, publishes `batch-ready` SSE push; drop the batch-progress/terminal-CAS logic (a batch is one event, no counting) — debounce map removable | |
| `app/src/test/kotlin/ServerTest.kt` | grows into router test suite (§8) | |

**`FormulaDecrementProcessorImpl` (worker) — fallback only, and the amputable cut-line (01 §8, spec cut-line).** Per 01 §8 the decrement is **computed client-side** in the baker app's local transaction (see `06-baker-mobile.md`); the pushed batch carries `movementsMinted: Boolean` and, when true, its stock movements arrive as ordinary `STOCK_MOVEMENT` mutations in the same sync push. The worker processor is the *fallback path* for batches synced with `movementsMinted=false` (e.g. formula absent from the client cache but present server-side):
1. Load batch by id; if `decrement_status != PENDING` → done (idempotent redelivery).
2. If `movements_minted=true` → mark `APPLIED` (client already minted; the `ON CONFLICT` guard in step 4 makes even a race harmless), publish `BatchProcessed(APPLIED)`, stop. If tenant lacks `INVENTORY_FORMULAS` or product has no active formula → mark `SKIPPED`, publish `BatchProcessed(SKIPPED)`.
3. Else compute per-ingredient grams from `mix_weight_grams` and formula basis points — integer math, `grams_i = mixWeight * bp_i / totalBp`, **largest-remainder rounding so Σ = mixWeight exactly** (01 §5; worked example in `06-baker-mobile.md`). This pure function lives in its own class (`FormulaMath`) for direct unit testing.
4. Insert one `stock_movements` row per ingredient (`kind=FORMULA_DECREMENT`, `delta_grams` negative, `source_batch_id=batchId`, worker-minted UUIDv4 ids, one server_seq each) — `ON CONFLICT (source_batch_id, ingredient_id) DO NOTHING` makes redelivery harmless. Same tx sets `decrement_status=APPLIED`.
5. Publish `BatchProcessed(APPLIED)`. On exception: status `FAILED`, publish, rethrow → nack → DLQ (template pattern kept).

**Amputation switch:** env `DECREMENT_ENABLED=false` on the worker short-circuits step 3–4 into `SKIPPED`. Nothing downstream depends on decrement rows existing (manual adjustment I5 covers stock-in/out; K5 just shows whatever the ledger holds).

`worker/main.kt` / `notifier/main.kt` loops, backoff, shutdown hooks: unchanged.

---

## 7. Config / settings / local dev

`BaseSettings` (backend/data) grows: `JwtSettings(secret, issuer, audience, accessTtlMinutes, refreshTtlDays)` (app only — put on `Settings` in `app`, not `BaseSettings`, since worker/notifier don't need it), `mlSettings(baseUrl, apiKey)` (worker), `DECREMENT_ENABLED` (worker), `REPORTING_TZ` (app).

Env vars per service (app reads via `application.yaml` substitution like today; worker/notifier via `System.getenv` like today):

| Var | app | worker | notifier | default (dev) |
|---|---|---|---|---|
| PORT | ✓ | | | 8080 |
| DB_HOST/PORT/NAME/USER/PASSWORD | ✓ | ✓ | ✓ | localhost/5432/ecopan/postgres/postgres |
| RABBITMQ_HOST/PORT/USER/PASSWORD | ✓ | ✓ | ✓ | localhost/5672/guest/guest |
| RABBITMQ_EXCHANGE/QUEUE/ROUTING_KEY | ✓ | ✓ | | production / production / production |
| RABBITMQ_DEAD_LETTER_* | ✓ | ✓ | | production.dlx / production.dlq |
| RABBITMQ_BATCH_EVENTS_EXCHANGE/QUEUE | | ✓ | ✓ | batch.events |
| RABBITMQ_SSE_EXCHANGE | ✓ | | ✓ | notifications.sse |
| JWT_SECRET / JWT_ISSUER / JWT_AUDIENCE | ✓ | | | dev-secret / ecopan / ecopan-clients |
| ACCESS_TOKEN_TTL_MIN / REFRESH_TOKEN_TTL_DAYS | ✓ | | | 15 / 30 |
| REPORTING_TZ | ✓ | | | America/Santiago |
| DECREMENT_ENABLED | | ✓ | | true |
| ML_BASE_URL / ML_API_KEY | | ✓ | | http://localhost:8000 / (empty) |
| SEED_PROFILE | ✓ | | | (empty; **comma-separated list** of profile names — each loads `seed/<name>.json` idempotently at startup, 01 §7; no seed endpoint/CLI exists — see `11-seeds-and-demo.md`) |
| DEVICE_SESSION_TTL_DAYS | ✓ | | | 180 (§2 device sessions) |

`docker-compose.yaml` (repo root or `backend/`; new file) for local dev — infra only, services run from Gradle:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    ports: ["5432:5432"]
    environment: { POSTGRES_DB: ecopan, POSTGRES_USER: postgres, POSTGRES_PASSWORD: postgres }
    volumes: [pgdata:/var/lib/postgresql/data]
    healthcheck: { test: ["CMD-SHELL", "pg_isready -U postgres"], interval: 5s }
  rabbitmq:
    image: rabbitmq:4-management-alpine
    ports: ["5672:5672", "15672:15672"]
    healthcheck: { test: ["CMD", "rabbitmq-diagnostics", "ping"], interval: 10s }
volumes: { pgdata: }
```

Dev loop: `docker compose up -d` → `./gradlew :backend:app:run` (+ `:backend:worker:run`, `:backend:notifier:run` when exercising the pipeline). Azure packaging is `10-azure-deployment.md`'s problem.

---

## 8. Testing plan (01 §10)

**Repository test DB decision: Testcontainers (PostgreSQL), not H2 PG-mode.** Rationale (recorded per 01 §10): the schema leans on PG-specific behavior H2's compatibility mode does not faithfully emulate — `INSERT ... ON CONFLICT DO NOTHING` as the sync idempotency primitive, a shared global sequence read via `nextval` inside transactions, `timestamptz` semantics, and timezone-aware `date_trunc` in analytics aggregates. Testing those against H2 would test a different database. Cost is acceptable: Docker is already the dev baseline (compose above); use a single reusable container per test JVM (`@Testcontainers` singleton pattern, `withReuse(true)`), truncate tables between tests. Verify current Testcontainers JUnit5 API via context7 at implementation time.

| Layer | Tool | Cases (minimum) |
|---|---|---|
| Routers | Ktor `testApplication` (exists: `ServerTest.kt` pattern) with Koin overridden to fake repos | login happy/wrong-password; refresh rotation + reuse-after-rotate → 401; `requirePermission` 403 matrix (one per role boundary); `requireModule` 403 with `MODULE_DISABLED` body; tenant isolation (principal A cannot read tenant B rows — fake repo asserts received tenantId); push envelope validation errors; pull paging (`hasMore`/`nextCursor`) |
| Repositories | JUnit + Testcontainers PG | sale+lines atomic insert; duplicate sale id → DO NOTHING + original server_seq returned; server_seq strictly increasing across mixed tables; pull query returns rows > cursor in order; stock Σ with negative result; decrement movement unique `(source_batch_id, ingredient_id)` conflict ignored; analytics aggregates against a small fixed fixture |
| `FormulaMath` | plain unit tests | Σ grams == mixWeight for adversarial bp sets (largest-remainder), single-line formula, zero-bp line rejected, property-style random bp vectors |
| `FormulaDecrementProcessorImpl` | unit tests, fake repos/brokers | PENDING→APPLIED writes N movements + publishes APPLIED; redelivery after APPLIED is a no-op; no formula → SKIPPED; `DECREMENT_ENABLED=false` → SKIPPED; repo throw → FAILED published + rethrown (DLQ path) |
| Notifier | unit tests, fakes | `BatchProcessed` → exactly one `batch-ready` SsePushEvent with tenant/branch keying |

Broker impls (`RabbitMqServiceImpl` etc.) are template-proven; no new broker tests beyond keeping compile-level type changes green.

---

## 9. Work items (ordered)

Each item: paths are create/modify targets; AC = acceptance criteria. Run `./gradlew :backend:app:test` (+ builds of worker/notifier) after each.

1. **Docker compose + settings groundwork** — create `docker-compose.yaml`; extend `backend/data/.../core/BaseSettings.kt` (rename job→production/batch-event broker settings), `app/.../core/Settings.kt` + `application.yaml`, `worker`/`notifier` `Settings.kt` per §7 env table. AC: `docker compose up -d` healthy; all three services boot against it unchanged in behavior.
2. **Schema + core repositories** — new entity files (§1 tables incl. `SEQUENCE server_seq` bootstrap in `DatabaseFactory.connectToDb(init=true)`); repositories `TenantRepository`, `UserRepository`, `RefreshTokenRepository`, `CatalogRepository`, `IngredientRepository` (+Impls) in `backend/data/.../repository/`; bind in `DatabaseModule.kt`. Delete nothing yet. AC: Testcontainers suite green for CRUD + server_seq assignment; `SchemaUtils.create` produces all tables/indexes (assert via information_schema query in a test).
3. **Auth** — `app/.../core/Auth.kt` (configureAuth, PrincipalContext, requirePermission, requireModule), `feature/auth/` (AuthRouter, AuthService, dtos), bcrypt dependency, delete `CallExtensions.kt`. Seed `role_permissions` in `connectToDb(init=true)`. AC: `testApplication` login/refresh/guard tests green; every existing route now 401s unauthenticated.
4. **Tenant + catalog + inventory routers** — `feature/tenant/`, `feature/catalog/`, `feature/inventory/` (routers T1–T4, C1–C7, I1–I5 + services/dtos); wire in `Routing.kt` + Koin `EventsModule`-style feature modules. AC: router tests incl. `MODULE_DISABLED` cases; config writes bump server_seq.
5. **Fact repositories + sync endpoints** — `SaleRepository`, `ProductionBatchRepository`, `StockMovementRepository`, `SyncPullRepository`; `feature/sync/` (SyncRouter S1/S2, SyncIngestService, SyncPullService, wire DTOs exactly per §4). AC: idempotent re-push test (DUPLICATE + original seq); atomic sale+lines; pull paging test; branch-forbidden REJECTED test. **This freezes the 03 wire contract.**
6. **Pipeline swap** — rename broker messages/brokers per §6; worker `FormulaDecrementProcessor(+Impl)` + `FormulaMath`; notifier rewrite; app publishes `ProductionBatchMessage` on batch APPLIED ingest; SSE re-key + snapshot per §5; **delete** ExampleJob/Batch entities, repos, routers, dtos, `JobService`, old messages. AC: §8 processor/notifier/FormulaMath suites green; end-to-end smoke (manual, compose up): push batch → movements appear in pull + `batch-ready` frame observed on `/events/sse`; `DECREMENT_ENABLED=false` yields SKIPPED with zero movements.
7. **Analytics + forecasts** — `AnalyticsRepository`, `ForecastRepository`; `feature/analytics/` (K1–K8), `feature/forecasts/` (F1, F2 publishing `ForecastRefreshRequested`; worker handler may stub the ML call until `08`). AC: aggregate correctness tests over a fixture tenant (known totals); K5 flags negative stock; K8 joins cache vs actuals.
8. **Seed loader** — `SEED_PROFILE` startup hook accepting a comma-separated profile list, reading `backend/app/src/main/resources/seed/<name>.json` per name (tenants, branches, users incl. bcrypt-at-load passwords **and PINs**, products, ingredients, formulas, policies). Boot-time only — no admin seed endpoint, no CLI path (01 §7); demo reset = restart. Profiles themselves authored in `11-seeds-and-demo.md`. AC: booting with `SEED_PROFILE=reference,unipan` yields two login-able tenants; idempotent on reboot (upsert by id).
9. **Swagger/docs pass** — keep `configureHttp()` swagger at `/docs`; ensure new routes appear. AC: `/docs` renders; root health 200 test still green.

Do not start item 1 before `03-sync.md` exists in draft? — No: 02 owns the contract (§4); 03 adapts. Proceed in order above.

## Open questions

1. **Offline login credential: DECIDED (01 §4 device session + PIN unlock).** Devices hold a long-lived device session (`refresh_tokens` + `device_id`/`branch_id`, §2); staff unlock locally against `pin_hash` synced in the `USER` pull (§4.2). `users.pin_hash` is the one nullable field this cost; password hashes never leave the server. Closed.
2. **Reporting timezone** is a deployment env var (`REPORTING_TZ`), not a tenant column — violates "tenant-specific value is a row" in spirit. Promote to `tenants.timezone` if the two seed profiles ever need to differ; cheap column, deferred.
3. **Pull branch filtering:** v1 sends the whole tenant to every device. Fine at capstone scale; revisit only if seed profile 2 (3 branches) makes pull payloads awkward in the demo.
4. **`sync-available` SSE nudge** (§5) is optional; drop without consequence if the notifier change grows — offline clients poll-pull on their own schedule regardless (03).
5. **Forecast refresh trigger:** F2 manual endpoint only, or also a nightly schedule? Nightly cron belongs to `10-azure-deployment.md` (Container Apps job) — backend just needs the same `ForecastRefreshRequested` message either way.
