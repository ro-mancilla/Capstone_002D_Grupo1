# 05 — Desktop POS Feature

Depends on: `01-architecture-decisions.md` (binding), `03-sync.md` (outbox + SyncStatus), `04-hardware.md` (driver interfaces). Sources: `spec/SPECS.md` (Client 1, Inventory Semantics, Hardware Abstraction), `spec/bakery_system_scope_spec.md` §4.1 §5. Conventions: `writing-kmp-features` skill — followed exactly (api/impl split, `internal` everything except interfaces + DI vals, UDF Action/State/Event, `Channel` events, injected dispatchers, `alias(libs.plugins.f776.xxx)`).

**Zero production code here.** All fragments are contract sketches; implementation agents verify APIs via context7 before writing real code.

POS is **pure common code** in `commonMain` (01 §1: one app, role-shaped). Desktop-first layout via adaptive helpers; serial drivers exist only on JVM but the POS feature never knows that — it sees `Scale`/`ReceiptPrinter` interfaces from `:data:hardware:api`, and `ManualScale`/mock drivers make every flow work on any platform. **Every flow below must work with zero connectivity** — the only network-touching component is the sync engine, which POS never calls directly.

---

## 1. Modules

### `:jvm:feature:pos:api`
- Plugins: `kotlinMultiplatform`, `androidKmpLibrary`, `kotlinSerialization`.
- Contents (only): `route/PosRoutes.kt`
  - `@Serializable data object PosRoute : NavKey` — the single top-level POS destination (sale flow is modal state within the screen, not routes; payment is a dialog/pane, not a NavKey — keeps cart state in one ViewModel and back-gesture semantics trivial).
  - `fun PolymorphicModuleBuilder<NavKey>.posRoutes() { subclass(PosRoute.serializer()) }`

### `:jvm:feature:pos:impl`
- Plugins: `kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform`, `kotlinSerialization`.
- Depends on: `:jvm:feature:pos:api`, `:core:ui`, `:core:navigation` (explicit — not transitive), `:data:sales:api`, `:data:catalog:api`, `:data:hardware:api`, `:data:sync:api`, `:data:tenant:api`, `:data:production:api`. **Never** any `:data:*:impl`.
- Layout (all `internal` except `PosModule`):
  - `screens/pos/PosViewModel.kt`, `PosScreen.kt` (Root + Screen split), `PosAction.kt`, `PosEvent.kt`, `PosState.kt`
  - `screens/pos/components/` — `ProductTypeGrid.kt`, `CartPane.kt`, `WeighPanel.kt`, `QuantityStepper.kt`, `PaymentDialog.kt`, `ReceiptView.kt` (on-screen fallback), `StatusBar.kt` (sync + scale + cashier/branch), `BatchReadyBanner.kt`, `BarcodeCapture.kt` (invisible focus sink for HID wedge)
  - `di/PosModule.kt` — the one public symbol:
    ```
    val PosModule = module {
        includes(PosNavigationModule, SalesDataModule, CatalogDataModule,
                 HardwareModule, SyncDataModule, TenantDataModule, ProductionDataModule)
        viewModel { PosViewModel(...) }
    }
    ```
  - `navigation/PosNavigation.kt` — `val PosNavigationModule = module { navigation<PosRoute> { PosScreenRoot() } }`
- App wiring: add `PosModule` to `composeApp/.../di/AppModule.kt` `includes(...)`; register `posRoutes()` in `AppNavigation.kt` polymorphic block; add to `TopLevelKeys` + nav-suite item **filtered through `EntitlementsProvider`** per 01 §7 (POS_SALES is always-on, but the filter mechanism lands here) and role (`CASHIER`/managers see it; per 01 §1 `AppNavigation` computes destinations from session role).
- Previews: `@LocalizedPreview` + `@AdaptivePreview` per screen (skill requirement) — desktop-size preview is the primary design target.

