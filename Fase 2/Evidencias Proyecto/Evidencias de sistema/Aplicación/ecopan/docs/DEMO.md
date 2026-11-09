# Demo guide

One command starts everything on clean data; this page is the order to show it in.

```shell
scripts/demo-up.sh        # ~2 min once images are built, a few minutes the first time
scripts/demo-down.sh      # optional, when you're done: stops everything, deletes nothing
```

`demo-up.sh` always resets to the clean demo state, so there is no need to run `demo-down.sh`
before it — not between rehearsals, not before the demo.

**Run it about 20 minutes before the demo**, not during: it builds images, boots an emulator and
compiles the till. Then do the dry run below once, and don't touch the data again.

## What the script guarantees

| | State after `demo-up.sh` |
|---|---|
| Catalog, users, formulas | **Kept as they are**, including every product photo (Azurite volume + `products.image_url`) and every Almacén product's opening count |
| Sales history | Cleared and re-seeded: ~120 days, weekday-aligned (Saturday is the peak). It ends up to a week before today, so "today" is empty until you sell something live — which is what beat 2 does |
| Batches, ingredient movements, Almacén purchases/adjustments, sessions, queued jobs | **Empty** |
| Opening stock | Insumos: flour 200 kg, water 150 kg, … so the first batch reads as a decrement, not as negative stock. Almacén: **Bebida Cola 500 ml** 5 / 8 units (Principal / Providencia, reorder level 3), **Agua Mineral 500 ml** 3 / 12 (level 4, so *Sucursal Principal* starts on **Stock bajo**), **Galletas Soda (paquete)** 20 / 15 (level 5). Sales are cleared, so every product is back at its opening count |
| Forecast | `DEMAND_FORECASTING` on, forecast pre-computed for both branches |
| Devices | Desktop and Android wiped → each enrols fresh; nothing is signed in |
| Backup | `~/.ecopan-demo/backups/<timestamp>/` — DB dump, photo blobs, old desktop state |

It never runs `docker compose down -v`. **Do not run that yourself before the demo** — it deletes
the product photos.

To undo a reset, restore the dump (command at the top of `scripts/demo-up.sh`).

## Credentials

Panadería (identificador): `reference` (prefilled).

| Where | Usuario / Contraseña | PIN | Rol |
|---|---|---|---|
| Web dashboard | `gerente` / `gerente123` | — | Gerente general (sees both branches) |
| Desktop till | `cajero` / `cajero123` | `1111` | Cajero/a |
| Android baker | `panadero` / `panadero123` | `2222` | Panadero/a |
| Spare | `jefelocal` / `jefelocal123` (PIN `3333`), `admin` / `admin123` (PIN `5555`) | | |

Enrolment is online, once per device; after that staff switch locally by PIN and it works offline.

## Screens to have ready before you start

1. **Web dashboard**, signed in as `gerente`, on *Resumen*. Use a private window so an old session
   from rehearsals can't interfere.
