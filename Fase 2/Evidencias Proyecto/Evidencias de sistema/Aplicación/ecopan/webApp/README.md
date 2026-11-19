# ecopan — panel de gestión (web)

The manager dashboard: a React + TypeScript + Vite client for the same Ktor backend the desktop
till and the baker mobile app use. Read-heavy — sales/stock analytics plus the admin CRUD
for catalog, formulas, users, branches and modules.

**The UI is Spanish-only** (`src/lib/i18n.ts`, `es-CL`), matching the KMP clients. English
identifiers in the code (`CASHIER`, `BRANCH_MANAGER`) are wire values and are mapped before display.

Unlike the KMP clients this app is **online-only** — there is no offline cache and no outbox. If the
backend is down, the dashboard is down.

---

## Run it

### 1. Start the backend first

The dashboard has no mock mode in dev; it talks to the real API. From the repo root:

```shell
cp backend/app/.env.example backend/app/.env
docker compose up -d --build
curl -i http://localhost:8080/health     # expect 200
```

That seeds the demo tenant (`SEED_PROFILE=reference`). See the root [README](../README.md) for the
full stack walkthrough.

### 2. Start the dashboard

```shell
cd webApp
cp .env.example .env
npm install
npm run dev
```

Vite serves on <http://localhost:5173> by default. `.env` holds one variable:

```shell
VITE_API_BASE_URL=http://localhost:8080
```

Other scripts: `npm run build` (typecheck + bundle), `npm test` (Vitest, MSW-mocked — needs no
backend), `npm run lint` (Oxlint).

`.env` is optional in dev — with none, the client falls back to `http://localhost:8080`. A
production build has no default and fails if `VITE_API_BASE_URL` is unset.

### If the browser blocks requests (CORS)

The dashboard and the API are different origins, so the backend has to name this one explicitly.
It allows `http://localhost:5173` and `http://localhost:4173` by default, which covers `npm run dev`
and `npm run preview`.

A **port** is part of an origin: if Vite reports `Local: http://localhost:5174` because 5173 was
taken, that origin is *not* allowed and every call fails preflight. Either free 5173, or add the
port to `CORS_ALLOWED_ORIGINS` in `backend/app/.env` and restart the backend:

```shell
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174
```

A blocked request shows as a CORS error in the browser console and, on the backend, as a `403`.

---

## Sign in

The login form asks for three things — **panadería (identificador)**, **usuario**, **contraseña**.
The tenant slug for the seeded demo is `reference`.

### Who can actually get in

**This dashboard is manager-only.** `CASHIER` and `BAKER` authenticate successfully and are then
rejected client-side with a "no tienes acceso" card, because the dashboard is not their tool — they
use the desktop till and the baker mobile app. The backend also 403s manager-only routes; the
client-side check just avoids showing them a confusing empty shell first
(`MANAGER_APP_ROLES` in `src/api/types.ts`).

| Usuario | Contraseña | Rol | Sucursal | Dashboard? |
|---|---|---|---|---|
| `jefelocal` | `jefelocal123` | Jefe/a de sucursal (`BRANCH_MANAGER`) | Sucursal Principal | ✅ sí |
| `gerente` | `gerente123` | Gerente general (`GENERAL_MANAGER`) | (todas) | ✅ sí |
| `admin` | `admin123` | Administrador/a (`ADMIN`) | (todas) | ✅ sí |
| `cajero` | `cajero123` | Cajero/a (`CASHIER`) | Sucursal Principal | ❌ rechazado |
| `panadero` | `panadero123` | Panadero/a (`BAKER`) | Sucursal Principal | ❌ rechazado |

**Start with `gerente`** — `GENERAL_MANAGER` sees every branch, so the branch comparison views have
something to compare. `jefelocal` is scoped to Sucursal Principal, which is the right account for
checking that branch scoping actually works.

The PIN column in the root README does not apply here: PINs are the on-device staff lock for the
till and the baker app. The web app uses password login and a refresh-token session in
`localStorage`.

> Development credentials from a committed seed file (`backend/app/src/main/resources/seed/reference.json`).
> Safe only because the demo stack is local — never point a real deployment at this seed profile.

---

## What data is there

Seeded tenant **Panadería El Sol** (`reference`), currency CLP, with branches **Sucursal Principal**
and **Sucursal Providencia**.

| Producto | Tipo | Venta | Precio | Fórmula |
|---|---|---|---|---|
| Marraqueta | Pan | por peso | $2.500/kg | sí |
| Hallulla | Pan | por peso | $2.300/kg | sí |
| Pan Amasado | Pan | por peso | $2.800/kg | no |
| Empanada de Pino | Pastelería | por unidad | $1.200 | no |
| Berlín | Pastelería | por unidad | $900 | no |

Ingredients: `Harina`, `Agua`, `Sal`, `Levadura`, `Manteca`, `Azúcar`. The two unit products
carry EAN-13 barcodes.

### The seed is configuration only — there are zero transactions

This trips people up, so it is worth stating plainly. The seed creates catalog, staff and branches.
It creates **no sales, no production batches, and no stock movements**. Verified against the seeded
database:

```
sales = 0        production_batches = 0        stock_movements = 0
products = 5     ingredients = 6               formulas = 2
users = 5        branches = 2
```

So on a freshly seeded stack the **admin** screens are fully populated, while the **dashboard**
analytics (ventas, stock) render as empty states rather than charts. That is
correct behaviour, not a bug.

To get numbers into the dashboard, generate real activity with the other clients:

- **Sales** — run the desktop till (`./gradlew :jvm:app:run`), enroll as `cajero`, ring up a sale.
- **Production batches + stock movements** — run the baker client
  (`./gradlew :android:app:installDebug`), enroll as `panadero`, log a batch. The formula decrements
  ingredient stock, which is what makes the stock views non-empty.

Stock starts at zero and the ledger is append-only, so consumption reads as **negative**. That is
deliberate: going below zero is surfaced for reconciliation, never blocked.

---

## Known limits of this setup

- **Pronóstico (forecast) is unreachable.** The route is gated on the `DEMAND_FORECASTING`
  entitlement, which the `reference` tenant does not enable. The ML pipeline behind it is not built
  yet.
- **No offline support.** Online-only by design; a dropped backend means a dead dashboard.
- **No HTTPS**, and the API base URL is a build-time env var with no in-app setting.
- A second tenant (`unipan`) exists to prove nothing is hardcoded to one bakery's shape — unit-sold
  only, three branches, no formulas. Seed both with `SEED_PROFILE=reference,unipan`.

## Layout

```
src/
  api/          typed backend client (types.ts mirrors the Kotlin DTOs) + React Query hooks
  features/     auth, dashboard, admin-{org,catalog,formulas,inventory,modules}
  routes/       TanStack Router file routes; _authed/ is the guarded subtree
  lib/          i18n (all UI strings), route guards, formatting
  components/   ui primitives, layout, charts, shared widgets
  mocks/        MSW handlers — used by the test suite, not by dev
```