### `:data:sales:api`
- Plugins: `kotlinMultiplatform`. Package `com.f776.ecopan.domain.sales`.
- See §2.

### `:data:sales:impl`
- Plugins: `kotlinMultiplatform`, `androidKmpLibrary`. Package `com.f776.ecopan.data.sales`.
- `internal class SaleRepositoryImpl : SaleRepository` delegating to `internal interface LocalSaleDataSource` / `internal class DbSaleDataSource` (Room DAOs from `:core:database`, injected `defaultDispatcher`), mappers in `local/mapper/`.
- **Atomicity contract (per 03):** `recordSale` performs ONE Room transaction inserting `SaleEntity` + N `SaleLineEntity` + one outbox row (`OutboxEntity` with the client-minted sale `Uuid` as idempotency key, per 01 §6.3). The outbox DAO is exposed to `:data:sales:impl` the same way `:data:sync:impl` uses it — via `:core:database` DatabaseModule bindings; the sync engine drains it independently. POS never awaits network.
- Koin: `val SalesDataModule = module { includes(DatabaseModule); factoryOf(::DbSaleDataSource).bind<LocalSaleDataSource>(); factoryOf(::SaleRepositoryImpl).bind<SaleRepository>() }`

---

## 2. `:data:sales:api` contract

Sketch (illustrative, not final code):

```
enum class PaymentMethod { CASH, CARD, TRANSFER, OTHER }   // matches 02 §1 sales.payment_method
enum class SoldBy { WEIGHT, UNIT }          // lives in :data:catalog:api; re-exported use only

data class Sale(
    val id: Uuid,                            // client-minted, 01 §3
    val branchId: Uuid,
    val cashierId: Uuid,
    val timestamp: Instant,                  // wall-clock, domain meaning only
    val paymentMethod: PaymentMethod,
    val totalMinor: Long,                    // Σ lineTotalMinor, stored
    val cashTenderedMinor: Long?,            // CASH only
    val changeMinor: Long?,                  // CASH only, stored
    val lines: List<SaleLine>,
)

data class SaleLine(
    val id: Uuid,
    val productId: Uuid,
    val productTypeSnapshot: String,         // type name at sale time — survives catalog edits
    val soldBy: SoldBy,                      // snapshot
    val weightGrams: Long?,                  // WEIGHT lines; null for UNIT
    val quantity: Int?,                      // UNIT lines; null for WEIGHT
    val unitPriceMinorSnapshot: Long,        // pricePerKgMinor (WEIGHT) or priceMinor (UNIT), snapshotted
    val dayOld: Boolean,
    val dayOldDiscountBpApplied: Int?,       // basis points snapshot when dayOld && policy=DISCOUNT
    val lineTotalMinor: Long,                // computed once at add-time, STORED, authoritative (01 §5)
)

interface SaleRepository {
    /** Atomic: Sale + lines + outbox row in one local transaction. Returns nothing to await network. */
    suspend fun recordSale(sale: Sale)
    /** Today's sales for the current branch, local-first — drives the till view / running total. */
    fun observeTodaySales(branchId: Uuid): Flow<List<Sale>>
}
```

Rules (binding, from 01 §5–6):
- Append-only: no update/delete members on `SaleRepository`. Voids after payment are out of scope (§6 edge cases).
- WEIGHT line total: `round_half_up(weightGrams * pricePerKgMinor / 1000)`, computed once, stored. UNIT: `quantity * priceMinor`, then day-old discount `total = round_half_up(total * (10000 - bp) / 10000)` applied per line, stored. Historical totals are never recomputed. `Sale.totalMinor = Σ stored line totals`.
- Day-old discount stored as **basis points** (`Int`), converted from the catalog policy's pct at snapshot time — consistent with 01 §5 formula-bp convention; no floats.
- "Today" boundary: local calendar day in the till's own OS timezone (DECIDED — see §10, this is a till-local concern distinct from 02's `REPORTING_TZ` server-side analytics constant); the DAO query filters by epoch-millis range computed in the data source.

