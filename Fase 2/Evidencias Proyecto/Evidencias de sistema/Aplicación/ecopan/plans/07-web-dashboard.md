# 07 — Web Manager Dashboard (React, `webApp/`)

Online-only manager client (spec `SPECS.md` Client 3, scope spec §4.3). Read-heavy KPIs + the **entire genericity config surface** (scope spec §7): everything tenant-specific the demo edits lives here. Defers to `01-architecture-decisions.md` on all cross-cutting points (§4 auth, §7 entitlements, §9 stack). Zero production code in this doc — contracts and steps for implementation agents, who must re-verify APIs via context7 before coding.

**Library setup below was verified via context7** (Tailwind v4 Vite install docs, shadcn/ui Vite guide, TanStack Query v5 `queryOptions`/invalidation docs, TanStack Router file-based routing + `beforeLoad` guard docs, Recharts CSS-variable color examples). Versions drift — re-verify at implementation time.

---

## 1. Scaffold plan

`webApp/` at repo root. **Plain npm project — not a Gradle module.** Do not register it in `settings.gradle.kts`; do not wrap npm in Gradle tasks. It has its own `package.json`, lockfile, `npm run dev|build|test` lifecycle. CI builds it as a separate step (see `10-azure-deployment.md`).

Generation steps (verified against Tailwind v4 + shadcn Vite docs):

1. `npm create vite@latest webApp -- --template react-ts` (React + TS template is required by shadcn).
2. `npm install tailwindcss @tailwindcss/vite` — Tailwind v4 has **no `tailwind.config.js` and no PostCSS step**; the Vite plugin + CSS-first config is the whole setup.
3. `vite.config.ts` plugins, **in this order**: `tanstackRouter({ target: 'react', autoCodeSplitting: true })` (must precede the React plugin, per TanStack Router docs), then `react()`, then `tailwindcss()`. Add `resolve.alias` `"@" → ./src` + `@types/node` dev-dep, and matching `paths` in `tsconfig.json`/`tsconfig.app.json` (shadcn CLI requires the alias).
4. **Copy `spec/index.css` verbatim to `webApp/src/index.css`** — it is already the correct Tailwind v4 shape (`@import "tailwindcss"`, `@custom-variant dark (&:is(.dark *))`, `:root`/`.dark` variable blocks, `@theme inline` mapping, `@layer base`). It is the app stylesheet; import it from `main.tsx`. Do not fork the tokens — `spec/index.css` stays canonical; if tokens change, re-copy.
5. `npx shadcn@latest init` (detects Vite + Tailwind v4; point `components.json` `tailwind.css` at `src/index.css`, `cssVariables: true`). Then `npx shadcn@latest add` per-component as needed (button, input, card, table, dialog, select, switch, form, sonner, dropdown-menu, tabs, badge, sidebar, skeleton).
6. `npm install @tanstack/react-router @tanstack/react-query` and dev-deps `@tanstack/router-plugin`, `@tanstack/eslint-plugin-query` (enable `prefer-query-options` rule), `@tanstack/react-query-devtools`, `@tanstack/router-devtools`.
7. `npm install recharts`.
8. Test deps: `vitest`, `@testing-library/react`, `@testing-library/user-event`, `jsdom`, `msw`.

Dependency list + purpose:

| Package | Purpose |
|---|---|
| `react`, `react-dom`, `typescript`, `vite`, `@vitejs/plugin-react` | Base scaffold |
| `tailwindcss`, `@tailwindcss/vite` | Tailwind v4, CSS-first config from `index.css` |
| shadcn-generated components (+ `lucide-react`, `class-variance-authority`, `clsx`, `tailwind-merge` pulled by CLI) | UI kit over the theme tokens; code is vendored into `components/ui/`, not a dep |
| `@tanstack/react-router` + `@tanstack/router-plugin` | Type-safe file-based routing, route guards |
| `@tanstack/react-query` | ALL server state. **No other state library** (no Redux/Zustand/Jotai) — the only client state is the auth session + UI ephemera (React context/`useState`) |
| `recharts` | Charts (see §5) |
| `vitest`, `@testing-library/*`, `jsdom`, `msw` | Tests (§10) |