2. **Desktop till**, enrolled as `cajero`, on the sale screen.
3. **Android emulator**, enrolled as `panadero`, on *Hoy*.
4. Arrange them so all three are visible at once: the SSE beat (#4) needs the till and the
   dashboard on screen while you tap on the phone.

Enrolling takes about a minute per device — do it *before* the audience arrives. Keep the
emulator's airplane-mode toggle within reach (quick settings, swipe down twice).

## Run sheet (≈ 17 min)

### 1. The dashboard first — "here is a bakery that has been trading" (2 min)
*Web, `gerente`.*
- The sidebar is grouped by task: **Análisis** (Resumen, Ventas, Pronóstico, Mermas), **Catálogo**
  (Productos, Insumos) and **Organización** (Sucursales, Usuarios, Módulos, Panadería).
- **Análisis → Resumen** — KPIs over 120 days of history. Mention it's seeded demo data. The
  **Stock bajo** card already counts *Agua Mineral 500 ml* in Sucursal Principal.
- **Análisis → Ventas** — the weekly rhythm: Saturday is the peak. Point at the stack by product.
- **Organización → Sucursales** — one page: revenue per branch next to the branch list; this is the
  multi-branch model.
- **Catálogo → Productos** — the catalog with photos, including the ones uploaded by hand. Photos
  are uploaded straight to blob storage from the browser; the API only signs the upload. Note the
  **Tipo** column (Pan, Pastelería, Almacén), which beat 5 comes back to.

*Why first:* the audience sees a populated system before anything is created live.

### 2. Sell at the till — "the counter" (3 min)
*Desktop, `cajero`.*
- Tap **Marraqueta** → a weighed product opens the **manual weight keypad** (there's no physical
  scale; this is the supported production mode) → enter e.g. `1.5` → add.
- Add **Empanada de Pino** (by unit) — or type its barcode `7801111100015` to show the scanner path.
- Bought-in products sit next to the baked ones. **Agua Mineral 500 ml** already carries a
  **Stock bajo** badge on its tile (3 left, reorder level 4); beat 5 makes a second one appear live.
- *Optional, simulated devices:* with the `demo-weights.txt` script in the till's working directory
  (sample: `docs/hardware/demo-weights.reference.txt`) and the **Balanza simulada (demo)** scale,
  tapping Marraqueta plays a scripted ramp that settles on a stable weight instead of the keypad;
  a cash sale on the mock drawer shows **Cajón abierto (simulado)**, and the receipt dialog shows
  the thermal-printer preview. Say out loud that these are simulated drivers, not a validated
  scale, printer or drawer.
- Charge → pick a payment method → receipt.
- Point at the **sync pill** in the status bar: `pendiente` → synced.
- Switch to the web dashboard → **Ventas** shows the sale.

### 3. Offline-first — "the network goes away" (2 min)
*Desktop or Android.*
- Stop the backend: `docker compose stop web` (or airplane mode on the emulator).
- Make another sale. It completes; the pill shows the **queued count**. Nothing is refused.
- Bring it back: `docker compose start web`. The pill drains and the sale appears on the web.
- Message: append-only writes plus an outbox, idempotent on the server, so nothing is lost or
  double-counted.

### 4. Bake a batch → live push (3 min) — *the headline*
*Android, `panadero`, with the till and the dashboard visible.*
- Pick **Marraqueta**, enter a mix weight of `10` kg, confirm twice.
- The batch shows in *Hoy* as `pendiente`, then `sincronizado`.
- **The till shows a "batch ready" banner within a second or two** — pushed over SSE, not polled.
- *Stock* on the phone / **Catálogo → Insumos** on the web (column *Stock actual*): the formula
  decremented the ingredients.
  A 10 kg Marraqueta mix consumes exactly 10 kg, split by the formula:
  `Harina` 5.952 kg, `Agua` 3.869 kg, `Sal` 119 g, `Levadura` 60 g.
- Backstage: API → RabbitMQ → worker (idempotent decrement) → notifier (aggregates) → SSE.

### 5. Catalog and stock — "baked here, or bought and resold" (4 min)
*Web `gerente`, then the till.*
- **Catálogo → Productos** — one **Tipo** per product: *Pan* and *Pastelería* (baked here:
  have a formula, produced in batches) and *Almacén* (bought finished and resold: drinks, packaged
  cookies; no formula, always sold by *Unidad*). Pick the *Sucursal*: the **Stock** column fills in
  for Almacén products only, and *Agua Mineral 500 ml* shows **Stock bajo** in Principal.
- **Nuevo producto** — create an Almacén item live: *Nombre* `Jugo de Naranja 1 L`, *Tipo*
  `Almacén`, *Se vende por* `Unidad`, *Precio unitario* `1800`,
  *Stock inicial (unidades)* `6` in the chosen *Sucursal del stock inicial*, *Nivel de reorden
  (unidades)* `3` → **Crear producto**. The product and its opening count are saved together, and
  a product cannot move between *Almacén* and *Pan* / *Pastelería* afterwards. (It stays in the catalog across resets — rehearse with
  another name, or edit it back out.)
- Row button **Stock** → *Stock de Jugo de Naranja 1 L*: *Stock actual*, *Conteo inicial, compras y
  ajustes*, *Vendido*, *Nivel de reorden* (**Editar nivel**), **Registrar compra** (*Registrar
  ingreso*), **Registrar ajuste**, and the *Movimientos* list whose first row is *Conteo inicial*.
  Stock = opening count + purchases and adjustments − units sold; nothing is ever edited in place.
- **Back at the till** (online, enrolled as `cajero`, Sucursal Principal; give it a sync first so it
  has heard the counts): sell **Bebida Cola 500 ml** — 5 on the shelf, reorder level 3.
  Ring it up and charge, three times: after the third the tile shows **Stock bajo** (2 left); two
  more sales and it reads **Agotado**. Keep going if you like: a sixth still completes and the tile
  turns **Stock negativo**. *The badge is information, never a block.* It already counts the till's
  own unsynced sales; once the sync pill drains, the web **Stock** panel agrees, and **Resumen →
  Stock bajo** moves.
- **Panadería products carry the formula.** On the *Marraqueta* row press **Editar fórmula**
  (*Crear fórmula* if none): quantities are **kg por kilo de harina**, the way the bakers think,
  and *Vista previa* shows the exact ledger arithmetic. There is no separate formulas page.
- **Catálogo → Insumos** — the one page for ingredients: *Stock actual*, *Nivel de reorden* and
  status (*OK*, *Stock bajo*, *Stock negativo*) per ingredient, **Nuevo insumo**, and the row
  **Stock** button for *Ingreso de compra* (record a flour purchase) and *Ajuste manual de stock*.
  Almacén stock is deliberately not here: it lives on the product (**Ir a Productos**).

### 6. Forecast — "what should we bake tomorrow" (2 min)
*Web → **Pronóstico**.*
- The forecast chart for the coming days per product (filter by type or product), and tomorrow's suggested production. Each series says whether it is a Modelo or a Línea base; a baseline is a plain average, not a learned weekend lift.
- Be explicit: **this runs on synthetic demo sales; the evaluation numbers are from a real public
  dataset** (`ml/RESULTS.md`). Every bakery series shows *Línea base*: the model did not transfer to
  unseen, gram-scale series (§4.7), so the baseline serves the bakery until it has its own history. Each row carries which estimator produced it (`modelVersion`), so
  the UI never implies a trained model produced a baseline number.

### 7. Entitlements — "one product, configured per bakery" (1 min)
*Web → **Organización → Módulos**.*
- Turn **Pronóstico de demanda** off → it disappears from the sidebar; the route now answers with
  the "module not enabled" screen. Turn it back on.
- Three enforcement points: server route gate, permission, client navigation.

## If something goes wrong

| Symptom | Fix |
|---|---|
| Device won't enrol / no request in the log | `docker compose logs -f web`. A `401` = wrong field (check *Panadería*, user, password); no line at all = the app can't reach the backend → Android: `adb reverse tcp:8080 tcp:8080 && adb reverse tcp:10000 tcp:10000` |
| Photos missing on the phone | `adb reverse tcp:10000 tcp:10000` — photos are read from Azurite directly |
| Dashboard shows the login page / CORS errors | It must be on **`localhost:5173`** exactly; another port is a different origin |
| No "batch ready" banner | Check the till is enrolled and online; `docker compose logs notifier`. Fallback: the batch still lands in the web dashboard, say so and move on |
| Forecast page empty | *Análisis → Pronóstico* → refresh; if still empty `docker compose logs worker ml` |
| A product tile has no stock badge | The till only knows the counts from its last sync: sync once online. Almacén products only; a level must be set and stock at or under it |
| Anything badly broken | Re-run `scripts/demo-up.sh` — ~3 min, same clean state. Faster: `scripts/demo-up.sh --no-build` |

## Reset between rehearsals

`scripts/demo-up.sh --no-build` gives the same clean state again (and takes a fresh backup each
time): sales are cleared, so every Almacén product is back at its opening count and the till badges
go back to ok. Use `--no-reset` only to restart the processes while keeping what's on screen.

## Known caveats

- The demo uses **Spanish UI**; labels above are what you'll see on screen.
- iOS is not part of this demo (needs Xcode).
- Seeded credentials are development credentials in a committed file — local demo only.
