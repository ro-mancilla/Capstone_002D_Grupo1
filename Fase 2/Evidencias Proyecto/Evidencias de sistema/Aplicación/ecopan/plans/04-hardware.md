# 04 — Hardware Abstraction (`:data:hardware`)

Implements the hardware posture of `spec/SPECS.md` (Hardware Abstraction) and `spec/bakery_system_scope_spec.md` §6 exactly: **interfaces + one real driver + one mock driver per interface; mock/manual are first-class supported modes; a stated supported-devices list; no arbitrary-peripheral support.** Binding constraints from `01-architecture-decisions.md`: §1 (serial drivers exist only on JVM — platform gates hardware affordances, role gates navigation), §5 (scale drivers report integer **grams**), §9 (jSerialComm for JVM serial).

Zero production code here — contracts are illustrative. Implementation agents verify every API via context7 (`/fazecast/jserialcomm`, `/anastaciocintra/escpos-coffee`) before writing code.

---

## 1. Module design

### 1.1 `:data:hardware:api` — pure commonMain contracts

- Plugins: `f776.kotlinMultiplatform` + `f776.androidKmpLibrary` (mock/manual drivers run on Android/iOS, so the api must target Android; no Compose, no serialization needed beyond plain models).
- Package: interfaces under `com.f776.ecopan.domain.hardware` (skill rule: repository-ish interfaces live in `domain.<name>`).
- Zero platform code, zero driver code. Depends only on coroutines + kotlinx-datetime.

Illustrative contracts (not final code):

```kotlin
enum class DeviceStatus { CONNECTED, DISCONNECTED, ERROR }

data class WeightReading(val grams: Long, val stable: Boolean, val at: Instant)

interface Scale : AutoCloseable {
    /** Hot stream; emits readings while active. POS consumes only stable==true for sale lines. */
    val readings: Flow<WeightReading>
    val status: StateFlow<DeviceStatus>
    fun start()          // idempotent; opens port / starts script / arms manual input
    override fun close()
}

/** Structured receipt — POS never formats ESC/POS itself. Money in minor units (01 §5). */
data class ReceiptDoc(
    val header: List<String>,            // tenant name, branch, address — from tenant config
    val lines: List<ReceiptLine>,        // name, qty-or-grams, unit price, line total (minor)
    val totalMinor: Long,
    val paymentMethod: String,
    val footer: List<String>,
    val saleId: Uuid,                    // printed as barcode/plain ref
)

interface ReceiptPrinter {
    val status: StateFlow<DeviceStatus>
    suspend fun print(doc: ReceiptDoc): PrintResult   // Ok | Failed(reason)
    suspend fun kickDrawer()                          // drawer usually hangs off the printer (RJ11)
}

interface CashDrawer { suspend fun open(); val status: StateFlow<DeviceStatus> }

/** Simulated only — terminal integration is out of scope (scope spec §10). */
interface PaymentTerminal {
    suspend fun request(amountMinor: Long): PaymentResult
    // PaymentResult = Approved(method: PaymentMethod) | Declined(reason) | Cancelled
}
```

**`BarcodeScanner` is deliberately NOT an interface/driver.** Keyboard-wedge HID scanners *type characters + Enter* into whatever has focus. Driver work is zero; the correct home is the POS screen (`05-pos-desktop.md`): a focused, possibly invisible text field (or window-level key-event capture on desktop) that accumulates characters and treats Enter as "code scanned" → catalog lookup. `:data:hardware` ships only a doc note stating this so nobody builds a `ScannerDriver`. Manual code entry uses the same text path — scanner and keyboard are literally the same input.

### 1.2 Driver selection: `DeviceProfile` + `DriverRegistry`

Also in `api`:

```kotlin
data class DriverChoice(val driverId: String, val config: Map<String, String>) // e.g. port, baud
data class DeviceProfile(
    val scale: DriverChoice, val printer: DriverChoice,
    val drawer: DriverChoice, val terminal: DriverChoice,
)

interface DriverRegistry {
    val profile: Flow<DeviceProfile>                     // DataStore-backed
    val availableScaleDrivers: List<DriverDescriptor>    // (id, displayName, platformOnly) — feeds settings UI
    // same for printer/drawer/terminal
    val scale: Flow<Scale>          // emits a NEW driver instance whenever profile.scale changes
    val printer: Flow<ReceiptPrinter>
    val drawer: Flow<CashDrawer>
    val terminal: Flow<PaymentTerminal>
    suspend fun select(iface: DeviceInterface, choice: DriverChoice)   // writes DataStore
}
```