**Dark mode:** the theme file's `@custom-variant dark (&:is(.dark *))` means dark = `.dark` class on `<html>`. Toggle component (header): three-state light/dark/system, persisted in `localStorage`, applied by a tiny inline script in `index.html` before first paint (avoid FOUC — same pattern Recharts' own site uses: read storage, fall back to `prefers-color-scheme`, set class). No Tailwind config needed — v4 variant is already declared in the CSS.

**Fonts:** tokens name Libre Baskerville / Lora / IBM Plex Mono. Self-host via `@fontsource` packages (or vendored woff2 + `@font-face` in `index.css`) — no Google Fonts CDN at runtime.

---

## 2. Project structure

```
webApp/
  package.json  vite.config.ts  tsconfig.json  components.json  index.html
  src/
    main.tsx                 # QueryClientProvider + RouterProvider + theme bootstrap
    index.css                # copied from spec/index.css (canonical tokens)
    routes/                  # TanStack Router file-based routes (plugin generates routeTree.gen.ts)
      __root.tsx             # root: router context { auth, queryClient }, devtools, Toaster
      login.tsx
      _authed.tsx            # layout: requires session; app shell (sidebar nav, header, theme toggle)
      _authed/index.tsx      # redirect → dashboard (or first entitled section)
      _authed/dashboard/     # ANALYTICS_DASHBOARD guard on this segment's layout
        index.tsx  sales.tsx  branches.tsx  day-old.tsx  stock.tsx  forecast.tsx
        production.tsx       # HU-ANA-0003 (built as `_authed/analytics/production.tsx`): kg de masa per product × branch
      _authed/admin/         # GENERAL_MANAGER/ADMIN role guard on segment layout
        products.tsx  formulas.tsx  day-old-policies.tsx
        ingredients.tsx  stock-entry.tsx
        branches.tsx  users.tsx  modules.tsx  tenant.tsx
    api/
      http.ts                # fetch wrapper: base URL, JSON, auth header, refresh, ApiError
      auth.ts                # login/refresh/logout calls + token storage
      types.ts               # typed DTOs mirroring backend (single file or per-resource)
      queries/               # queryOptions factories + mutation hooks, one file per resource
        catalog.ts formulas.ts dayOld.ts inventory.ts branches.ts users.ts
        tenant.ts kpis.ts forecast.ts events.ts
    features/                # page-level composition per surface (components + view logic)
      dashboard/  admin-catalog/  admin-formulas/  admin-inventory/
      admin-org/  admin-modules/  auth/
    components/
      ui/                    # shadcn CLI output — do not hand-edit beyond theming
      charts/                # thin Recharts wrappers bound to --chart-* tokens
      shared/                # PageHeader, MoneyText, WeightText, EmptyState, ErrorState, EntitlementGate
    lib/
      utils.ts               # shadcn cn()
      i18n.ts                # strings table (§8)
      format.ts              # minor-units→decimal, grams→kg, bp→%, es-CL Intl formatters
      entitlements.ts        # ModuleId type + hasModule(session, module)
```

Convention: `routes/` files are thin (guard + mount a `features/` component). Server-data access only via `api/queries/*`; components never call `fetch` directly.

### API client (`api/http.ts`)

