# ecopan — offline-first bakery POS (KMP + Ktor)

A Kotlin Multiplatform bakery point-of-sale: a **desktop till** (POS, scale/printer/drawer), a
**baker mobile client** (batch logging, stock glance), and a Ktor backend (HTTP API + worker +
notifier, RabbitMQ-backed, with Server-Sent Events push).

The clients are offline-first: every write lands in a local Room database and an outbox, and syncs
when a connection is available. Losing the network degrades freshness, never the ability to sell or
log a batch. See `CLAUDE.md` for the architecture guide and `plans/` for the design documents.

**The UI and all seeded data are Spanish.** English identifiers you see in the code (`CASHIER`,
`CASH`, `PRODUCTION_BATCH`) are wire/storage values and are mapped to Spanish before display.

---

## Run the demo end to end

Everything below assumes a clean checkout. You need **Docker**, **JDK 17**, and — for the mobile
client — an **Android emulator**.

### 1. Start the backend

```shell
cp backend/app/.env.example backend/app/.env
docker compose up -d --build
```

First build takes a few minutes. Check it is up:

```shell
curl -i http://localhost:8080/health     # expect 200
```

This also starts the ML forecasting service on `:8000`. It serves statistical baselines out of the
box; to serve the trained model instead, train it once locally (`cd ml && make data train`, see
[ml/README.md](./ml/README.md)) and restart — `ml/data/artifacts` is bind-mounted into the
container, and `curl http://localhost:8000/health` reports `model_loaded` either way. Forecasts
refresh via `POST /forecasts/refresh` (the worker calls the ML service and caches results in
Postgres) and are read via `GET /forecasts`.

Expect **baseline** forecasts on a fresh stack even with the model loaded, and note that this is
the system working rather than failing: a series only earns the model once it has 28 days of history (the
longest feature lag). The `reference` seed ships 120 days of synthetic sales, so it is the
demo's forecast that shows the trained model; a freshly created branch will not. Rows carry the estimator that
actually produced them in `modelVersion` — `baseline-trailing-mean` here, `lgbm-…` once real sales
accumulate — so the dashboard can say "estimate based on last week" instead of implying a trained
model. `DEMAND_FORECASTING` is off in the `reference` seed; enable it with
`PUT /tenant/modules` before F1/F2 will answer.

The `web` service seeds the demo tenant at boot (`SEED_PROFILE=reference` in `docker-compose.yml`).
Seeding is upsert-by-fixed-UUID, so restarting re-seeds harmlessly and never duplicates rows. To
wipe everything and start over:

```shell
docker compose down -v && docker compose up -d
```

> That deletes the Postgres **and** Azurite volumes, i.e. every uploaded product photo. For the
> demo use `scripts/demo-up.sh` instead (see [docs/DEMO.md](./docs/DEMO.md)): it resets sales,
> batches and stock movements but keeps the catalog, photos and Almacén opening counts.

**If you already had the stack running before pulling:** `docker compose up -d --build` is enough to
pick up a changed seed. A profile is applied create-if-absent so that demo edits survive restarts,
which on its own would mean a committed change to `backend/app/src/main/resources/seed/*.json` only
ever reached fresh databases. Each profile therefore carries a `revision`; when the file's revision
is ahead of what your database has recorded (in `seed_revisions`), the next boot rewrites that
profile's configuration rows once — catalog, users, branches, ingredients, formulas. Sales history
is never rewritten, and the clients pick the change up on their next sync like any other server-side
edit. **If you edit a seed profile, bump its `revision`**, or nobody who already ran the stack will
see your change.

### 2. Sign in

Devices are enrolled **once** with an online login, then stay linked and work offline (staff switch
locally by PIN afterwards — that never touches the network). At the enrollment screen:

| Field | Value |
|---|---|
| **Panadería (identificador)** | `reference` (prefilled) |
| **Usuario** / **Contraseña** | see below |
| **Nombre del dispositivo** | anything, e.g. `Caja 1` |

Seeded users — all belong to **Panadería El Sol**:

| Usuario | Contraseña | PIN | Rol | Sucursal |
|---|---|---|---|---|
| `cajero` | `cajero123` | `1111` | Cajero/a | Sucursal Principal |
| `panadero` | `panadero123` | `2222` | Panadero/a | Sucursal Principal |
| `jefelocal` | `jefelocal123` | `3333` | Jefe/a de sucursal | Sucursal Principal |
| `gerente` | `gerente123` | `4444` | Gerente general | (todas) |
| `admin` | `admin123` | `5555` | Administrador/a | (todas) |

The **PIN** is what you re-enter to unlock after the device is enrolled, and it is what attributes a
sale or a batch to a person. The password is only ever used for that first enrollment.

