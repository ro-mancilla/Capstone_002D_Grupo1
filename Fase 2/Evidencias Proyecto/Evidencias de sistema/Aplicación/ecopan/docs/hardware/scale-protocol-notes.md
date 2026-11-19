# Scale protocol notes — Phase-0 spike (plan `04-hardware.md` §4)

## Verdict

**NO-GO** — no physical serial scale or substitute device was available in this development
environment; the Phase-0 spike harness (`tools/scale-spike/`) is built and ready to run against
real hardware, but has not been exercised against one. Per plan 04 §4's designed no-hardware
fallback, `ManualScale` is confirmed as the primary POS scale mode, and `SerialScale`/`ScaleProtocol`
below are implemented and tested only against a synthetic capture log, not real hardware.

This is the plan's explicitly designed fallback path, not a deviation from it: §4 states up front
that *"NO-GO = client's integrated sticker-printer unit emits nothing readable and no substitute
scale obtained → `ManualScale` becomes the primary POS mode ... and `SerialScale` ships tested only
against replay captures."* That is exactly the situation recorded here.

## What was actually done

1. **Harness built** (`tools/scale-spike/`, work item 1) — a standalone plain-JVM module
   (`org.jetbrains.kotlin.jvm`, jSerialComm only, no Koin/Compose/app) implementing the plan's
   three harness steps:
   - `list`: enumerates `SerialPort.getCommPorts()`, printing `getSystemPortName()` and
     `getDescriptivePortName()` for each.
   - `dump <port> [baud]`: opens the named port (default 9600 baud, 8N1), reads with
     `TIMEOUT_READ_SEMI_BLOCKING`, prints raw hex + ASCII as bytes arrive, and writes a timestamped
     capture log file. If the line stays silent for a few seconds it sends the common poll bytes
     the plan calls out (`W\r`, `P\r`, ENQ `0x05`) and keeps listening.
   - Iterating the frame regex against real captured bytes (step 3) was not reachable without a
     device to capture from.
2. **API verification via context7** — before writing any driver code, `/fazecast/jserialcomm` and
   `/anastaciocintra/escpos-coffee` were queried to confirm exact method/constant names (not
   guessed):
   - `SerialPort.getCommPorts()`, `.systemPortName` / `.descriptivePortName`, `.openPort()`,
     `.setComPortParameters(baud, dataBits, stopBits, parity)`, `.setComPortTimeouts(...)`,
     `.addDataListener(SerialPortDataListener)`, `SerialPort.LISTENING_EVENT_DATA_AVAILABLE`,
     `.writeBytes(buffer, numBytes)`, `.readBytes(buffer, numBytes)`, `.bytesAvailable()`.
   - **Open question #1 resolved**: the port-disconnect listener event constant is
     `SerialPort.LISTENING_EVENT_PORT_DISCONNECTED` (confirmed present in the library's own
     disconnection-handling example). `SerialScale` listens for it and also runs an independent
     stale-frame watchdog, per the plan's "if unreliable on a platform, the watchdog is the sole
     DISCONNECTED trigger" fallback — the watchdog was kept as the sole *verified-in-this-environment*
     trigger, since the disconnect event itself could not be tested against a real unplug event here.
   - `PrinterOutputStream.getPrintServiceByName(name)` / `.getDefaultPrintService()`,
     `new EscPos(PrinterOutputStream)`, `escpos.writeLF(...)`, `escpos.feed(n)`,
     `escpos.cut(EscPos.CutMode.PART)`, `escpos.pulsePin(EscPos.PinConnector.Pin_2, onMs, offMs)`
     (the standard ESC `p` drawer-kick pulse) — all confirmed against the library's README/wiki
     snippets before use.
3. **`ScaleProtocol` implemented and unit-tested against a synthetic capture log** —
   `ContinuousGenericProtocol` / `PollGenericProtocol`
   (`data/hardware/impl/src/jvmMain/.../jvm/ScaleProtocol.kt`), config-driven (delimiter, frame
   regex, unit→grams factors, stability-flag values, poll command/interval — all config strings,
   not hardcoded per brand), with the K/T/W software-stability fallback (default 3 readings / ±2 g
   / 500 ms window) for devices whose frames carry no stability flag of their own.