- `fetch` wrapper, JSON in/out, base URL from `import.meta.env.VITE_API_BASE_URL`.
- **JWT per 01 §4:** access (~15 min) + refresh token from `POST /auth/login`. Access token in memory (module-scoped), refresh token in `localStorage` (capstone-acceptable; note the XSS trade-off vs httpOnly cookie in the doc comment — reconciled with 02: refresh is a body-carried opaque rotating token, no cookies, so localStorage stands).
- **Refresh flow:** on 401, single-flight `POST /auth/refresh` (concurrent 401s await one shared refresh promise), retry the original request once. **Refresh failure → hard logout:** clear tokens, clear the Query cache, `router.navigate({ to: '/login', search: { redirect } })`. No offline grace — this client is online-only (unlike the KMP clients' cached-session behavior in 01 §4).
- Non-2xx → typed `ApiError { status, code, message }` matching the backend's typed error body (01 §7.2's 403 body included). Never scope by tenant from the client; the token's principal does that server-side (01 §4).
- **Typed DTOs** in `api/types.ts` mirror backend wire types exactly: `amountMinor: number` (integer), `weightGrams: number`, `bp: number`, ISO-8601 strings for instants, string UUIDs. Conversions to display units live only in `lib/format.ts`. Hand-written and reconciled against `02-backend.md`; codegen from OpenAPI is an optional later upgrade (open question).

### TanStack Query conventions (verified v5 patterns)

- **`queryOptions` factory per resource** (docs-recommended for type safety + key/fn pairing), e.g. `productsQuery(tenantScope)` → `queryOptions({ queryKey: ['products'], queryFn: ... })`; parameterized keys like `['kpi','sales',{branchId,from,to,groupBy}]`.
- **Mutations: `useMutation` + `onSuccess: () => queryClient.invalidateQueries({ queryKey: [...] })`** — the docs' invalidate-from-mutation pattern; await the invalidation when the UI must not show stale data post-save. No optimistic updates in v1 (admin writes are rare; keep it simple).
- Route `loader`s may `queryClient.ensureQueryData(xQuery())` for prefetch; components still `useQuery`/`useSuspenseQuery` the same options object.
- Defaults: `staleTime` 30 s for config resources, 0 for KPI queries; `retry: 1`; no `persistQueryClient` (§7).

---

## 3. Auth pages + route guards

- **`/login`:** tenant slug (or preselected via env for demo) + username + password → `POST /auth/login` (02 A1) → `{ accessToken, refreshToken, user{id,displayName,role,branchId}, tenant{id,name,currencyCode,enabledModules[]}, branches[] }`; the client assembles its `session = { userId, tenantId, branchId?, role, enabledModules, tenantName, currencyCode, branches }` from `user` + `tenant`. Store; navigate to `?redirect` or `/dashboard`.
- **Who may log in here:** `BRANCH_MANAGER`, `GENERAL_MANAGER`, `ADMIN` (01 §4 roles). `CASHIER`/`BAKER` get a friendly "use the POS/baker app" rejection (client-side check on role after successful auth; backend also 403s manager-only routes).
- **`_authed` layout route:** `beforeLoad` throws `redirect({ to: '/login', search: { redirect: location.href } })` when no session — the documented TanStack Router guard pattern (router context carries `{ auth, queryClient }` from `__root`). Renders the app shell: shadcn `sidebar` styled with `--sidebar-*` tokens, header with branch selector (GENERAL_MANAGER only, when MULTI_BRANCH_ROLES on), theme toggle, user menu (logout).
- **Entitlement-driven nav (01 §7.3):** nav config is data — `[{ label, to, module?: ModuleId, minRole? }]` filtered through `hasModule(session, module)` and role. Disabled module → section absent from the sidebar entirely (hidden, not greyed).
- **Route guards:** segment layouts (`_authed/dashboard`, `_authed/forecast` subpaths, admin formula routes) check entitlement/role in `beforeLoad` → redirect to `/dashboard` (or first entitled route) with a toast. Guards are belt-and-suspenders: the backend's `requireModule` 403 (01 §7.2) is the real enforcement; a 403 that slips through (module toggled off mid-session) renders a shared "module not enabled" `ErrorState`, not a crash, and triggers a session/tenant-profile refetch.
- **Role gating inside admin:** `BRANCH_MANAGER` sees dashboard scoped to their branch + inventory stock-entry/adjustment only; full admin surface requires `GENERAL_MANAGER`/`ADMIN`. Same table-driven capability lookup idea as 01 §4 — one `can(role, capability)` map, no scattered role ifs.