**Live-swap mechanics (the demo requirement — "one config line", no restart):**

- `DriverRegistryImpl` (`:data:hardware:impl`, `internal`, package `com.f776.ecopan.data.hardware`) reads the profile from Preferences DataStore (`:core:datastore` pattern, incl. the mandatory `catch { if (it is IOException) emit(emptyPreferences()) }`).
- `scale = profile.map { it.scale }.distinctUntilChanged().map { factoryFor(it).create(it.config) }` with previous instance `close()`d on each new emission (`onEach`/`scan` pair or `flatMapLatest` over a `callbackFlow` owning the driver lifecycle). Shared via `shareIn(appScope, SharingStarted.WhileSubscribed, replay = 1)` — scope/dispatcher **injected** (`named("ioCoroutineScope")`, `named("ioDispatcher")`), never hardcoded.
- POS device layer (in `:feature:pos:impl`) collects `registry.scale` and `flatMapLatest { it.readings }` — changing the setting in the Settings screen is therefore *the* swap: DataStore write → profile emission → new driver instance → POS weight pane live on the new source. Cheap, so runtime swap without restart is in scope, not stretch.
- Driver factories are Koin bindings: `internal interface ScaleDriverFactory { val descriptor: DriverDescriptor; fun create(config): Scale }`. Common module binds Mock + Manual factories; the **JVM `PlatformModule` actual** additionally binds Serial/EscPos factories; Android/iOS actuals bind nothing extra. Registry collects them via `getAll<ScaleDriverFactory>()`. Unknown/unavailable driverId (e.g. profile says `serial` on Android after a synced setting) → registry falls back to `manual` and surfaces a warning — never crashes.

### 1.3 `:data:hardware:impl`

- Plugins: `f776.kotlinMultiplatform` + `f776.androidKmpLibrary`. No Compose. Deps: `api(projects.data.hardware.api)`, `implementation(projects.core.datastore)`, koin, coroutines; **jvmMain only**: jSerialComm, escpos-coffee.
- Public surface: exactly one symbol — `val HardwareDataModule` (`di/HardwareDataModule.kt`, `includes(PlatformModule, DatastoreModule)`); everything else `internal`. `internal expect val PlatformModule: Module` with `androidMain`/`iosMain`/`jvmMain` actuals, exactly like `data/reminder/impl/.../di/ReminderDataModule.kt`.
- Source-set layout mirrors `:data:reminder:impl`: drivers common where possible, serial/ESC-POS under `jvmMain/kotlin/.../jvm/`.
- Wire into `composeApp/.../di/AppModule.kt` `includes(...)`.

### 1.4 DataStore keys (Preferences DataStore, `:core:datastore`)

| Key | Values | Default |
|---|---|---|
| `hw.scale.driver` | `mock` \| `manual` \| `serial` (JVM only) | `manual` |
| `hw.scale.serial.port` | system port name (e.g. `ttyUSB0`, `COM3`) | unset |
| `hw.scale.serial.baud` | int | `9600` |
| `hw.scale.serial.protocol` | protocol id (see §3) | `continuous-generic` |
| `hw.scale.serial.rawlog` | bool — raw byte capture mode | `false` |
| `hw.printer.driver` | `mock` \| `escpos` (JVM only) | `mock` |
| `hw.printer.name` | OS print-service name | unset |
| `hw.drawer.driver` | `mock` \| `printer-kick` (JVM only) | `mock` |
| `hw.terminal.driver` | `mock` | `mock` |

---

## 2. Drivers matrix

| Interface | Mock (all platforms) | Manual (all platforms) | Real (JVM only) |
|---|---|---|---|
| `Scale` | `MockScale` — scripted/random weights | `ManualScale` — cashier types weight | `SerialScale` (jSerialComm) |
| `ReceiptPrinter` | `MockPrinter` — on-screen/log receipt | — (n/a) | `EscPosPrinter` (escpos-coffee) |
| `CashDrawer` | `MockDrawer` — no-op + event log | — | `PrinterKickDrawer` — delegates to printer pulse |
| `PaymentTerminal` | `SimulatedTerminal` — approve/decline dialog flow | — | **none, ever** (out of scope §10) |