Consumed contracts (defined elsewhere; listed so this plan is self-checking):
- `:data:catalog:api` — `CatalogRepository.observeProducts(): Flow<List<Product>>` (`Product(id, name, type, soldBy, priceMinor|pricePerKgMinor, barcode?, imageRef?, dayOldPolicy)`), `suspend fun findByBarcode(code: String): Product?`. `DayOldPolicy` = `DISCARD` | `DISCOUNT(discountBp: Int)`.
- `:data:hardware:api` (04) — `Scale { val readings: Flow<ScaleReading>; val status: Flow<ScaleStatus> }` with `ScaleReading(grams: Long, stable: Boolean)`, `ScaleStatus { CONNECTED, DISCONNECTED, ERROR, MANUAL }`; `ScaleSelector` (switch active driver: SERIAL ↔ MANUAL at runtime); `ReceiptPrinter { suspend fun print(receipt: ReceiptDoc): PrintResult }`; `CashDrawer { suspend fun open() }`; `PaymentTerminal { suspend fun authorize(amountMinor: Long): TerminalResult }` (simulated impl only, per spec).
- `:data:sync:api` (03) — `SyncStatusProvider { val status: Flow<SyncStatus> }`, `SyncStatus(connectivity: ONLINE|OFFLINE, phase: IDLE|SYNCING|ERROR, pendingCount: Int, lastSyncAt: Instant?)`.
- `:data:tenant:api` — `SessionProvider` (current tenant/branch/user/role), `EntitlementsProvider`.
- `:data:production:api` — `observeBatchReady(): Flow<BatchReadyEvent>` — merged local + SSE-received feed (SSE ingestion is the sync/data layer's job; POS just collects the flow; offline it simply only shows locally-known batches — degrades silently).

---

## 3. Screen flow

Single `PosRoute` destination; internal UI modes driven by `PosState` (no sub-routes):

1. **Idle / building cart** — product-type grid visible; cart accumulates lines.
2. **Weigh path** (product `soldBy == WEIGHT`): tap type tile → weigh panel opens showing live readout from `Scale.readings` (grams → kg display) + stability indicator. When `stable == true` and `grams > 0`, "Add" arms (also auto-capture option: first stable non-zero reading fills the field, cashier confirms). Price preview = rounding rule applied live. Confirm → `SaleLine` appended with snapshots → panel closes, scale expected back to zero before next weigh (tare-drift open question §10).
3. **Unit path** (`soldBy == UNIT`): tap tile → line added at qty 1; stepper on the cart line adjusts (or long-press tile → qty entry). Same snapshot rules.
4. **Barcode path** (resale products): a hidden always-focused text sink captures HID wedge input (burst of chars + terminator, typically Enter). On terminator → `findByBarcode` → hit: add/increment UNIT line; miss: `ShowMessage("unknown barcode")`. Scanner needs no driver — it is input capture in the UI layer only; the capture component must not steal focus from an open manual-weight/cash-tendered field (buffer-with-timeout heuristic: sub-~100 ms inter-key gap ⇒ scanner).
5. **Mixed cart** — weight, unit, and barcode lines coexist; per-line: name/type, weight-or-qty, unit-price snapshot, day-old chip, line total, void (✕).
6. **Day-old toggle per line** — policy-driven from the product's `DayOldPolicy` snapshot at add-time: `DISCARD` → toggle absent/disabled (product cannot be sold day-old — attempting is a policy violation, not a UI option); `DISCOUNT(bp)` → toggle applies bp to the stored line total (recompute *this line only*, before payment; it is still pre-sale draft state).
7. **Payment step** — "Charge" button (enabled iff cart non-empty) opens payment dialog: total, method select. CASH: tendered-amount pad → change = tendered − total (must be ≥ 0 to confirm) → on confirm: `recordSale` → `CashDrawer.open()` → print. CARD: `PaymentTerminal.authorize(total)` (simulated: succeeds after a delay; a "decline" test button on the simulated driver for demo) → on approve: `recordSale` → print (no drawer kick). OTHER: method recorded, no hardware. **`recordSale` commits before any hardware side effect** — a printer failure never loses the sale.
8. **Receipt** — build `ReceiptDoc` from the just-recorded `Sale`; `ReceiptPrinter.print()`; on failure or no-printer driver → `ShowReceiptOnScreen` event renders `ReceiptView` (first-class fallback, per spec). Then cart resets to Idle.
9. **Batch-ready** — collecting `observeBatchReady()` surfaces a transient banner/badge ("Marraqueta batch ready, 14:32") with a dismissible list; purely informational.
10. **Lock screen / cashier switch (01 §4 device session + PIN unlock)** — the till itself holds a long-lived **device session** (enrolled by one online login, 03 §8); nobody "logs in" at the till after that. The lock screen shows the **cached user list** for the branch (from the `:data:tenant` users cache, synced with `pinHash` per 02 §4.2) + a numeric **PIN pad**; PIN verified **locally** against the slow-KDF hash — fully offline-capable. Tapping another cashier's tile mid-shift is a **one-tap switch**: enter their PIN, `SessionProvider`'s current user flips, cart is preserved (draft state belongs to the till, not the user — open question below if that's wrong). PIN attempts are **rate-limited locally** (e.g. 5 misses → 30 s lockout, escalating; constants tuned later); the PIN attributes actions, the device session authenticates (01 §4). Manual lock action in the chrome; optional idle auto-lock is a settings row, not v1-critical.

Always-visible chrome (spec REQUIRES the network indicator):
- **Sync indicator**: pill from `SyncStatus` — `OFFLINE (n pending)` / `SYNCING` / `SYNCED hh:mm` / `SYNC ERROR (n)`. Never blocks selling.
- **Scale indicator**: from `Scale.status` — on `DISCONNECTED|ERROR`, one-tap switch to `ManualScale` (weigh panel becomes a numeric grams/kg entry — a supported mode, not an error state); one-tap switch back.
- **Cashier + branch header** from `SessionProvider` — shows the currently-unlocked cashier (the one snapshotted as `cashierId` on every recorded sale, already per §2's `Sale.cashierId`); tap → lock screen / cashier switch (flow item 10).

---

## 4. Wireframe (main POS screen, desktop-first)

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ ECOPAN POS   Branch: Centro   Cashier: M. Rojas   [⚖ SCALE OK ▾] [☁ OFFLINE·3] │
├───────────────────────────────────────────────┬────────────────────────────────┤
│ ▸ Batch ready: Marraqueta 14:32  [dismiss]    │  CART                          │
│┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐│  Marraqueta  0.842kg  $1.010 ✕ │
││Marraque-│ │ Hallulla│ │ Integral│ │Empanada ││  Hallulla    1.210kg  $1.390 ✕ │
││ta       │ │         │ │         │ │ (unit)  ││  Empanada    [− 2 +]  $2.400 ✕ │
││$1200/kg │ │$1150/kg │ │$1400/kg │ │ $1200   ││  Integral ⏳1d 0.500kg  $490 ✕ │
│└─────────┘ └─────────┘ └─────────┘ └─────────┘│      (day-old −30%)            │
│┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐│  ─────────────────────────────│
││ Dobla-  │ │ Coliza  │ │Berlín   │ │ Torta   ││  TOTAL              $5.290    │
││ dita    │ │         │ │ (unit)  │ │ (unit)  ││                                │
│└─────────┘ └─────────┘ └─────────┘ └─────────┘│  ┌──────────────────────────┐  │
│                                               │  │       CHARGE  $5.290     │  │
│ WEIGH: Hallulla ─────────────────────────────┐│  └──────────────────────────┘  │
│ │   1.210 kg   ● STABLE    ≈ $1.392  [ADD]  ││  [scan barcode: ready]         │
│ └─────────────────────────────────────[✕]───┘│                                │
├───────────────────────────────────────────────┴────────────────────────────────┤
│ Today: 47 sales · $61.240        [Till view]              [Manual weight] [⚙]  │
└────────────────────────────────────────────────────────────────────────────────┘
```
Payment dialog (over all): total, `CASH | CARD | TRANSFER | OTHER` segmented; CASH → tendered pad + change readout; CARD → simulated terminal progress; confirm → record → drawer/print → receipt (printed or on-screen). Compact widths (same code, phone/tablet): grid and cart become two stacked panes / list-detail via `:core:ui` adaptive helpers.

---

## 5. UDF sketch (PosViewModel)

State (immutable, exposed `StateFlow<PosState>`; cart is draft — nothing persisted until payment):

```
internal data class PosState(
    val products: List<ProductUi> = emptyList(),      // from CatalogRepository (offline cache)
    val cart: List<CartLineUi> = emptyList(),         // draft lines, snapshots already taken
    val cartTotalMinor: Long = 0,
    val weighing: WeighingUi? = null,                 // null = panel closed
    //   WeighingUi(product, grams, stable, priceMinorPreview, manualEntry: Boolean)
    val payment: PaymentUi? = null,                   // null = dialog closed
    //   PaymentUi(method?, cashTenderedMinor?, changeMinor?, terminalPhase: IDLE|AUTHORIZING|APPROVED|DECLINED)
    val scaleStatus: ScaleStatus = DISCONNECTED,
    val syncStatus: SyncStatus = ...,
    val session: SessionUi,                           // cashier + branch names
    val todaySummary: TodaySummaryUi,                 // count + total from observeTodaySales
    val batchBanners: List<BatchReadyUi> = emptyList(),
)
```

```
internal sealed interface PosAction {
    data class OnProductTapped(val productId: Uuid) : PosAction        // WEIGHT→open weigh; UNIT→add line
    data object OnWeighCancelled : PosAction
    data object OnWeighConfirmed : PosAction                           // captures current stable reading
    data class OnManualWeightEntered(val grams: Long) : PosAction
    data object OnSwitchToManualScale : PosAction
    data object OnSwitchToSerialScale : PosAction
    data class OnQuantityChanged(val lineId: Uuid, val quantity: Int) : PosAction
    data class OnDayOldToggled(val lineId: Uuid) : PosAction
    data class OnVoidLine(val lineId: Uuid) : PosAction                // pre-payment only
    data class OnBarcodeScanned(val code: String) : PosAction
    data object OnChargeClicked : PosAction
    data class OnPaymentMethodSelected(val method: PaymentMethod) : PosAction
    data class OnCashTenderedChanged(val amountMinor: Long) : PosAction
    data object OnConfirmPayment : PosAction                           // recordSale → drawer → print
    data object OnPaymentCancelled : PosAction
    data object OnDismissBatchBanner : PosAction
    data object OnReceiptDismissed : PosAction
}

internal sealed interface PosEvent {
    data class ShowMessage(val message: UiText) : PosEvent             // snackbar: unknown barcode, discard-policy block, decline…
    data class ShowReceiptOnScreen(val receipt: ReceiptUi) : PosEvent  // print fallback
    data object SaleCompleted : PosEvent                               // clears cart / success flash
    data object ScaleUnavailable : PosEvent                            // prompts one-tap manual switch
}
```

- `Channel<PosEvent>` + `receiveAsFlow()`, consumed via `ObserveAsEvents` in `PosScreenRoot`. Screen composable takes `(state, onAction)` only — no ViewModel reference.
- Injected deps: `SaleRepository`, `CatalogRepository`, `Scale`/`ScaleSelector`, `ReceiptPrinter`, `CashDrawer`, `PaymentTerminal`, `SyncStatusProvider`, `SessionProvider`, production feed, `SavedStateHandle`, dispatchers by `named(...)` qualifier — never hardcoded.
- Weigh readout: while `weighing != null`, collect `Scale.readings` into state (conflated); stop on close.
- All money/weight math in a small pure `internal object PosCalculator` (round-half-up line totals, day-old bp application, change) — unit-tested exhaustively (§8).

---

## 6. Edge cases (plan for all)

| Case | Behavior |
|---|---|
| Zero weight | Add disabled while `grams <= 0` (empty scale must not create a $0 line). |
| Unstable weight | Add disabled until `stable`; readout shows live value greyed + "unstable". Auto-capture only fires on stable. |
| Weight tap on UNIT product / unit path on WEIGHT product | Impossible by construction — the tile's path is chosen from `soldBy`; there is no user-selectable mode. Barcode hit on a WEIGHT product → route to weigh panel, not a unit line. |
| Price change mid-shift | Catalog cache updates via sync pull; **already-added cart lines keep their snapshots** (unit price, type, day-old bp frozen at add-time; totals stored per 01 §5). New lines use new prices. No retroactive recompute, ever. |
| Day-old on DISCARD product | Toggle not offered; barcode/any path attempting it → `ShowMessage` (policy block). |
| Void line before payment | Allowed — draft state, plain removal, nothing recorded. |
| Void/refund after payment | **Out of scope** (append-only sales, 01 §6.1). No delete path exists in `SaleRepository`. State this in UI copy ("completed sales cannot be modified"); corrections are a manager/backend concern, not built. |
| Offline everything | Sale recording, catalog read, weigh, print, drawer, cash math: all local. `recordSale` = local tx + outbox; sync drains later (03). Card "authorization" is simulated ⇒ also offline. Only degradation: batch-ready events from *other* devices don't arrive until reconnect. Acceptance: full sale in airplane mode. |
| Scale dies mid-shift | `ScaleStatus` flips → indicator red → `ScaleUnavailable` event → one-tap `ManualScale`; weigh panel becomes numeric entry. Switch back any time. |
| Printer failure / absent | Sale already committed; `ShowReceiptOnScreen` fallback; drawer kick attempted independently (cash must still be storable). |
| Terminal decline (simulated) | Payment dialog stays open, `DECLINED` phase, retry or switch method; nothing recorded. |
| Scanner burst into an open text field | Wedge disambiguation by inter-key timing; captured bursts never leak into manual fields. |
| Cash tendered < total | Confirm disabled; change never negative. |
| App crash with non-empty cart | Cart is draft; acceptable to lose (v1). Note in Open questions whether to persist via `SavedStateHandle`/DataStore. |

---

## 7. Data flow recap

```
tile tap ──► PosAction ──► PosViewModel ──► PosCalculator (pure int math, snapshots)
Scale.readings ─┘                    │
                                     ▼ OnConfirmPayment
                    SaleRepository.recordSale ── one Room tx: sale + lines + outbox
                                     │                          │
                     CashDrawer/Printer (after commit)          ▼ (later, 03)
                                                        sync engine ► backend ► serverSeq
```

---

## 8. Testing (01 §10)

All `jvmTest` in the owning modules, template fake style (`FakeNoteRepository` pattern). Fakes needed: `FakeScale` (scripted `List<ScaleReading>` emissions + status control), `FakeSaleRepository` (records calls, exposes recorded `Sale`), `FakeCatalogRepository` (seeded products incl. one DISCARD, one DISCOUNT, one barcode), `FakePaymentTerminal` (scripted approve/decline), `FakeReceiptPrinter` (scripted success/failure), `FakeCashDrawer`, `FakeSyncStatusProvider`, `FakeSessionProvider`.

Named cases — `PosViewModelTest` (`:jvm:feature:pos:impl` jvmTest):
- `weightProduct_stableReading_addsLineWithComputedStoredTotal`
- `weightProduct_zeroOrUnstableReading_addDisabled`
- `unitProduct_tap_addsQtyOneLine_stepperUpdatesTotal`
- `barcode_knownUnitProduct_addsOrIncrementsLine`
- `barcode_unknown_emitsShowMessage`
- `barcode_weightProduct_opensWeighPanelInsteadOfUnitLine`
- `dayOldToggle_discountPolicy_appliesBpToLineTotalOnly`
- `dayOldToggle_discardPolicy_notOfferedAndBlocked`
- `catalogPriceChangeMidCart_existingLinesKeepSnapshot_newLinesUseNewPrice`
- `voidLineBeforePayment_removesDraftLine_nothingRecorded`
- `cashPayment_tenderedComputesChange_recordSaleThenDrawerThenPrint_orderAsserted`
- `cashPayment_tenderedBelowTotal_confirmDisabled`
- `cardPayment_declined_nothingRecorded_dialogStaysOpen`
- `printerFailure_saleStillRecorded_showReceiptOnScreenEmitted`
- `scaleDisconnect_emitsScaleUnavailable_manualEntryProducesIdenticalLine`
- `recordSale_offlineStatus_succeedsIdentically` (assert zero dependency on sync state)
- `pinUnlock_wrongPinRateLimitsLocally_lockoutEscalates` (5 misses → lockout; timer via virtual time; no network calls ever made)
- `cashierSwitchMidShift_offline_nextSaleAttributedToNewCashier` (switch via PIN with `FakeSyncStatusProvider` offline; recorded `Sale.cashierId` is the new cashier, prior sales untouched)

`PosCalculatorTest` (money rounding, pure):
- `weightLineTotal_halfUpRounding_boundaryCases` (e.g. ×.5 minor-unit products; 842 g × 1200/kg = 1010.4 → 1010; exact-half cases round up)
- `dayOldDiscountBp_halfUpRounding_neverNegative`
- `saleTotal_isSumOfStoredLineTotals_notRecomputed`
- `changeCalc_exactAndOverTender`
- property-style: totals always ≥ 0, integer-only, `f(w, p)` monotonic in `w`.

`SaleRepositoryImplTest` (`:data:sales:impl` jvmTest, Room in-memory or fake DAO per template pattern):
- `recordSale_persistsSalePlusLinesPlusOutboxRow_atomically` (failure injection mid-tx leaves nothing)
- `recordSale_isAppendOnly_noMutationApi`
- `observeTodaySales_filtersToLocalDayAndBranch`
- `outboxRow_carriesClientMintedUuid_forIdempotentIngest`

Run: `./gradlew :jvm:feature:pos:impl:jvmTest :data:sales:impl:jvmTest` (+ compile tasks for iOS/Android/Jvm targets, root `test`).

---

## 9. Work items (ordered)

1. **`:data:sales:api`** — `Sale`/`SaleLine`/`PaymentMethod` models + `SaleRepository` at `data/sales/api/src/commonMain/kotlin/com/f776/ecopan/domain/sales/`; register in `settings.gradle.kts`. ✅ compiles for all targets; no `impl`/Room types referenced.
2. **`:core:database` additions** — `SaleEntity`, `SaleLineEntity` (+ indices on `branch_id`,`timestamp`; FK line→sale), DAO with today-range query, version bump + migration per CLAUDE.md recipe; outbox entity comes from 03 (coordinate — one migration if landing together). ✅ schema export updated; migration test passes.
3. **`:data:sales:impl`** — data source, repository, mappers, `SalesDataModule` at `data/sales/impl/src/commonMain/kotlin/com/f776/ecopan/data/sales/`. ✅ `recordSale` atomicity test green; only public symbol is the module val.
4. **`:jvm:feature:pos:api`** — `PosRoute` + `posRoutes()` at `jvm/feature/pos/api/.../route/PosRoutes.kt`. ✅ mirrors `HomeRoutes.kt` shape.
5. **PosCalculator + PosViewModel + Action/State/Event** (no UI yet) at `jvm/feature/pos/impl/.../screens/pos/`. ✅ all §8 ViewModel/calculator tests green against fakes; zero hardware/network imports outside `:data:*:api`.
6. **UI: grid + cart + weigh panel + steppers** (`ManualScale`/`MockScale` first, per phasing) at `.../screens/pos/components/`. ✅ full weigh-and-sell + unit sale on desktop run with mock drivers; adaptive compact layout renders; previews present.
7. **Payment dialog + receipt + drawer** — CASH/CARD/OTHER, simulated terminal, on-screen receipt fallback. ✅ **full sale recordable with airplane-mode backend** (kill network: sale persists, outbox row present, receipt renders); recordSale-before-hardware ordering test green.
8. **Barcode HID capture** — `BarcodeCapture` sink + timing heuristic + lookup wiring. ✅ scripted char-burst test adds correct line; typed text in manual fields unaffected.
9. **Status bar** — sync pill (`SyncStatus`), scale indicator + one-tap manual switch, cashier/branch header, today summary. ✅ toggling `FakeSyncStatusProvider`/`FakeScale` states updates chrome; selling never blocked by any status.
10. **Batch-ready surface** — collect production feed, banner list. ✅ fake event shows banner; offline shows only local batches without error.
11. **App wiring** — `PosModule` into `AppModule`, `posRoutes()` + `TopLevelKeys` + nav item in `AppNavigation.kt`, entitlement/role filter hook. ✅ cashier session lands on POS; compiles for Android/iOS/Jvm; root `test` + touched `jvmTest` green.
12. **`SerialScale` swap** (post Phase-0/04) — config-driven driver selection in Settings device section. ✅ live swap `MockScale` → `SerialScale` is one config change, zero `:jvm:feature:pos` edits (the demo proof).

## Open questions

- **Cart draft persistence across crash/restart** — lose it (simple, v1 default) or persist via `SavedStateHandle`/DataStore? Recommend: lose in v1, note in demo script.
- **Auto-capture vs. explicit Add** on stable weight — auto-add speeds the queue but risks phantom lines on tare drift; plan says explicit Add with auto-fill. Confirm with advisor/demo feel.
- **Tare/zero handling** — does `Scale` (04) expose tare, and must POS require return-to-zero between weighs? Depends on Phase-0 findings; keep out of `PosState` until known.
- **"Today" timezone source — DECIDED:** the till's own OS timezone, permanently, no tenant override. Rationale: 02's `REPORTING_TZ` is a fixed deployment-wide constant for cross-branch analytics rollups (a different concern — server-side aggregation across branches that may span timezones); this is what the cashier physically standing at that till sees as "today" for the local running total, which is inherently a property of the hardware's location, not the tenant's configuration. Implemented in `:data:sales:impl`'s `SaleRepositoryImpl`.
- **Receipt content/format** — minimal fields (tenant name, branch, lines, total, method, timestamp, sale id)? Tax/boleta fields explicitly out of scope? Needs one decision before `ReceiptDoc` in 04.
- **Till view scope** — read-only list of today's sales (from `observeTodaySales`) behind the "Till view" button: same screen pane or a second `TillRoute` in `:jvm:feature:pos:api`? Lean pane; add route only if it grows filters.
- **Barcode uniqueness** — enforce per-tenant unique product barcodes in catalog (02/07 admin), else `findByBarcode` semantics are ambiguous.
- **Cart across cashier switch** — flow item 10 keeps the draft cart on switch (cart belongs to the till). If mid-sale switches prove confusing in rehearsal, flip to clear-on-switch; one-line change.
