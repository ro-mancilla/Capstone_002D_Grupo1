# POS Desktop UI Review — 2026-09-06

Captured from the running desktop till (`:jvm:app:hotRun`) via the `compose-hot-reload` MCP
server, against a live local backend with seed data. Screenshots in `screenshots/desktop-pos/`, referenced
below by filename. Scope: `jvm/app` + `jvm/feature/pos` (Caja) and `feature/settings`
(Configuración) only — mobile/baker client not covered by this pass.

## Bugs

1. **"Ingreso manual" chip is dead** — `01-pos-caja.png`. Styled identically to the other
   clickable pills in the toolbar (rounded chip, same weight), but exposes no `onClick` in the
   Compose semantic tree. Users will tap it expecting a manual-weight entry dialog and nothing
   will happen.
   - Where: `jvm/feature/pos/impl/.../PosScreen.kt` (toolbar row).

2. **Pluralization bug in cart header** — `02-pos-cart-with-item.png`. Shows "1 líneas" with a
   single line item; should be "1 línea". Likely a hardcoded plural string in
   `strings.xml`/format call instead of a quantity-aware string resource.

3. **Payment method selector uses checkbox semantics for a single-select group** —
   `07-cobro-payment-methods.png`. EFECTIVO/TARJETA/TRANSFERENCIA/OTRO each report
   `role: Checkbox` in the accessibility tree even though selecting one deselects the others.
   Should be `Role.RadioButton` (or a `selectableGroup()`) — affects screen reader / a11y
   semantics and is misleading to any test or assistive tech relying on `role`.

4. **Inconsistent "Cerrar" button styling across dialogs** — compare `03-ver-caja-dialog.png`
   (solid filled orange pill) vs. `06-acerca-de-dialog.png` (plain text, no fill). Same action,
   two different visual treatments in dialogs that otherwise share a design language.

5. **"Caja del día" dialog doesn't size to content** — `10-caja-del-dia-with-sale.png`. With a
   single sale logged, the dialog still reserves a large fixed height, leaving a big empty gap
   below the one row. Should shrink-wrap content (or switch to a scrollable list only once
   content exceeds a max height).

6. **Boleta (receipt) is missing transaction detail** — `09-boleta-receipt.png`. Only shows line
   items + total. No timestamp, payment method, cash received, or vuelto (change) — data that
   was already collected earlier in the same flow (`08-cobro-cash-amount.png` shows vuelto
   computed correctly, but it's discarded before the receipt renders).

## Design observations (not bugs, but worth a design pass)

- **Mixed typefaces read as unfinished.** Configuración pairs a serif display font (page title,
  dropdown selected values, device row titles, "Acerca de Ecopan") with a monospace font
  (section labels, helper/compatibility text) — see `04-configuracion-top.png` /
  `05-configuracion-bottom.png`. If this is an intentional two-typeface system it needs a
  clearer rule for which font each text role gets; today it looks accidental.
- **Large unused whitespace.** The product grid (`01-pos-caja.png`) and the empty cart state
  both leave significant dead space at typical desktop window widths — the grid could use more
  columns or larger cards, and the cart panel's empty state is very sparse.
- **Settings cards don't use the available width** — content maxes out around 930px inside a
  much wider window, leaving a large empty right gutter (`04-configuracion-top.png`). May be
  intentional for readability, but at a 1280px+ desktop width it feels unbalanced.
- **Sync status badge language is confusing.** Idle state reads "Requiere atención" (⚠, red) —
  alarming for what appears to be a normal idle/not-yet-synced state — then correctly flips to
  "Sincronizando…" once a real sale is made (`13-pago-confirmado` equivalent capture). Worth
  confirming whether the idle red/attention state is truly an error condition or just "nothing
  synced yet," and toning down the visual severity if it's the latter.

## Repro notes

- Backend: `docker compose up -d --build` (db/rabbitmq/ml/worker×2/notifier/web all healthy).
- App: `./gradlew :jvm:app:hotRun`, driven via `compose-hot-reload` MCP
  (`status` → `list_windows` → `get_semantic_tree` → `click`/`take_screenshot`).
- Full checkout flow exercised end-to-end: add item → COBRAR → EFECTIVO → quick amount →
  Confirmar pago → Boleta → Nueva venta → Ver caja (confirms the sale landed in "Caja del día").