Android/iOS `PlatformModule` actuals register **only** mock (+manual for scale) — per 01 §1 platform gating. Settings UI on mobile therefore simply never lists serial/escpos.

Behavior contracts + failure modes:

- **`MockScale`** — for dev/demo. Two modes via config: `random` (plausible bakery weights 80–2500 g, ramp-then-settle: 3–4 unstable readings converging, then `stable=true`) and `scripted` (fixed sequence from config, deterministic for the stage demo and for screenshots). Status: always `CONNECTED`. Emits on injected default dispatcher; never touches wall-clock scheduling beyond `delay`.
- **`ManualScale`** — a *real supported mode* (un-integrated devices) **and** the mid-shift fallback, not a stub. `start()` → `CONNECTED` immediately. Exposes `fun submitWeight(grams: Long)` (internal extension surface consumed by the POS weight-entry field when the active driver is manual); each submit emits one `WeightReading(stable = true)`. Validation lives here (single home): rejects ≤ 0, rejects > `MAX_MANUAL_GRAMS` (50_000 default, config), input UI takes kg with up to 3 decimals → integer grams (no floats stored, 01 §5). Never enters `ERROR`.
- **`SerialScale`** — jSerialComm (verified via context7): enumerate `SerialPort.getCommPorts()` and match `getSystemPortName()` against `hw.scale.serial.port`; `openPort()`; `setComPortParameters(baud, dataBits, stopBits, parity)`; read via `addDataListener(SerialPortDataListener)` on `LISTENING_EVENT_DATA_AVAILABLE` (poll-mode protocols instead use `TIMEOUT_READ_SEMI_BLOCKING` + `writeBytes(pollCmd)` loop). Bytes go into the `ScaleProtocol` parser (§3); parsed frames → `WeightReading` in grams. Failure modes:
  - Port absent / `openPort()` fails → `status = ERROR`, retry with backoff; never throws to the collector.
  - **Unplugged mid-shift** → detected by jSerialComm disconnect event (`LISTENING_EVENT_PORT_DISCONNECTED` — verify exact constant via context7) *plus* a stale-data watchdog (no valid frame for N s on a continuous protocol) → `status = DISCONNECTED`. POS reaction (contract for `05`): weight pane shows disconnected banner + one-tap **"Switch to manual weight"** → `registry.select(SCALE, manual)` → cashier keeps selling. Reconnect is a swap back — same one-line mechanism as the demo.
  - Garbage frames (wrong baud/protocol) → parser yields nothing → watchdog → `ERROR("no parseable frames")`; settings screen shows last raw bytes hex to aid config.
- **`MockPrinter`** — renders `ReceiptDoc` to a plain-text 32-col layout, exposes it as a `Flow<String>` the POS shows in an on-screen "receipt" panel (this *is* the demo printer); `kickDrawer()` logs + emits event. Always `CONNECTED`.
- **`EscPosPrinter`** — escpos-coffee (verified): `PrinterOutputStream.getPrintServiceByName(hw.printer.name)` → `EscPos(PrinterOutputStream)`; layout `ReceiptDoc` with `Style`/`writeLF`, `cut(CutMode.PART)`; `kickDrawer()` = `escpos.pulsePin(EscPos.PinConnector.Pin_2, 50, 100)` (the standard ESC `p` drawer-kick escape; pin/pulse durations config-able because drawers vary). Print-service missing / IOException → `PrintResult.Failed` + `status = ERROR`; POS offers reprint-later, sale is **never** blocked on printing.
- **`PrinterKickDrawer`** — the physical drawer has no port of its own; it kicks off the printer (spec §6.1). `open()` delegates to the active `ReceiptPrinter.kickDrawer()`; status mirrors printer status. `MockDrawer` on non-JVM.
- **`SimulatedTerminal`** — `request(amountMinor)` suspends until the POS-rendered simulated-terminal dialog resolves (approve → `Approved(CARD)`, decline, cancel; small artificial delay for realism). Cashier keys the amount into the real standalone terminal in parallel; POS records the method for reconciliation — exactly the scope-spec posture.

