# Supported devices (plan `04-hardware.md` §6)

The stated posture, not an apology: `:data:hardware` supports **one real driver + one mock driver
(+ manual, for the scale) per interface**, plus a fixed set of matching real devices. A new device
is a new driver/protocol row added at the edge — never a core-contract change.

| Interface | Mock (all platforms) | Manual (all platforms) | Real (JVM only) |
|---|---|---|---|
| `Scale` | `MockScale` — **simulated**: random demo weights, or a per-product script (see below) | `ManualScale` — cashier types the weight; **primary supported mode**, see below | `SerialScale` — serial scales matching `continuous-generic` or `poll-generic` protocol at a configurable baud. **NOT YET validated on physical hardware** — see `docs/hardware/scale-protocol-notes.md` (Phase-0 NO-GO). |
| `ReceiptPrinter` | `MockPrinter` — **simulated**: lays the receipt out through the same ESC/POS builder as `EscPosPrinter` and previews it in the till | n/a | `EscPosPrinter` — ESC/POS printers exposed as an OS print service. Validated on: **escpos-coffee-compatible printers, not yet physically tested** (no printer/print-service available in this environment). |
| `CashDrawer` | `MockDrawer` — **simulated**: opens nothing; the till says "Cajón abierto (simulado)" on a cash sale | n/a | `PrinterKickDrawer` — cash drawers with RJ11 printer-kick (pulses the printer's drawer-kick pin; the drawer has no port of its own). Same hardware-validation caveat as `EscPosPrinter`, since it depends on it. |
| Barcode input | Any HID keyboard-wedge barcode scanner. Not a driver — see below. | | |
| `PaymentTerminal` | `SimulatedTerminal` — on-screen approve/decline/cancel dialog | n/a | **None, ever.** Real terminal integration is out of scope (scope spec §10) — simulated only, permanently. |

## Simulated demo mode (mock drivers)

The mock drivers give the till demo a full weigh → sell → print → drawer flow without hardware. It is
**simulated**: it shows what the software does, and is **not** evidence that a physical scale,
printer or drawer works. Hardware validation stays open (IMP-01 / IMP-09).

- **Weights.** `MockScale` plays a script per product. Format (one product per line, `#` comments):
  `Marraqueta: 310, 640, 880, 842` — every value but the last is an unstable reading, the last is
  the stable one. The weights follow the last colon, so a name may contain colons; names match
  case-insensitively; a repeated name keeps the later line; a leading byte order mark and a trailing
  comma are tolerated; a line that does not parse is skipped and logged with its line number. A
  product with no line keeps the random behaviour. The file is read lazily, off the calling thread,
  the first time a product is weighed. **Reachable today** by putting the file at `demo-weights.txt`
  in the till's working directory and choosing the simulated scale. The `script` config key (a path)
  is implemented in the driver but only reachable from the app once #35's registry passthrough
  lands. A sample for the `reference` seed's by-weight products (Marraqueta, Hallulla, Pan Amasado)
  is `docs/hardware/demo-weights.reference.txt`.
- **Printed receipt.** `EscPosReceipt` is the single place a receipt becomes ESC/POS bytes
  (58 mm = 32 columns, 80 mm = 48; code page 858 for Spanish; ESC @ / ESC t 19 / ESC d / GS V).
  `EscPosPrinter` sends exactly those bytes (`EscPosPrinterTest` pins it) and `MockPrinter` shows the
  same lines in the receipt dialog, labelled as a simulation. The `paperWidthMm` config (default 58)
  is implemented in both printers but only reachable from the app once #35's registry passthrough
  lands. **The receipt format changed with this work and is not hardware-validated:** money prints
  as `$1.234`, each line has a `qty @ unit-price` detail line with the line total flush right
  (the price is never cut; the left side is elided with `..`), long names wrap, the payment method is
  the Spanish label, the full sale id is printed on two lines under `Venta:`, and the raw
  command stream is no longer produced by escpos-coffee's helpers (only the drawer kick still is).
- **Drawer.** A cash sale on `MockDrawer` shows "Cajón abierto (simulado)" inside the receipt dialog.

## Scale — driver detail

- **`ManualScale`** is a first-class, permanently supported deployment mode, not a fallback stub:
  a scale-less till is a fully supported till. It is also the mid-shift fallback if a serial scale
  disconnects (one-tap "switch to manual" in the POS weight pane).
- **`SerialScale`** matches any device that speaks either shipped protocol:
  - `continuous-generic` — device streams a frame continuously (~100 ms cadence typical), ASCII,
    matched against a configurable regex (delimiter, value/unit/stability capture groups, unit→grams
    factor — all config strings, not code).
  - `poll-generic` — same parser, plus a configurable poll command + interval for devices that only
    answer when asked.
  - Devices with no stability flag of their own get a software stability fallback (3 consecutive
    readings within ±2 g over a 500 ms window, all configurable).
  - **Validated on:** nothing yet. No physical serial scale or substitute device was available in
    this development environment. The driver is implemented, API-verified against jSerialComm's
    real method/constant names via context7, and unit-tested against a hand-crafted **synthetic**
    capture log replay — not a real device recording. See `docs/hardware/scale-protocol-notes.md`
    for the full, honest Phase-0 writeup and the go/no-go verdict (NO-GO).

## Barcode scanner — deliberately not a driver

Keyboard-wedge HID barcode scanners type characters + Enter into whatever has focus. There is
nothing to select in Settings → Devices and no `ScannerDriver` in this module — the POS screen's
own focused text field accumulates scanned characters and treats Enter as "code scanned." Manual
code entry uses the exact same input path as a scanner; they are indistinguishable to the app.

## Payment terminal — simulated only, on purpose

`SimulatedTerminal` is not a placeholder for a future real integration — real payment-terminal
integration is explicitly out of scope (scope spec §10). The cashier keys the amount into the real
standalone terminal in parallel with the on-screen simulated dialog; the app records only the
payment method for reconciliation.

## New device = new driver/protocol row

Adding a device that speaks a genuinely different frame format does not require touching `Scale`,
`ReceiptPrinter`, `DriverRegistry`, or any POS code:

- A new scale frame format is usually just a new `hw.scale.serial.protocol` config row
  (`continuous-generic`/`poll-generic` regex + factors), no code at all.
- A device needing bespoke logic gets a new internal `ScaleProtocol` implementation, registered
  next to the existing two.
- A printer/drawer that isn't ESC/POS-over-print-service (e.g. serial-only) would get a new
  `ReceiptPrinter`/`CashDrawer` implementation bound in the JVM `PlatformModule` actual, following
  the same `DriverFactory` pattern as `SerialScaleDriverFactory`/`EscPosPrinterDriverFactory`.

## Configuración → Dispositivos

Each driver picker (`:feature:settings`, shown on the desktop till only) lists only
`DriverRegistry`-available drivers for the running platform (`DriverDescriptor.platformOnly` hides
JVM-only drivers on Android/iOS builds automatically -- nothing platform-specific in the UI layer
itself) alongside a one-line "supported devices" caption per interface mirroring this document.
Unavailable-on-this-platform drivers are simply absent from the list, never shown greyed out.

Two drivers expand extra controls under their picker once selected (no separate panel or screen):

- **Balanza serial** -- *Puerto* (ports detected with jSerialComm's `getCommPorts()`, refresh
  button to re-scan; a saved port that is not currently plugged in stays listed), *Baudios*,
  *Formato de bits* (8N1 / 7E1 / 8E1 / 7O1 / 8N2) and *Protocolo*. A collapsed **Avanzado** block
  exposes the frame description (end-of-frame delimiter, frame regex, unit -> grams factors, stable
  flag values, poll command and interval, software-stability window), prefilled with the defaults
  in `docs/hardware/scale-protocol-notes.md`. This is what makes the driver generic: any ASCII-frame
  scale is a settings row, not a code change. Edits stay in a draft until **Aplicar**, which
  restarts the driver; invalid fields are flagged inline and block Aplicar.
- **Impresora ESC/POS** -- *Impresora del sistema*, a picker of the OS print services
  (`PrinterOutputStream.getListPrintServicesNames()`), or the OS default printer. The cash drawer
  (`printer-kick`) reuses whichever printer is chosen here.

The keys these controls persist (`hw.scale.serial.*`, `hw.printer.name`) are listed in
`docs/hardware/scale-protocol-notes.md`. Mock and manual drivers have no extra controls and are unaffected. Ports are listed once per device: on macOS the `tty.*` twin of a `cu.*` node is hidden.