4. **Synthetic capture log** —
   `data/hardware/impl/src/jvmTest/resources/scale-captures/synthetic-continuous-generic.log`.
   Explicitly labeled SYNTHETIC in its own header: hand-crafted, timestamped hex+ASCII lines
   mimicking the plan's own example frame format (`ST,GS, 1.234 kg\r\n`) at a ~100 ms cadence,
   ramping unstable then settling stable at 842 g. It is **not** a real device recording.
5. **Replay tests green against the synthetic log** (`ScaleProtocolReplayTest`, jvmTest): the log
   replays to the expected grams/stability sequence both chunked at recorded frame boundaries and
   re-chunked at 1-byte granularity (proving framing is split-safe), plus a garbage-input test
   (random bytes → zero readings, no throw).

## Device identity, port params, frame format — as far as they exist

There is no real device identity, port, or baud to record — none was available. The
`(port, baud, framing, protocol-id, regex, unit factor)` tuple the plan's go/no-go gate calls for
was never produced, because it requires a device to iterate against (harness step 3). What exists
instead is a **plausible default configuration** matching the plan's own worked example, used only
to validate the parser's mechanics:

| Field | Value used (synthetic only) |
|---|---|
| Protocol id | `continuous-generic` |
| Delimiter | `\r\n` |
| Frame regex | `^(?<stable>ST\|US),[A-Z]{2},\s*(?<value>[+-]?[0-9]+(?:\.[0-9]+)?)\s*(?<unit>kg\|g)$` |
| Stability flag | `ST` = stable, `US` = unstable (device-reported, not the software K/T/W fallback) |
| Unit factor | `kg` → 1000 g, `g` → 1 g |
| Poll vs. stream | Continuous stream (no poll command) — `poll-generic` exists and is covered by the same parser, but has no captured data of its own either |

## Consequences (per plan §4/§8, already the designed fallback)

- **`ManualScale` is the primary supported POS scale mode.** It was already first-class, not a
  stub, so nothing downstream changes — this simply confirms the plan's default rather than
  overriding it.
- **`SerialScale` ships** (04 work item 6) — the code exists, compiles, and is unit-tested against
  the synthetic replay log — but carries the same honesty caveat as this whole document: no real
  serial scale has confirmed it works end-to-end.
- **`EscPosPrinter`/`PrinterKickDrawer` ship the same way** — API-verified via context7, unit-built,
  but not validated against a physical printer either (no OS print service / physical ESC/POS
  printer was available in this environment). See `docs/hardware/supported-devices.md`.
- The live demo (plan §5) runs entirely on mock/manual drivers, which was always the stated,
  non-hidden plan regardless of Phase-0's outcome.

## Evidence assets (plan §5, §9)

No screen recording exists yet — the checklist in plan §5 requires a physical scale in hand, which
this environment did not have. Recording remains a follow-up task for whoever runs
`tools/scale-spike/` against real hardware; link it here when captured.

## Re-running the harness against real hardware

```
./gradlew :tools:scale-spike:run --args="list"
./gradlew :tools:scale-spike:run --args="dump <systemPortName> 9600 8N1"
```

Iterate baud (9600, 4800, 19200, 38400, 115200) and framing (`8N1`, then `7E1`; `8E1`, `7O1` and
`8N2` are also accepted) per the plan's order -- `dump` takes the framing as its fourth argument and
defaults to `8N1`. Once a readable frame format is found, **no code change is needed**: enter the
tuple in the till under *Configuración* -> *Dispositivos* -> *Balanza*, choosing *Balanza serial* (see
`docs/hardware/supported-devices.md` for the field-by-field mapping), and replace this document's
verdict with a real GO, including the actual `(port, baud, framing, protocol-id, regex, unit
factor)` tuple and a real capture log.

## Where the settings live (`hw.scale.serial.*`)