---

## 3. `ScaleProtocol` — protocol variants as data, not code forks

Serial scales differ: some **stream continuously** (frame per ~100 ms, ASCII like `ST,GS, 1.234 kg\r\n`), some **answer a poll command** (`W\r` → one frame); frame formats, units, and stability flags vary per brand. Inside `SerialScale`, isolate parsing:

```kotlin
internal interface ScaleProtocol {
    /** Feed raw bytes; returns zero or more complete parsed readings (grams). Stateful framing inside. */
    fun feed(bytes: ByteArray): List<WeightReading>
    val pollCommand: ByteArray?   // null => continuous-stream protocol
    val pollIntervalMs: Long?
}
```

- Implementations selected by `hw.scale.serial.protocol` id. Ship two: `continuous-generic` (regex-configurable: frame delimiter, capture groups for value/unit/stability flag, unit→grams factor — the regex + factor are **config strings**, so a new frame format is a settings row, not a class) and `poll-generic` (same parsing + configurable poll bytes/interval). A genuinely weird device gets a third `ScaleProtocol` class — still zero change to `Scale`/POS (the supported-devices posture: new device = new driver/protocol at the edge).
- Software stability fallback: if the device has no stability flag, mark `stable=true` when K consecutive readings within ±T g over W ms (K/T/W config; defaults 3/2 g/500 ms).
- All arithmetic in integer grams; sub-gram devices round half-up at the parser boundary (01 §5).
- **Raw-byte logging mode** (`hw.scale.serial.rawlog=true`): before parsing, append `epochMillis <tab> hexBytes` lines to `scale-raw-<date>.log` in the app data dir. This is the Phase-0 capture tool *and* stays available in the shipped app for field debugging of a new device.

---

## 4. Phase-0 scale spike (week one — spec §6.1 risk burn-down)

**Goal:** prove (or disprove) that a real serial scale can be read, *before any POS screen exists*. Isolated, throwaway-friendly.

- **Where:** standalone plain-JVM module `:tools:scale-spike` (register in `settings.gradle.kts`; plain `org.jetbrains.kotlin.jvm`, dep: jSerialComm only; a `main()` — no app, no Koin, no Compose). Deliberately outside the KMP module graph so it can be hacked freely and deleted later; the *learnings* (protocol config + capture logs) migrate into `:data:hardware:impl`.
- **Harness steps (in order):**
  1. Enumerate: print `getCommPorts()` with `getSystemPortName()` + `getDescriptivePortName()`; plug/unplug the scale to identify its port.
  2. Dump: open at candidate bauds (9600 first, then 4800/19200/38400/115200; 8N1 then 7E1), `TIMEOUT_READ_SEMI_BLOCKING`, print raw hex + ASCII, write the timestamped capture log. If silence: send common poll bytes (`W\r`, `P\r`, ENQ 0x05) and observe.
  3. Iterate parser: adjust the frame regex until stable weights parse; place a known weight, verify grams; test settle behavior and stability flag.
- **Go/no-go gate (end of week one):** GO = a documented `(port, baud, framing, protocol-id, regex, unit factor)` tuple that parses stable weights reliably for ≥5 min including replug. NO-GO = client's integrated sticker-printer unit emits nothing readable and no substitute scale obtained → **`ManualScale` becomes the primary POS mode** (already a first-class supported mode, so nothing downstream changes — POS is built manual-first in Phase 4 regardless), and `SerialScale` ships tested only against replay captures.
- **Deliverables (checked into repo):**
  - `data/hardware/impl/src/jvmTest/resources/scale-captures/<device-slug>.log` — raw timestamped byte capture (feeds replay tests, §7).
  - `docs/hardware/scale-protocol-notes.md` — device identity, port params, frame format, poll vs stream, stability flag semantics, go/no-go verdict.

---

## 5. Presentation & evidence plan (spec §6.2)