> These are development credentials in a committed seed file. They are safe only because the demo
> stack is local; do not point a real deployment at this seed profile.

If login fails, watch the backend — every request is logged as one line:

```shell
docker compose logs -f web        # e.g. POST /auth/login -> 401
```

A `401` means the backend rejected the attempt; **no line at all** means the app never reached it
(wrong host, or the stack is down). Note that the backend answers an unknown *bakery identifier*
with the same 401 as a wrong password — deliberately, so it cannot be used to enumerate tenants —
so check all three fields, not just the password.

### 3. Run the desktop till

```shell
./gradlew :jvm:app:run
```

Enroll as `cajero`. You get the POS screen: product grid, cart, payment, and a sync pill in the
status bar. There is no physical scale, so weighed products open a **manual weight** keypad — that
is the supported production mode, not a stub.

### 4. Run the baker client on an Android emulator or phone

```shell
adb reverse tcp:8080 tcp:8080     # once per connected device, see below
adb reverse tcp:10000 tcp:10000   # product photos: the Azurite blob port (compose `storage`)
./gradlew :android:app:installDebug
```

Enroll as `panadero`.

`adb reverse` forwards port 8080 on the device to port 8080 on your machine, over the adb
connection (and 10000 likewise, because product photos are read straight from blob storage --
Azurite locally -- never through the API). That is why every client — desktop, iOS simulator and Android alike — is built pointing
at plain `http://localhost:8080`, and why this works on a **physical phone over USB** as well as on
an emulator. Re-run it whenever you reconnect a device or restart adb; it does not survive either.

> The more familiar `10.0.2.2` (the emulator's alias for your machine's loopback) is deliberately
> not used. It is emulator-only, and on at least one recent emulator image it does not work from
> inside an app at all: the socket sits in `SYN_SENT` until it times out, while `adb shell` and the
> emulator's own browser reach the same host and port.

Backend somewhere other than your machine? Build with `-Pecopan.backendHost=<host>` (or put
`ecopan.backendHost=<host>` in `gradle.properties`). One value, all targets — it is a single
`BuildKonfig` constant generated in `:data:sync:impl`, not a per-platform source edit.

Cleartext HTTP is permitted only in **debug** builds
(`android/app/src/debug/res/xml/network_security_config.xml`). Release builds deny it outright, so
none of this can silently become the shipping posture.

### 5. The end-to-end path worth trying

1. On the **baker** client, pick `Marraqueta`, enter a mix weight (e.g. `10` kg), tap *Registrar lote*. One tap registers it; only a weight outside
   the product's usual range asks for a second tap (the button turns into a confirm), and there is no undo.
2. The batch appears in *Hoy* immediately with a `pendiente` chip, then flips to `sincronizado`.
3. On the **desktop till**, a batch-ready banner appears — pushed over SSE, so within a second or
   two, not on the next sync cycle.
4. Ingredient stock was decremented by the formula. Check *Stock* on the baker client. The mix
   weight is **split proportionally** across the formula's ratios (it is not baker's percentage —
   the parts sum to the mix, they don't multiply it), so a 10 kg Marraqueta mix consumes exactly
   10 kg: `Harina` 5.952 kg, `Agua` 3.869 kg, `Sal` 119 g, `Levadura` 60 g.
   Stock starts at zero and the ledger is append-only, so these read as negative — that is
   deliberate. Going below zero is surfaced for reconciliation, never blocked, because refusing
   the write would mean refusing to let a baker record work they actually did.
5. Now the offline half. Turn on **airplane mode** in the emulator, log another batch — the chip
   stays `pendiente` and the sync pill shows the queued count. Turn it off; everything flushes and
   the desktop catches up. Nothing is lost and nothing is double-counted.

### Seeded catalog

| Producto | Tipo | Venta | Precio | Fórmula |
|---|---|---|---|---|
| Marraqueta | Pan | por peso | $2.500/kg | sí |
| Hallulla | Pan | por peso | $2.300/kg | sí |
| Pan Amasado | Pan | por peso | $2.800/kg | sí |
| Empanada de Pino | Pastelería | por unidad | $1.200 | sí |
| Berlín | Pastelería | por unidad | $900 | sí |

The two unit products carry scannable EAN-13 barcodes (`7801111100015`, `7801111100022`) — POS
accepts keyboard-wedge scanner input on the sale screen.

Three more products have type **Almacén** (bought finished and resold: unit-sold, no formula,
stock tracked per branch from an opening count). The product *Tipo* is a fixed list (Pan,
Pastelería, Almacén) and decides everything else (Almacén is the bought-in kind), so the seed
sets only the type, reorder level and an opening count for every branch:

| Producto | Tipo | Precio | Código de barras | Stock inicial (Principal / Providencia) | Nivel de reorden |
|---|---|---|---|---|---|
| Bebida Cola 500 ml | Almacén | $1.500 | `7802222200015` | 5 / 8 | 3 |
| Agua Mineral 500 ml | Almacén | $800 | `7802222200022` | 3 / 12 | 4 (Principal starts on *Stock bajo*) |
| Galletas Soda (paquete) | Almacén | $900 | `7802222200039` | 20 / 15 | 5 |

The till shows a **Stock bajo** / **Agotado** badge on those tiles; it never blocks a sale.

Ingredients: `Harina`, `Agua`, `Sal`, `Levadura`, `Manteca`, `Azúcar`.

A second tenant (`unipan`) exists to prove the system isn't hardcoded to one bakery's shape —
unit-sold only, three branches, no formulas. Seed both with `SEED_PROFILE=reference,unipan`.

### Known limits of this setup

- **The backend host is a build-time constant, not an in-app setting.** Pointing a build elsewhere
  is `-Pecopan.backendHost=<host>`; there is no settings screen for it.
- **iOS on a physical device** needs `-Pecopan.backendHost=<your LAN IP>`; `adb reverse` has no iOS
  equivalent. The simulator works as-is, because it shares the host's network stack.
- **No HTTPS.** Local development only.
- **Estimated loaf counts are blank.** `mixToLoafRatioMilli` is not part of the catalog sync
  payload yet, so batch entry shows the weight but not a unit estimate.
- **Analytics and the web dashboard** are not part of this walkthrough.

---

## Project layout

* [/composeApp](./composeApp/src) — shared Compose Multiplatform UI and app wiring.
  `commonMain` holds cross-platform code; the sibling folders are per-platform.
* [/android/app](./android/app) — Android entry point (baker client).
* [/jvm/app](./jvm/app) — desktop entry point (POS till). Desktop-only features live under
  `/jvm/feature`.
* [/iosApp](./iosApp/iosApp) — iOS entry point; required even though the UI is shared.
* [/backend](./backend) — Ktor services: `app` (HTTP API + SSE), `worker`, `notifier`, and `data`
  (shared repositories and message-broker clients). A plain multi-module Gradle project, not KMP.
* [/plans](./plans) — the design documents the implementation follows.

## Build commands

```shell
./gradlew :android:app:assembleDebug     # Android APK
./gradlew :jvm:app:run                   # desktop app
./gradlew test                           # JVM/Android unit tests
```

On Windows use `.\gradlew.bat` instead of `./gradlew`.

Note that `./gradlew test` does **not** reach Kotlin Multiplatform test tasks. For a touched KMP
module, also run its `:<module>:jvmTest` (and `:testAndroidHostTest` where Android-specific tests
exist).

For iOS, open [/iosApp](./iosApp) in Xcode and run from there.

## End-to-end tests

`scripts/e2e/run.sh web|till|android|ios|all` brings up the throwaway stack (its own compose project, ports and volumes, never the dev
stack) and drives the dashboard, the desktop till or the baker app through their real UIs. See [docs/e2e](docs/e2e/README.md) and
the story-by-story [coverage table](docs/e2e/COVERAGE.md).

## CI

Every PR runs [`ci.yml`](./.github/workflows/ci.yml): one job per toolchain, no path filters, so
all three checks can be required unconditionally.

| Job | What it runs |
|---|---|
| `gradle` | `./gradlew test jvmTest testAndroidHostTest` + the three `:backend:*:build`s (Testcontainers Postgres included) |
| `web` | `npm run lint`, `npm run build`, `npm test` in `webApp/` |
| `ml` | `make check` (ruff + pytest) in `ml/` via uv |

iOS compilation is deliberately absent — Apple targets need a macOS runner, and the iOS client
ships from a developer machine (see "Known limits").

[`deploy.yml`](./.github/workflows/deploy.yml) is a **manual** (`workflow_dispatch`) run of
`infra/deploy.sh` via OIDC login; its header comment documents the six repo secrets it needs and
why the three stack secrets must be copied from an existing `infra/.env.local` rather than
regenerated.

## Licences

The project itself is under [LICENSE.md](./LICENSE.md).

It also **redistributes fonts**, which carry their own terms. Every bundled family is under the
SIL Open Font License 1.1, and each licence ships beside the files it covers:

* [core/designsystem/fonts-licenses](./core/designsystem/fonts-licenses) — Outfit, IBM Plex Mono, built
  into the Android/iOS/desktop clients.
* [webApp/licenses](./webApp/licenses) — Outfit, IBM Plex Mono, inlined into the
  dashboard's `dist/` bundle at build time.

Adding a font means adding its licence in the same change. Note that Lora carries a Reserved Font
Name, so a modified or subset copy cannot keep the name "Lora".

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