---

## 4. Dashboard pages (ANALYTICS_DASHBOARD module)

All KPI data comes from backend aggregation endpoints (§9) — the web app never aggregates raw sale rows itself beyond presentational totals. Global filters: branch (or "all", GM only), date range presets (today / 7d / 30d / custom).

| Page | Content |
|---|---|
| `dashboard/` (overview) | KPI stat cards: sales today (revenue, tx count, kg sold), vs yesterday delta; low-stock count; unsynced-warnings count. Mini sales sparkline. |
| `sales` | Sales over time by product type: line/area chart (day buckets) + stacked bar by type; table below with per-type totals. Weight-sold products show kg, unit-sold show pieces — both feed revenue. |
| `branches` | Revenue by branch: bar chart + table (requires MULTI_BRANCH_ROLES for >1 branch to exist; page still renders with one branch). |
| `day-old` | Day-old outcomes per product (02 K4 `/analytics/day-old-loss`): DISCARD → loss (est. kg + revenue-equivalent), DISCOUNT → recovered revenue at the policy's `discountBp`. Grouped bar (lost vs recovered) + policy column so the manager sees policy↔outcome pairing (spec ML §: policy feeds forecasting framing). |
| `stock` | Current raw-material stock levels (Σ movements, from backend), reorder-ish low-stock highlighting (threshold per ingredient, config row). **Reconciliation warnings per 01 §6.4:** negative-stock ingredients surfaced prominently (destructive badge + drill-in to the movement ledger) — negative is allowed and *surfaced*, never hidden. |
| `forecast` (also needs DEMAND_FORECASTING) | Next-7-days forecast per (branch, product type): line chart forecast vs recent actuals overlay; per-type small-multiples or type selector. Data from the backend's `forecasts` cache via 02 F1 (`GET /forecasts?branchId&from&to`); the forecast-vs-actual overlay comes from 02 K8 (details `08-ml-forecasting.md`). Empty/cold-start state is a designed state ("collecting history…"), not an error. |

**Chart library: Recharts** (confirmed recommendation). Rationale: first-class React components, SVG, tiny learning curve, and — verified in its docs — `stroke`/`fill` accept CSS custom properties directly (`stroke="var(--chart-1)"`), so charts pick up `--chart-1..5` from `index.css` and re-theme automatically on `.dark` toggle with zero JS. Build 2–3 thin wrappers in `components/charts/` (TimeSeriesChart, GroupedBarChart) that bake in: series colors cycling `var(--chart-1..5)`, grid/axis using `var(--border)`/`var(--muted-foreground)`, tooltip styled as a `card`, `ResponsiveContainer`. Alternative considered: visx (more control, much more code) — rejected for capstone scope.