- **The live demo runs entirely on mock/manual drivers** — first-class mode, nothing hidden; stated as such on the slide.
- **Dev-time screen recording checklist** (record once, during Phase 0/4, while a physical scale is in hand):
  1. Frame shows *both* the physical scale display and the app window.
  2. Settings screen visible: `Scale driver: SerialScale`, port/baud shown.
  3. Place item → scale display and app weight pane show the same grams, live; remove/replace to show settle behavior.
  4. Complete one weigh-and-sell line from that reading (price computed from grams).
  5. Bonus shot: unplug cable → status banner flips to DISCONNECTED → switch to manual. (Failure handling is stronger evidence than the happy path.)
  6. Keep raw-log mode ON during the session — the capture log is a by-product of the same sitting.
- **Live driver-swap demo (on stage, no hardware):** POS selling on `MockScale` (scripted mode) → open Settings → Devices → change Scale driver `MockScale → SerialScale` (with no device: status honestly shows DISCONNECTED — narrate against the recording) → swap back → keep selling. Alternative equally valid swap shown live end-to-end: `MockScale → ManualScale`. The one-line-config swap *is* the abstraction proof; narrate over the recording for the serial leg.
- **Physical scale priority order:** (1) borrow the client's scale for one off-hours session — best evidence *and* the week-one protocol gate; (2) any serial-output scale from teammate/lab; (3) only then buy the cheapest serial/USB-output scale — brand/capacity/precision irrelevant. **Do not buy a bakery scale.**

---

## 6. Supported-devices list

The stated posture, not an apology (scope spec §6). Two homes, one source:

- **`docs/hardware/supported-devices.md`** — canonical: table of interface / driver / devices ("Serial scales matching continuous-generic or poll-generic protocol at configurable baud — validated on: `<Phase-0 device>`"; "ESC/POS printers exposed as an OS print service — validated on: `<model or 'escpos-coffee-compatible'>`"; "Cash drawers with RJ11 printer-kick"; "Any HID keyboard-wedge barcode scanner"; "Payment terminals: simulated only"). New device = new driver/protocol row, zero core change.
- **Settings → Devices UI copy** (`:feature:settings`, POS section): each driver picker shows only registry-available drivers for the platform, with a one-line "Supported devices" caption per interface drawn from string resources mirroring the doc. Unavailable-on-this-platform drivers are absent, not greyed out.

---

## 7. Testing

| Suite | Where | What |
|---|---|---|
| **Driver contract tests** | `:data:hardware:impl` `commonTest` | Abstract `ScaleContractTest` (subclassed per driver): `start()` idempotent; status reaches CONNECTED; readings flow emits; `close()` stops emissions; stable readings are positive grams. Run against `MockScale` (both modes) and `ManualScale`. Same pattern `PrinterContractTest` for `MockPrinter`; `SimulatedTerminal` approve/decline/cancel paths. |
| **`SerialScale` replay tests** | `jvmTest` | Feed the Phase-0 capture log (`resources/scale-captures/*.log`) into `ScaleProtocol.feed()` chunked at recorded timing boundaries (and re-chunked at 1-byte granularity to prove framing is split-safe); assert parsed grams sequence + stability flags. No hardware, CI-safe. Garbage-input test: random bytes yield zero readings, no throw. |
| **`ManualScale` validation** | `commonTest` | kg-string → grams conversion (3 decimals, half-up), rejects ≤0 / > max / malformed; each submit emits exactly one stable reading. |
| **`DriverRegistry` swap** | `jvmTest` | Fake DataStore prefs flow: profile change → new driver instance emitted, previous `close()`d; unknown driverId → falls back to manual + warning. |
| **ViewModel-facing fakes** | `commonTest` fixtures | `FakeScale`/`FakePrinter` (template `FakeNoteRepository` style) exported for `:feature:pos:impl` tests. |

Run per CLAUDE.md: `:data:hardware:api`/`impl` `compileKotlin{Jvm,Android,Ios*}`, then root `test` + `:data:hardware:impl:jvmTest`.

---

## 8. Work items (ordered)