The serial scale's configuration is persisted in the same DataStore as the driver choice, one key per
setting, and handed to `SerialScale` through `DriverRegistry` (`DriverChoice.config`). Applying a
change from Settings restarts the driver with the new values.

A few driver-level keys are read from the same config but have **no Settings control**: `rawlog`
(`true` appends raw hex to `scale-raw.log`, for capturing a device) on the scale, and `drawerPin`,
`drawerOnMs` and `drawerOffMs` (drawer-kick pin and pulse timing, defaults `2` / `50` / `100`) on the
printer. They are not persisted by the registry today, so they take effect only when passed in a
`DriverChoice.config` by code.

| Key (`hw.scale.serial.<key>`) | Settings field | Default |
|---|---|---|
| `port` | Puerto (picker of `SerialPort.getCommPorts()`) | none -- required |
| `baud` | Baudios | `9600` |
| `framing` | Formato de bits (`8N1`, `7E1`, `8E1`, `7O1`, `8N2`) | `8N1` |
| `protocol` | Protocolo (`continuous-generic` / `poll-generic`) | `continuous-generic` |
| `delimiter` | Avanzado -> Fin de trama | `\r\n` |
| `frameRegex` | Avanzado -> Expresión regular (needs `(?<value>)` and `(?<unit>)`; optional `(?<stable>)`) | `^(?<stable>ST\|US),[A-Z]{2},\s*(?<value>...)\s*(?<unit>kg\|g)$` |
| `stableValues` | Avanzado -> Valores de peso estable | `ST` |
| `unitFactors` | Avanzado -> Unidad a gramos (`unit=factor,...`) | `kg=1000,g=1` |
| `pollCommand` | Avanzado -> Comando de consulta (poll protocol only) | `W\r` |
| `pollIntervalMs` | Avanzado -> Intervalo de consulta (poll protocol only) | `500` |
| `stabilityCount` / `stabilityToleranceGrams` / `stabilityWindowMs` | Avanzado -> software stability | `3` / `2` / `500` |

`delimiter` and `pollCommand` are stored in their typed, escaped form: `\r`, `\n`, `\t`, `\\` and
`\xHH` (exactly two hex digits, e.g. `\x05` for ENQ) are turned into the real bytes by the driver.

Every field in the table above is checked when it is edited, and a failure is shown beside the
field while **Aplicar** stays disabled (`SerialScaleSettings.validate()`):

- the port must be chosen, and the baud must be 50..4,000,000;
- the frame regex must compile, must declare real `(?<value>)` and `(?<unit>)` named groups, and must
  finish within a 50 ms budget on a realistic frame and on a few hostile samples (nested
  quantifiers are rejected as too slow); `stableValues` is required only if the regex declares
  `(?<stable>)`;
- `unitFactors` must be `unit=factor` pairs with factors above zero;
- numbers must be integers in range (stability count 1..50, tolerance 0..1000 g, window 10..10000 ms;
  poll interval 50..60000 ms, poll command non-empty, both only for `poll-generic`).

Not checked: that the regex's `unit` capture actually names a unit listed in `unitFactors` (a frame
whose unit is unknown is simply dropped at read time). Because the regex is user-typed and runs on
the serial event thread, the driver also time-boxes every match, caps the receive buffer at 4,096
characters when no delimiter ever arrives, and survives an exception from the protocol; a bad value
that gets in anyway (hand-edited prefs) leaves the scale in `ERROR` rather than crashing the
registry. After an unplug the driver closes the dead handle and goes back to opening the port.

The ESC/POS printer has one Settings-visible key, `hw.printer.name` (the OS print-service name chosen
in *Impresora del sistema*; unset means the OS default printer). The drawer kick goes through the
same printer, so it needs no key of its own.

Two further keys are forwarded untouched to the **mock** drivers for the demo: `hw.scale.script`
(`script`) and `hw.printer.paperWidthMm` (`paperWidthMm`). `DriverRegistry` only stores and passes
them through; their meaning belongs to `MockScale` / `MockPrinter`.