All money renders through `lib/format.ts`: minor units → `Intl.NumberFormat('es-CL', { style:'currency', currency })` (CLP is zero-decimal — the formatter handles it; never divide by 100 blindly, use the currency's fraction digits). Weights: grams → kg with es-CL decimal comma.

---

## 5. Admin surfaces (the genericity config surface — scope spec §7)

Every form here edits **tenant config rows** — this section *is* the "engine + config" demo. Shared pattern per resource: list page (shadcn `table`, search) → create/edit `dialog` or detail pane → `useMutation` → invalidate list. Destructive ops confirm. All writes are LWW-by-serverSeq per 01 §6.2 — acceptable here precisely because these are online, rare, human-reviewed; no conflict UI needed (note this in code comments for the defense).

| Surface | Route | Config rows edited | Key UI rules |
|---|---|---|---|
| Product catalog | `admin/products` | `Product` | Fields: name, type, `soldBy` (WEIGHT/UNIT select), price. **Storage is minor units; display/entry is decimal** via currency-aware formatter (CLP: no decimals). `soldBy=WEIGHT` → "price per kg" field (`pricePerKgMinor`); `UNIT` → "unit price" (`priceMinor`). Switching `soldBy` swaps the field. |
| Formulas editor | `admin/formulas` | `Formula` rows per product | Baker's %: per-ingredient percent input, **stored as basis points** (display 65% ↔ 6500 bp; 2-dp entry max). **Flour row fixed at 100% (10000 bp), not editable, always present.** Show live computed preview: for a sample mix weight, per-ingredient grams via the 01 §5 integer/largest-remainder math (display-only mirror of the KMP logic — the ledger math runs on the baker client, which mints the decrement movements, with the backend worker as fallback per 01 §8 / 02 §6; this preview is illustrative). Only shown/enabled when INVENTORY_FORMULAS is on. |
| Day-old policy | `admin/day-old-policies` | `DayOldPolicy` per product | Per product (02 C7 `GET/PUT /catalog/products/{id}/day-old-policy`): mode `DISCARD` \| `DISCOUNT`; `DISCOUNT` reveals a percent input, **stored as `discountBp`** (30% ↔ 3000 bp). Table view over all products for at-a-glance policy audit. |
| Ingredients + purchase entry | `admin/ingredients`, `admin/stock-entry` | `Ingredient`; **appends stock movements** | Ingredient CRUD (name, low-stock threshold). Purchase entry = append `+grams` movement (entered in kg, stored grams) with note. **Manual stock adjustment = the cut-line fallback (00 §cut-lines):** signed movement `±kg` + reason — must work even if formula auto-decrement is amputated; never edits a stored quantity, always appends (01 §6.1). Movement ledger view per ingredient. |
| Branches | `admin/branches` | `Branch` | CRUD name via 02 T5 — **gated on MULTI_BRANCH_ROLES** (01 §4: the module gates branch structure); with it off, the single branch renders read-only from T1's `branches[]`. |
| Users + roles | `admin/users` | `User` | **Always-on — no module gating** (01 §4: user admin is core). CRUD: username, password set/reset, **PIN set/reset** (numeric, the device-unlock credential per 01 §4 — sent as `pin?` in 02 T4, server hashes, never displayed back), role (seeded role list from 01 §4 — roles are **not** user-definable), branch (nullable = tenant-wide, disabled for branch-scoped roles). |
| Module toggles | `admin/modules` | `tenant.enabled_modules` | shadcn `switch` per module; POS_SALES pinned on/disabled. **This is the entitlement demo switch** — toggling refetches the tenant profile, nav re-filters live. Copy under each switch names what disappears. |
| Tenant profile | `admin/tenant` | `Tenant` | Name, currencyCode (display-only or GM-editable), read-only tenant id — mostly the "second seed profile" inspection surface for `11-seeds-and-demo.md`. |

Forms: shadcn `form` (react-hook-form + zod, as the shadcn form component scaffolds) with zod schemas that parse display values → wire integers (decimal price → minor units, % → bp) in one place per form.

---

## 6. Live updates: batch-ready / production events

**Decision: SSE via `EventSource` to `GET /events/sse`, scoped to the dashboard overview + stock pages; polling as the degraded path.**

Justification: the backend already fans out `SsePushEvent`s through `SseConnectionRegistry` for exactly this (01 §8 — batch-ready flow ends "→ SSE → online POS/**web** clients"); reusing it costs a ~50-line hook and demos the pipeline end-to-end at the defense. Pure `refetchInterval` polling would work but wastes the existing capstone-relevant plumbing and adds latency to the "batch ready" moment.

Implementation sketch: `useServerEvents()` hook (in `api/queries/events.ts`) opens `EventSource` on `GET /events/sse?access_token=<accessToken>` (reconciled with 02 §2: `EventSource` cannot set headers, so the backend's JWT provider also accepts the `access_token` query param), and on relevant event types **invalidates the matching query keys** (`['kpi', ...]`, `['stock']`, `['batches']`) — SSE is a cache-invalidation signal, not a second data channel; data still flows through normal queries. On `EventSource` error: browser auto-reconnects; additionally set `refetchInterval: 60_000` on the affected queries as the fallback so a dead SSE connection degrades to polling, not staleness. Toast ("Lote listo: Marraqueta — Sucursal Centro") on batch-ready.

---

## 7. Online-only posture (spec §4.3 — enforced, not just assumed)

- **No offline caching beyond Query's in-memory defaults.** No `persistQueryClient`, no service worker, no IndexedDB, no PWA manifest/offline shell. A dropped connection shows error states with retry — that is correct behavior for this client.
- **No POS logic.** No sale creation, no weighing, no payment, no receipt anything — the spec explicitly forbids a second POS growing here. The only write paths are the admin config surfaces + stock movements (§5). Reviewers of PRs against `webApp/` should treat any `POST /sales`-shaped code as a spec violation.
- No outbox, no sync module, no conflict handling beyond rendering the backend's LWW result.

---

## 8. i18n (es-CL primary)

Centralize strings from day one, even though only Spanish ships in v1: `lib/i18n.ts` with a typed `strings` object (`const t = strings.es` — a `t('key')` lookup or plain typed object; no i18next dependency needed for one locale, but **zero hardcoded UI strings in components** is the rule, so adding `en` later is a data change, consistent with the project's config-not-code thesis). Dates/numbers/currency exclusively via `Intl.*` with `es-CL` (decimal comma, CLP formatting, `EEEE d 'de' MMMM`-style dates via `Intl.DateTimeFormat`). Keep keys semantic (`admin.products.soldBy.weight`), not English-sentence keys.

---

## 9. API surface (reconciled with `02-backend.md`)

Reconciled with 02 §3's endpoint tables — 02 owns the contract; letter codes below reference its rows. All under auth except login/refresh; module guards per 01 §7.2 shown in brackets.

```
POST /auth/login                          A1  { tenantSlug, username, password } → { accessToken, refreshToken, user{...}, tenant{...}, branches[] }
POST /auth/refresh                        A2  { refreshToken } → { accessToken, refreshToken }
POST /auth/logout                         A3  { refreshToken } → 204
GET  /tenant                              T1  → tenant profile + enabledModules (refetched on module toggle / 403)
PUT  /tenant/modules                      T2  { enabledModules[] } (MODULES_TOGGLE)
GET/POST/PUT /tenant/users(/{id})         T3/T4  (USERS_MANAGE permission only — no module guard, 01 §4; T4 body may carry password?/pin?)
POST/PUT /tenant/branches(/{id})          T5     (branch CRUD)                    [MULTI_BRANCH_ROLES]
GET/POST/PUT/DELETE /catalog/products(/{id})               C1–C4 (DELETE = soft, active=false)
GET  /catalog/formulas                    C5                                      [INVENTORY_FORMULAS]
PUT  /catalog/products/{id}/formula       C6  { id, lines:[{id,ingredientId,basisPoints}] } (replace-all)  [INVENTORY_FORMULAS]
GET/PUT /catalog/products/{id}/day-old-policy   C7  { mode:"DISCARD"|"DISCOUNT", discountBp? }
GET/POST/PUT /inventory/ingredients(/{id})      I1/I2                             [INVENTORY_FORMULAS]
GET  /inventory/stock?branchId            I3  → per-ingredient Σ movements + low flags  [INVENTORY_FORMULAS]
GET  /inventory/movements?branchId&ingredientId&from&to   I4 (ledger view)        [INVENTORY_FORMULAS]
POST /inventory/movements                 I5  (purchase entry + manual adjustment; append-only)  [INVENTORY_FORMULAS]
GET  /analytics/sales-by-day?branchId?&from&to&groupBy=productType?   K1          [ANALYTICS_DASHBOARD]
GET  /analytics/revenue?granularity=day|week|month&branchId?&from&to  K2          [ANALYTICS_DASHBOARD]
GET  /analytics/product-mix?branchId?&from&to                         K3          [ANALYTICS_DASHBOARD]
GET  /analytics/day-old-loss?branchId?&from&to                        K4          [ANALYTICS_DASHBOARD]
GET  /analytics/stock-levels?branchId?    K5  (I3 shape + negativeStock flags)    [ANALYTICS_DASHBOARD + INVENTORY_FORMULAS]
GET  /analytics/production-summary?branchId?&from&to                  K6          [ANALYTICS_DASHBOARD]
GET  /analytics/payment-methods?branchId?&from&to                     K7          [ANALYTICS_DASHBOARD]
GET  /analytics/forecast-vs-actual?branchId&productType?&from&to      K8          [ANALYTICS_DASHBOARD + DEMAND_FORECASTING]
GET  /forecasts?branchId&from&to          F1  (forecast cache read)               [DEMAND_FORECASTING]
GET  /events/sse?access_token=<jwt>       E1  (EventSource auth = access_token query param, per 02 §2/§5)
```

Revenue-by-branch (the `branches` dashboard page) comes from K1's per-`branchId` rows aggregated presentationally (or K2 called per branch) — no dedicated endpoint. Branch CRUD is 02 T5 (MULTI_BRANCH_ROLES-gated).

KPI endpoints return pre-aggregated buckets (integers: `amountMinor`, `weightGrams`, counts) — aggregation is SQL-side, not client-side.

---

## 10. Testing

- **vitest + Testing Library + jsdom; MSW for all API mocking** (network-level handlers per resource; no fetch-wrapper stubbing — the wrapper's refresh flow gets its own MSW-driven tests: 401 → refresh → retry; refresh 401 → logout).
- **Two riskiest surfaces get component tests (mandated):**
  1. **Formula editor math display** — percent↔bp round-trips (65 ↔ 6500, 0.25% edge), flour locked at 10000 bp, live preview grams match the 01 §5 largest-remainder algorithm exactly (fixture cases shared with `06-baker-mobile.md`'s worked example so web display and KMP ledger provably agree), sum of preview grams === mix weight.
  2. **Sales KPI aggregation rendering** — given MSW-served KPI buckets: correct CLP formatting (no spurious decimals), per-type stacking totals, empty-day gaps rendered (not skipped), weight vs unit product rows labeled kg vs pieces, date-range filter changes the query key.
- Route-guard tests: unauthenticated → `/login` redirect; module-off → route inaccessible + nav item absent.
- `npm test` runs vitest; wire into CI next to Gradle checks (10-azure).

---

## 11. Work items (ordered)

1. **Scaffold** — steps §1 exactly; `index.css` copied; dark toggle working. *AC: `npm run dev` shows a themed page; `.dark` class flips the full palette; no `tailwind.config.js` exists.*
2. **API client + auth** — `http.ts`, token storage, refresh single-flight, `ApiError`; login page. *AC: MSW tests pass for 401→refresh→retry and refresh-failure→logout+redirect; login round-trips against a stubbed backend.*
3. **App shell + guards** — `_authed` layout, sidebar from nav config, entitlement + role filtering, theme toggle, logout. *AC: `beforeLoad` redirects unauthenticated to `/login?redirect=…` and back after login; nav renders only entitled sections.*
4. **Admin: catalog + day-old + tenant profile** — products CRUD (soldBy field swap, minor-units↔decimal), day-old policy table, tenant page. *AC: creating a WEIGHT product stores `pricePerKgMinor` as an integer; CLP shows no decimals; edit→save→list reflects change via invalidation (no manual refetch code).*
5. **Admin: ingredients + stock entry + manual adjustment** — CRUD, purchase-in, signed adjustment, movement ledger view. *AC: adjustment posts an append-only movement (kg entry → grams wire); ledger shows it; works with INVENTORY_FORMULAS **off** except formula-derived rows (cut-line proof).*
6. **Admin: formulas editor** — %↔bp, flour pinned, live gram preview. *AC: test surface (1) in §10 green; editor route absent and API 403 handled gracefully when INVENTORY_FORMULAS off.*
7. **Admin: branches + users + module toggles** — CRUD + switches. *AC: **toggling INVENTORY_FORMULAS off hides the formula editor and stock pages from nav, direct URL redirects, and any in-flight 403 renders the module-disabled state — no blank screens or uncaught errors**; toggling back restores them without reload beyond profile refetch. Same for ANALYTICS_DASHBOARD/DEMAND_FORECASTING.*
8. **Dashboard KPIs** — overview, sales, branches, day-old, stock pages; chart wrappers on `--chart-1..5`. *AC: test surface (2) in §10 green; charts recolor on theme toggle with no re-render code; negative-stock ingredient shows a destructive reconciliation warning with ledger drill-in.*
9. **Forecast page** — chart + cold-start empty state, DEMAND_FORECASTING-gated. *AC: renders 7-day per-type forecast from stubbed `/forecast`; module off → gone per item 7's criteria.*
10. **SSE live updates** — `useServerEvents`, invalidation map, toast, polling fallback. *AC: stubbed batch-ready event refreshes stock/overview queries and toasts; killing the event source degrades to 60 s polling.*
11. **i18n sweep + polish** — all strings via `lib/i18n.ts`, es-CL Intl everywhere; loading skeletons, empty states. *AC: grep for hardcoded UI strings in `features/`/`routes/` comes back clean.*

Items 4–7 (admin) precede 8–10 deliberately — per 00-overview Phase 6, **admin surfaces first: they unblock seeding real config** for every other client's testing.

## Open questions

- **`02-backend.md` reconciliation: done** — §9 now mirrors 02 §3's tables verbatim; SSE auth is decided (access_token query param, 02 §2). Remaining gaps below.
- **Branch CRUD endpoints: RESOLVED** — 02 now has T5 (`POST/PUT /tenant/branches(/{id})`, MULTI_BRANCH_ROLES-gated); the `admin/branches` surface calls it. With the module off the page stays read-only over T1's `branches[]`.
- **Users admin gating: RESOLVED (01 §4, DECIDED)** — user admin is always-on; 02 T3/T4 carry only the `USERS_MANAGE` permission guard, no module guard. `MULTI_BRANCH_ROLES` gates branch structure (T5, branch-scoped assignment, oversight views), never user CRUD.
- **Refresh-token storage:** `localStorage` (simple, XSS-exposed) vs httpOnly cookie — resolved for now: 02's refresh flow is body-carried opaque tokens, no cookie support; localStorage stays.
- **OpenAPI codegen for DTOs:** hand-written types now; worth generating from a backend OpenAPI spec if 02 produces one.
- **Branch-manager dashboard scope:** exact page subset for `BRANCH_MANAGER` (this plan: branch-locked dashboard + stock entry) — confirm against seed roles in `11-seeds-and-demo.md`.
- **Forecast "actuals" overlay source: resolved** — 02 K8 `/analytics/forecast-vs-actual` is the dedicated comparison endpoint; the forecast page uses it instead of re-joining `/analytics/sales-by-day` client-side.
- **Hosting:** static-site build served from where (Container App sidecar, Azure Static Web Apps, or behind the Ktor app)? Owned by `10-azure-deployment.md`; this plan only requires `VITE_API_BASE_URL` to be injectable at build time.