1. **Phase-0 spike harness** — `tools/scale-spike/` (+ `settings.gradle.kts`). *Accept:* `main()` lists ports and dumps raw hex/ASCII with timestamped log file; poll-command sending; README-less (notes go in deliverable doc).
2. **Phase-0 execution** — capture + notes deliverables at `data/hardware/impl/src/jvmTest/resources/scale-captures/` and `docs/hardware/scale-protocol-notes.md`. *Accept:* go/no-go verdict recorded; log replays ≥1 stable weight.
3. **`:data:hardware:api`** — `data/hardware/api/` (build script per §1.1; `settings.gradle.kts`). Interfaces/models under `.../domain/hardware/`: `Scale`, `WeightReading`, `DeviceStatus`, `ReceiptPrinter`, `ReceiptDoc`, `PrintResult`, `CashDrawer`, `PaymentTerminal`, `PaymentResult`, `DriverChoice`, `DeviceProfile`, `DriverRegistry`, `DriverDescriptor` + KDoc note on scanner-as-input. *Accept:* compiles all targets; zero deps beyond coroutines/datetime; everything public is an interface or model.
4. **`:data:hardware:impl` skeleton + mocks** — `data/hardware/impl/`: `MockScale` (random+scripted), `ManualScale`, `MockPrinter`, `MockDrawer`, `SimulatedTerminal`, all `internal` under `.../data/hardware/`; `di/HardwareDataModule.kt` + `internal expect val PlatformModule` with three actuals (android/ios bind nothing extra). *Accept:* contract tests (item 7) pass on all mocks; only public symbol is `HardwareDataModule`.
5. **`DriverRegistryImpl` + DataStore settings** — profile keys per §1.4 via `:core:datastore`; factory multibinding; swap semantics (close-old, fallback-to-manual). Wire `HardwareDataModule` into `composeApp/.../di/AppModule.kt`. *Accept:* swap test green; app boots on all platforms with manual scale default.
6. **JVM real drivers** — `jvmMain`: `SerialScale` + `ScaleProtocol` (`continuous-generic`, `poll-generic`, raw-log mode), `EscPosPrinter` (+ configurable drawer-kick pulse), `PrinterKickDrawer`; register factories in JVM `PlatformModule` actual. Verify jSerialComm/escpos-coffee APIs via context7 first. *Accept:* replay tests green from item-2 captures; `compileKotlinJvm` clean; Android/iOS builds unaffected (no jvm dep leakage).
7. **Test suites** per §7. *Accept:* contract suite runs against every non-serial driver; replay + registry + manual-validation tests in `jvmTest`/`commonTest`; all green in `./gradlew test` + `:data:hardware:impl:jvmTest`.
8. **Supported-devices doc + settings copy** — `docs/hardware/supported-devices.md`; Devices section spec handed to `:feature:settings` (05 wires the UI). *Accept:* doc lists every driver with validated-on entries; settings copy strings staged in resources.
9. **Evidence assets** — recording per §5 checklist stored outside repo (link in `docs/hardware/scale-protocol-notes.md`); demo swap script folded into `11-seeds-and-demo.md`. *Accept:* checklist items 1–5 all visible in the recording.

Dependency note for `05-pos-desktop.md`: POS consumes only `:data:hardware:api` (+ `HardwareDataModule` via app DI). The weight pane, disconnect banner, switch-to-manual action, on-screen mock receipt, simulated-terminal dialog, and scanner input capture are POS-side contracts defined here (§2) but implemented there.

## Open questions

1. **Disconnect detection constant** — confirm jSerialComm's port-disconnect listener event name/behavior on macOS/Windows via context7 at implementation time; if unreliable on a platform, the stale-frame watchdog is the sole DISCONNECTED trigger (already planned).
2. **Client scale readability** — unresolved until Phase 0; the integrated sticker-printer unit may expose nothing. Plan already carries the manual-as-primary fallback; decision recorded in `scale-protocol-notes.md`.
3. **escpos-coffee vs raw ESC/POS bytes** — escpos-coffee (verified, active, prints via OS print service) is the default; if the Phase-4 printer isn't visible as an OS print service (serial-only printer), fall back to writing ESC/POS bytes over jSerialComm directly (drawer kick = `ESC p 0 50 100` bytes) — same `ReceiptPrinter` contract, decide when a real printer is in hand.
4. **Should `DeviceProfile` sync to the backend?** Default: **no** — device config is per-station, stays in local DataStore. Revisit only if multi-station-per-branch appears (currently out of scope).
5. **Scripted mock timing for the stage demo** — exact scripted weight sequence/timings to be fixed in `11-seeds-and-demo.md` alongside the demo narrative.
