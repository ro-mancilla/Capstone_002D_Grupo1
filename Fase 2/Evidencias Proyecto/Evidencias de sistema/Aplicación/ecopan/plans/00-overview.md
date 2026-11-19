# 00 — Plan Overview & Index

Implementation planning for the **Bakery Management & Demand-Prediction System** (capstone), built on the ecopan KMP+Ktor template. Source of truth for requirements: `spec/SPECS.md` (technical baseline) and `spec/bakery_system_scope_spec.md` (rationale/scope). Source of truth for code conventions: `CLAUDE.md` + the `writing-kmp-features` skill (`.claude/skills/writing-kmp-features/`).

**These plans contain zero production code.** They exist to direct implementation agents. Any code-like fragments (interface sketches, schema tables, config keys) are illustrative contracts, not final code — implementation agents must verify all library APIs via context7 MCP before writing real code.

---

## System shape (recap)

Multi-tenant, multi-branch bakery platform. One backend + three clients:

| Client | Platform | Mode | Role |
|---|---|---|---|
| Desktop POS | Compose Desktop (`:jvm:app`) | **Offline-first** | Cashier — system of record for 100% of sales; hardware integration |
| Baker mobile | Android + iOS (both first-class) | **Offline-first** | Baker — batch logging (mix weight + type), production events |
| Manager dashboard | React (new `webApp/`) | Online-only | Manager — KPIs, analytics, admin, config |

Cloud: Azure — Ktor backend (`backend/*`), PostgreSQL, RabbitMQ, Python ML service (`ml/`).

**The spine:** offline-first + hardware-integrated POS captures *type + weight + price* per sale → that exact sales record is what makes per-(branch, product-type) demand forecasting possible. Systems work and ML are one thread.

---

## Plan index

| Doc | Area | Depends on |
|---|---|---|
| `00-overview.md` | This file — index, phasing, conventions | — |
| `01-architecture-decisions.md` | **Binding cross-cutting decisions** — module map, sync/conflict policy, tenancy, auth, units/IDs, entitlements. Every other plan defers to this. | — |
| `02-backend.md` | Ktor backend: Exposed schema, REST API, auth, sync endpoints, SSE, RabbitMQ pipeline repurposing | 01 |
| `03-sync.md` | Offline-first sync engine (client `:data:sync` + protocol details) | 01, 02 |
| `04-hardware.md` | Hardware abstraction: driver interfaces, mock/manual/serial drivers, scale risk spike | 01 |
| `05-pos-desktop.md` | Desktop POS feature: screens, weigh-and-sell, unit sales, payment, receipts | 01, 03, 04 |
| `06-baker-mobile.md` | Baker mobile feature: batch logging, formula decrement, batch-ready events | 01, 03 |
| `07-web-dashboard.md` | React manager dashboard: Vite, Tailwind v4 theme, shadcn/ui, TanStack Query | 01, 02 |
| `08-ml-forecasting.md` | Python ML: Favorita dataset, baselines, model, eval protocol, FastAPI service | 01, 02 |
| `09-theming.md` | Porting `spec/index.css` tokens to Compose Material3 in `:core:ui`; shared visual identity | — |
| `10-azure-deployment.md` | Azure hosting: Container Apps, PostgreSQL, RabbitMQ, ML service, CI | 02, 08 |
| `11-seeds-and-demo.md` | Two tenant seed profiles (genericity proof), demo script, hardware evidence plan | all |

---

## Phasing (build order)

Ordered by risk burn-down (spec §6.1) and dependency:

- **Phase 0 — Scale spike (week one, isolated).** Serial scale integration in a throwaway harness *before* any POS screen exists. Go/no-go gate: if the scale can't be read, `ManualScale` becomes primary. → `04-hardware.md`
- **Phase 1 — Domain foundation.** Client Room schema + backend Exposed schema + domain `:data:*` modules (catalog, sales, production, inventory, tenant). Strip Notes example last, once a real feature compiles against the new modules. → `01`, `02`
- **Phase 2 — Backend API + auth.** Login/JWT, tenant scoping, catalog/config CRUD, seed loading. → `02`
- **Phase 3 — Sync engine.** Outbox, push/pull protocol, conflict policy, network-state indicator component. Testable headless before any feature UI. → `03`
- **Phase 4 — POS desktop.** Weigh-and-sell flow on `ManualScale`/`MockScale` first; wire `SerialScale` from Phase 0; receipts, drawer, payment method. → `05`
- **Phase 5 — Baker mobile.** Batch logging, formula decrement, batch-ready → SSE → POS visibility. → `06`
- **Phase 6 — Web dashboard.** Admin surfaces first (they unblock seeding real config), then KPIs. Can start any time after Phase 2; runs in parallel with 4–5. → `07`
- **Phase 7 — ML.** Dataset + baselines + model + eval offline; FastAPI service; forecast display in dashboard. Independent of Phases 3–5; can run in parallel from Phase 1. → `08`
- **Phase 8 — Hardening + demo.** Two-tenant seed profiles, entitlement toggle demo, driver-swap demo, dev-time scale recording, Azure deploy. → `10`, `11`
- **Theming (`09`)** lands with the first real screen and evolves continuously.

**Cut-lines (from spec, keep amputable):**
- Formula-based raw-material auto-decrement → degrades to manual stock adjustment (manager, web). Nothing may *depend* on exact auto-decrement.
- iOS-specific polish → both mobile platforms are deliverables, but the codebase is ~99% shared; iOS work is limited to existing platform-actual patterns (the template already has all iOS actuals in place).

---

## Global conventions (binding on all implementation agents)

1. Follow the `writing-kmp-features` skill exactly: api/impl split, `internal` everything except interfaces and DI module vals, UDF (Action/State/Event), `Channel` for one-shot events, no hardcoded dispatchers, convention plugins via `alias(libs.plugins.f776.xxx)`.
2. **Context7 MCP is mandatory** before writing code against any library API (Ktor, Room KMP, Koin, Navigation3, Exposed, jSerialComm, TanStack Query, LightGBM, FastAPI…). Never guess signatures; never grep `.def`/`.jar` files.
3. After changes: `./gradlew :<module>:compileKotlin<Ios|Android|Jvm>` for affected targets, then root `./gradlew test` + `:<module>:jvmTest` for touched KMP modules.
4. Room package root stays `com.f776.unigrid.core.database` (known inconsistency — do not fix). Everything else: `com.f776.ecopan.*` per module path convention.
5. Every tenant-specific value is **data (a row), never an `if` in code**. Genericity test: the second seed profile (`11-seeds-and-demo.md`) must work with zero code change.
6. Module entitlements are checked at feature boundaries (nav registration + backend route guard), per `01-architecture-decisions.md` §7.
7. All quantities in integer grams; all money in integer minor units. See `01` §5. No floating-point ledgers.
