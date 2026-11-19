# 06 — Baker Mobile (batch logging, formula decrement, batch-ready events)

Depends on: `01-architecture-decisions.md` (binding — §1, §2, §5, §6, §8), `03-sync.md` (outbox/sync engine), `05-pos-desktop.md` + `07-web-dashboard.md` (consumers of batch-ready / manual-adjust surfaces). Spec: `spec/SPECS.md` Client 2 + Inventory Semantics; `spec/bakery_system_scope_spec.md` §4.2, §5. Conventions: CLAUDE.md + `.claude/skills/writing-kmp-features/` (followed exactly; no code here is final — verify APIs via context7).

Everything in this plan is `commonMain` unless the iOS-parity section says otherwise. The baker client is the same `:composeApp` binary family as POS (01 §1) — a `BAKER` session unlocks the production destinations. **Unlock model (01 §4 device session + PIN, same as POS — see 05 §3 item 10):** the phone/tablet is enrolled once by an online login (long-lived device session); thereafter the baker picks themself from the cached user list and enters their numeric **PIN**, verified locally against the `pinHash` synced in the USER pull — fully offline, locally rate-limited. The PIN attributes the batch (`bakerId` on every logged batch); the device session authenticates sync. No per-shift server login exists.

---

## 1. Module map

### `:mobile:feature:production:api`
Per `feature/home/api/.../route/HomeRoutes.kt` pattern. Contents (only):

```
feature/production/api/src/commonMain/kotlin/com/f776/ecopan/feature/production/route/
  ProductionRoutes.kt
    @Serializable data object ProductionRoute : NavKey        // top-level tab: batch entry + today's history
    @Serializable data object StockGlanceRoute : NavKey       // pushed detail (INVENTORY_FORMULAS only)
    fun PolymorphicModuleBuilder<NavKey>.productionRoutes()   // subclass(...) both
```
Build: plugins `kotlinMultiplatform`, `androidKmpLibrary`, `kotlinSerialization`.

### `:mobile:feature:production:impl`
```
feature/production/impl/src/commonMain/kotlin/com/f776/ecopan/feature/production/
  di/ProductionModule.kt          // val ProductionModule = module { includes(ProductionNavigationModule, ProductionDataModule, InventoryDataModule, SyncDataModule, CatalogDataModule); viewModelOf(::BatchEntryViewModel); viewModelOf(::StockGlanceViewModel) }
  navigation/ProductionNavigation.kt  // val ProductionNavigationModule = module { navigation<ProductionRoute> { BatchEntryScreenRoot(...) }; navigation<StockGlanceRoute>(metadata = SLIDE_HORIZONTALLY) { StockGlanceScreenRoot(...) } }
  screens/batchentry/  BatchEntryViewModel.kt / BatchEntryScreen.kt / BatchEntryAction.kt / BatchEntryEvent.kt / model/ (UI models)
  screens/stockglance/ StockGlanceViewModel.kt / StockGlanceScreen.kt / ...
```
Build: `kotlinMultiplatform`, `androidKmpLibrary`, `composeMultiplatform`, `kotlinSerialization`. Explicit `api(projects.core.ui)` + `api(projects.core.navigation)` (CLAUDE.md recipe step 5). Wire into `composeApp` `AppModule.includes(...)` + `AppNavigation` polymorphic block + `TopLevelKeys` (destination visible only when `session.role` includes production capability — 01 §1/§7).

### `:data:production:api` (package `com.f776.ecopan.domain.production`)
```
ProductionBatch          // id: Uuid, tenantId, branchId, bakerId, productId, productTypeName (denormalized snapshot), mixWeightGrams: Long, estimatedUnits: Int?, loggedAt: Instant, syncState (read-model, see §6)
LogBatch                 // command: productId, mixWeightGrams  (ids/timestamps minted in impl)
BatchLogResult           // sealed: Logged(batch, derivedMovements: List<DerivedIngredient>?), LoggedWithoutDecrement(batch, reason: NoFormula|ModuleDisabled)
DerivedIngredient        // ingredientId, ingredientName, grams: Long  (preview + result display)
ProductionRepository {
    fun todaysBatches(branchId): Flow<List<ProductionBatch>>
    fun batchReadyFeed(): Flow<ProductionBatch>              // local inserts + SSE-received remote batches (POS consumes this on desktop; see 05)
    suspend fun logBatch(cmd: LogBatch): BatchLogResult      // THE atomic write, §4
    suspend fun previewDerivation(productId, mixWeightGrams): List<DerivedIngredient>?  // null = no formula/module off
}
BatchSideEffects         // cut-line strategy interface, §5
```
Build: `kotlinMultiplatform` only.

### `:data:production:impl` (package `com.f776.ecopan.data.production`)
```
ProductionRepositoryImpl (internal, delegation shim per reference-data-layer)
local/ DbProductionDataSource (internal) — wraps ProductionBatchDao + transaction orchestration
local/mapper/ (internal ext fns entity↔domain)
domain/ FormulaDerivation.kt (internal pure object — the §3 integer math; no I/O, exhaustively testable)
sideeffects/ FormulaDecrementSideEffect / NoOpBatchSideEffects (internal)
di/ ProductionDataModule.kt — includes(DatabaseModule, InventoryDataModule, SyncDataModule, CatalogDataModule, TenantDataModule); binds BatchSideEffects per entitlement (§5)
```
No `PlatformModule` needed — zero platform code in this domain.

### `:data:inventory:api` (package `com.f776.ecopan.domain.inventory`)
```
Ingredient               // id, tenantId, name  (unit is always grams — 01 §5)
StockMovement            // id: Uuid, tenantId, branchId, ingredientId, deltaGrams: Long (signed), kind: PURCHASE|FORMULA_DECREMENT|MANUAL_ADJUSTMENT, sourceBatchId: Uuid?, recordedAt, recordedBy
StockLevel               // ingredientId, name, currentGrams: Long (derived = Σ deltas; may be negative — 01 §6.4)
InventoryRepository {
    fun stockLevels(branchId): Flow<List<StockLevel>>        // Room SUM query over movements
    fun movements(branchId, ingredientId): Flow<List<StockMovement>>
    suspend fun appendMovements(movements: List<StockMovement>)   // append-only; NO update/delete API exists — the interface itself enforces 01 §6.1
}
StockMovementTxWriter    // internal-ish seam for same-transaction appends from :data:production (see §4); exposed in api because production:impl must call it, but documented "do not use outside a DB transaction"
```
### `:data:inventory:impl`
`InventoryRepositoryImpl`, `DbInventoryDataSource`, mappers, `InventoryDataModule`. Manual adjustment writes (manager fallback) also flow through `appendMovements` with `kind = MANUAL_ADJUSTMENT` — surfaced in `:feature:inventory` (manager, see 01 §1 table) and the web dashboard raw-stock entry (07). This plan does not design those screens; it only guarantees the API they need exists.

### Room additions (`:core:database`, per CLAUDE.md recipe)
Entities `internal`: `ProductionBatchEntity`, `IngredientEntity`, `StockMovementEntity` (indices on `(tenant_id, branch_id)`, `source_batch_id`, `ingredient_id`), plus DAOs. Version bump + migration in `Migrations.kt` (`migrate` is non-suspend in Room 2.8.x KMP — verify via context7). Stock level = `SELECT ingredient_id, SUM(delta_grams) ... GROUP BY` DAO query returning a projection, exposed as Flow. Formula/product tables belong to `:data:catalog` (01 §2): `Product` carries `soldBy`, prices, `mixToLoafRatioMilli: Int?` (finished units per kg of mix, ×1000; null = no estimate), optional `minMixGrams`/`maxMixGrams`; `FormulaLine(productId, ingredientId, basisPoints: Int)` with flour = 10000 bp.

---

## 2. The single core flow

Baker's whole job: **product type → mix weight → confirm.** One screen, three taps plus digits.

1. Select product type from grid (products of the branch's catalog; cached read-only config, 01 §6.2).
2. Enter mix weight in **kg with decimal** on a huge keypad → converted once at parse time to `mixWeightGrams: Long` (`round(kg * 1000)`); grams are the only stored representation (01 §5).
3. Live preview: derived per-ingredient grams (§3) + estimated units (`mixGrams * mixToLoafRatioMilli / 1_000_000`, rounded — **informational, displayed, never ledgered**; snapshot stored on the batch row for history display only, per spec Inventory Semantics: finished goods are approximate and unreconciled).
4. Confirm → `ProductionRepository.logBatch` → §4 atomic write → success event, screen resets for next batch.

---

## 3. Formula derivation (restates 01 §5 — binding)

Formulas are baker's percentages stored as **basis points relative to flour** (`Int`; flour itself = 10000 bp). The baker logs *total dough* weight. Derivation, all integer math (`Long` intermediates):

```
totalBp   = Σ bpᵢ over all formula lines (including flour's 10000)
rawᵢ      = mixGrams * bpᵢ            // Long, no overflow at sane sizes (200 kg × 20000 bp « Long.MAX)
gramsᵢ    = rawᵢ / totalBp            // floor division
remᵢ      = rawᵢ % totalBp
deficit   = mixGrams − Σ gramsᵢ       // ∈ [0, n−1], guaranteed since Σ remᵢ = deficit * totalBp
```
Distribute `deficit` × 1 g to the ingredients with the **largest remainders**; tie-break deterministically by (remainder desc, ingredientId asc) so every device derives identical movements. Postcondition (asserted in code and tests): `Σ gramsᵢ == mixGrams` exactly.

**Worked example — 10 kg dough (`mixGrams = 10_000`), formula flour 10000 bp + water 6500 bp + salt 200 bp + yeast 100 bp → `totalBp = 16_800`:**

| Ingredient | bp | raw = 10000·bp | floor g | remainder |
|---|---:|---:|---:|---:|
| flour | 10000 | 100,000,000 | 5952 | 6400 |
| water | 6500 | 65,000,000 | 3869 | 800 |
| salt | 200 | 2,000,000 | 119 | 800 |
| yeast | 100 | 1,000,000 | 59 | **8800** |

Floor sum = 9999 → deficit = 1 g → largest remainder is yeast → yeast 59→60.
**Final: flour 5952 + water 3869 + salt 119 + yeast 60 = 10,000 g exactly.** (Remainder-sum check: 6400+800+800+8800 = 16,800 = 1 × totalBp ✓.)

Edge cases: single-line formula (flour only) → trivially exact; `totalBp ≤ 0` or any `bpᵢ ≤ 0` → formula treated as invalid = "no formula" path (§9); `mixGrams < n` lines → some lines legitimately get 0 g (still recorded as movements? No — drop zero-delta movements, they are ledger noise; postcondition then checked against the non-zero subset + dropped zeros).

Lives in `FormulaDerivation` (pure, internal, `:data:production:impl`) — the same object the preview and the commit call, so preview always equals what gets written.

---

## 4. The atomic write

`logBatch` performs **one Room transaction** on the shared `AppDatabase` (Room 2.8 KMP transaction API — implementer verifies exact call via context7):

Inside the transaction, in order:
- (a) insert `ProductionBatchEntity` (client-minted `Uuid` v4, 01 §3);
- (b) `BatchSideEffects.onBatchLogged(batch)` → default strategy derives §3 movements and appends `StockMovementEntity` rows (`kind = FORMULA_DECREMENT`, **negative** `deltaGrams`, `sourceBatchId = batch.id`) via `StockMovementTxWriter`;
- (c) nothing for finished goods — the estimate was computed for display and snapshotted on the batch row in (a); no ledger row exists for it;
- (d) outbox entries (per `03-sync.md`): one `BATCH_LOGGED` mutation + one `STOCK_MOVEMENTS_APPENDED` mutation (movement rows ride with it), enqueued via the `:data:sync:api` outbox writer **in the same transaction** — a batch can never exist locally without its outbox entry or vice versa.

All-or-nothing: process death mid-flow leaves zero partial state. Movements commute with concurrent POS/manager movements by construction (01 §6.1) — no conflict handling needed here at all.

**Double-decrement guard (now the codified policy — 01 §8):** 01 §8 makes client-side minting the primary path (mandatory: offline stock glance + preview must reflect them, and append-only deltas are the whole conflict story). On the wire (02 §4.1): the pushed `PRODUCTION_BATCH` payload carries `movementsMinted: Boolean`, and the minted movements are pushed as ordinary `STOCK_MOVEMENT` mutations with `kind = FORMULA_DECREMENT` and `sourceBatchId` set (server validates the batch reference). The worker's decrement processor runs **only** when `movementsMinted = false` (server-side fallback: formula existed on server but not in the client's stale cache, or a future thin client — 02 §6). Idempotent either way via client-minted movement UUIDs (01 §6.3) plus the server's `(source_batch_id, ingredient_id)` uniqueness.

---

## 5. Cut-line: `BatchSideEffects` (MUST remain amputable)

```
interface BatchSideEffects {                       // :data:production:api
    suspend fun onBatchLogged(batch, formula?, txScope): SideEffectOutcome
}
// bindings in ProductionDataModule:
//   INVENTORY_FORMULAS entitled → FormulaDecrementSideEffect
//   otherwise                   → NoOpBatchSideEffects (returns Skipped(ModuleDisabled))
// FormulaDecrementSideEffect with no formula for product → Skipped(NoFormula) — still success
```

Guarantees (spec: "do not build features that depend on exact auto-decrement"):
- Disabling `INVENTORY_FORMULAS`, or a product having no formula, **still records the batch, still enqueues `BATCH_LOGGED`, still emits batch-ready**. Only the movements are absent.
- Nothing downstream may assume movements exist: batch history, batch-ready SSE, POS display, and ML (rides on sales, not production) are all movement-free consumers. Stock glance simply shows whatever movements exist (purchases + manual adjustments only, if formulas are off).
- The fallback is **manual raw-stock adjustment** — a manager surface in `:feature:inventory` (mobile/desktop) and web raw-stock entry (see `05`/`07`), writing `MANUAL_ADJUSTMENT` movements through the same `InventoryRepository.appendMovements`. Amputating the strategy = deleting one binding; the entitlement toggle demo (11) shows exactly this.

Selection is entitlement-driven **at call time** (read `EntitlementsProvider` inside the repository, not at DI-graph build) so a config pull flipping the module mid-session takes effect without restart.

---

## 6. Batch-ready event flow + sync status

Per 01 §8, no new machinery:

```
logBatch tx commit
  └─ local: Room Flow emits → baker's own history list updates instantly (sync state: PENDING)
  └─ outbox → :data:sync push (when online) → backend:app persists (idempotent upsert by id)
        └─ publishes batch message → backend:worker (decrement fallback per §4) → completion
        └─ backend:notifier → SsePushBroker → GET /events/sse → online POS desktop + web
POS shows "batch ready: 10 kg Marraqueta — 14:32" (05 designs the surface; data arrives via :data:production batchReadyFeed on desktop too)
```

- **Per-row sync status on the baker's device:** `ProductionBatch.syncState ∈ {PENDING, SYNCED}` derived by joining batch id against outbox state (03's outbox exposes `Flow` of pending mutation ids; the DAO/read-model joins — no extra column to keep consistent). History rows render a status chip.
- **Decision — no push notifications, reminder module stays unused.** Justification: the consumer of "batch ready" is front-of-house *while working the counter* — the POS is open in front of them and shows it via SSE/local feed; a phone push adds a permission prompt, APNs/FCM infrastructure, and a second delivery path to test, for an audience already staring at the delivery surface. The baker needs no notification of their own action. `:data:reminder` stays in the repo unused (01 §2: "it costs nothing") — if a future tenant wants counter-staff phone alerts, `Reminder` is the ready-made hook. Scope closed.

---

## 7. Screens

All three live in `:mobile:feature:production:impl`; theming per `09`. Ergonomic constraints: flour-dusted/wet hands → minimum touch target 64 dp for keypad and product tiles, no text fields requiring the system keyboard, no long-press or swipe gestures on the critical path, high-contrast state colors.

### 7.1 Batch entry (`ProductionRoute` — the home screen for a baker session)

```
┌──────────────────────────────────────────────┐
│ ● Online · synced        Panadería El Sol ▾  │  ← SyncStatus indicator (REQUIRED, :data:sync
│                                              │     SyncStatus flow: Online/Offline/Syncing/
│  PRODUCT TYPE                                │     PendingCount) — shared component from 03
│  ┌─────────┐ ┌─────────┐ ┌─────────┐         │
│  │Marraque-│ │ Hallulla│ │ Integral│         │  ← product grid, 64dp+ tiles,
│  │   ta ✓  │ │         │ │         │         │     selected = filled
│  └─────────┘ └─────────┘ └─────────┘         │
│  ┌─────────┐ ┌─────────┐                     │
│  │ Baguette│ │  Dobla- │      ⚠ no formula   │  ← per-tile badge when product
│  └─────────┘ │   da ⚠  │                     │     lacks a formula (§9)
│              └─────────┘                     │
│  MIX WEIGHT                                  │
│           ┌───────────────┐                  │
│           │   10.0  kg    │                  │  ← display only, no system keyboard
│           └───────────────┘                  │
│   ┌─────┐ ┌─────┐ ┌─────┐                    │
│   │  7  │ │  8  │ │  9  │                    │
│   ├─────┤ ├─────┤ ├─────┤                    │  ← huge keypad, ≥64dp keys
│   │  4  │ │  5  │ │  6  │                    │
│   ├─────┤ ├─────┤ ├─────┤                    │
│   │  1  │ │  2  │ │  3  │                    │
│   ├─────┤ ├─────┤ ├─────┤                    │
│   │  .  │ │  0  │ │  ⌫  │                    │
│   └─────┘ └─────┘ └─────┘                    │
│  ┌──────────────────────────────────────┐    │
│  │ Will use:  flour 5.952 kg · water    │    │  ← live derived-ingredients preview
│  │ 3.869 kg · salt 119 g · yeast 60 g   │    │     (previewDerivation; hidden when
│  │ ≈ 22 loaves                          │    │     no formula / module off)
│  └──────────────────────────────────────┘    │
│  ┌──────────────────────────────────────┐    │
│  │        ✓  LOG BATCH  (10.0 kg)       │    │  ← full-width, 72dp confirm
│  └──────────────────────────────────────┘    │
│  ── Today ─────────────────────────────────  │
│  14:32  Marraqueta 10.0 kg     [synced ✓]    │  ← today's batches inline below
│  13:05  Hallulla    8.5 kg     [pending ⏳]  │     (compact); status chips
│  Stock ▸                                     │  ← nav to StockGlanceRoute (entitled only)
└──────────────────────────────────────────────┘
```

Confirm shows a 1-tap confirmation state on the button itself (button morphs to "Tap again to confirm 10.0 kg Marraqueta", 3 s timeout) rather than a dialog — fewer targets, faster loop. After commit: success flash + keypad clears, selection persists (bakers batch the same product repeatedly).

### 7.2 Batch history
Inline "Today" section above (compact phone layout); on expanded window classes (tablet in the bakehouse) it becomes a side pane (adaptive per template `currentWindowAdaptiveInfo`). Rows: time, product, kg, estimated units, status chip `pending-sync` (amber) / `synced` (green). Data: `todaysBatches` joined with outbox pending-ids. No pull-to-refresh — Room Flows are live.

### 7.3 Stock glance (`StockGlanceRoute`)
Visible only when `INVENTORY_FORMULAS` entitled (nav filter, 01 §7.1). Read-only list of `StockLevel`: ingredient, current kg (derived), red highlight + "needs reconciliation" tag when negative (01 §6.4 — surfaced, not blocked). No edit affordance — manual adjustment is the manager's surface (`:feature:inventory` / web). Same SyncStatus header.

---

## 8. ViewModel sketch (template UDF — Action/State/Event, Channel)

```kotlin
// BatchEntryViewModel(productionRepository, catalogRepository, entitlementsProvider,
//                     syncStatusProvider, defaultDispatcher, savedStateHandle)

internal sealed interface BatchEntryAction {
    data class OnSelectProduct(val productId: Uuid) : BatchEntryAction
    data class OnKeypadDigit(val digit: Char) : BatchEntryAction   // '0'..'9', '.'
    data object OnKeypadBackspace : BatchEntryAction
    data object OnConfirmRequested : BatchEntryAction              // arms 2-tap confirm
    data object OnConfirm : BatchEntryAction                       // -> logBatch
    data object OnOpenStockGlance : BatchEntryAction
}

// State: single @Stable data class via MutableStateFlow (+ savedStateHandle for entry text)
internal data class BatchEntryState(
    val products: List<ProductTileUi>,          // incl. hasFormula flag for ⚠ badge
    val selectedProductId: Uuid?,
    val weightInput: String,                    // raw "10.0"; parsed+validated on change
    val mixWeightGrams: Long?,                  // null = unparseable/empty
    val validation: WeightValidation,           // Ok | Empty | OutOfSoftBounds(min,max) | Invalid
    val derivedPreview: List<DerivedIngredientUi>?,  // null = no formula / module off
    val estimatedUnits: Int?,
    val confirmArmed: Boolean,
    val todaysBatches: List<BatchRowUi>,        // with SyncChip per row
    val syncStatus: SyncStatusUi,               // from :data:sync SyncStatus flow
    val stockGlanceEnabled: Boolean,
)

internal sealed interface BatchEntryEvent {
    data class BatchLogged(val summary: String) : BatchEntryEvent          // success flash
    data class BatchLoggedNoDecrement(val reason: String) : BatchEntryEvent // warn snackbar (§9)
    data class ShowError(val message: String) : BatchEntryEvent
    data object NavigateToStockGlance : BatchEntryEvent
}
// Channel<BatchEntryEvent>(BUFFERED) + receiveAsFlow(); Root observes via ObserveAsEvents.
// Screen composable takes (state, onAction) only — no ViewModel reference (skill guardrail).
// derivedPreview recomputed via combine(selectedProduct, mixGrams) → previewDerivation on defaultDispatcher.
```

`StockGlanceViewModel`: trivial — `stateIn` over `inventoryRepository.stockLevels(branch)` mapped to UI models; no actions beyond back.

---

## 9. Edge cases (decisions)

| Case | Decision |
|---|---|
| **Product has no formula** (or invalid bp) | Batch fully allowed — cut-line §5. Tile shows ⚠ badge, preview hidden, confirm proceeds, post-commit snackbar "Batch logged — stock not decremented (no formula)". `BatchLogResult.LoggedWithoutDecrement` drives it. Never a blocker. |
| **Zero / absurd mix weight** | Hard-invalid: `≤ 0 g`, unparseable, or `> 200 kg` (absolute cap — no home mixer exceeds it; constant, not config). Soft bounds: `Product.minMixGrams/maxMixGrams` if set, else tenant defaults `[500 g, 100 kg]` (rows, not code — 00 convention 5). Outside soft bounds → confirm button demands the 2-tap arm with explicit "10.0 kg is unusually large — tap again" copy. Bounds live in `:data:catalog` config. |
| **Ingredient stock would go negative** | Movement applied unchanged (01 §6.4 — the bread physically exists; rejecting blocks offline work). Negative level surfaced in stock glance + dashboard reconciliation warning. No client-side check at all — not even a warning at log time (the baker can't act on it mid-mix and it would train them to ignore dialogs). |
| **Ingredient missing from local cache** (formula references unknown id) | Treat as "no formula" for safety? No — formula and ingredients sync in the same config pull (03); a formula line referencing an ingredient absent from cache means a partial pull → treat whole formula as invalid → `LoggedWithoutDecrement(NoFormula)`. Never write a movement against an id we can't display. |
| **Edit / delete a batch** | **Scoped out for v1 — decided.** Rationale: the ledger is append-only (01 §6.1); a true void needs a `BATCH_VOIDED` event + compensating `+delta` movements, and must handle the batch having already synced, SSE-fanned to POS, and been counted — a distributed-retraction problem worth days, for an error mode (typo'd weight) whose existing remedy is the manager's manual adjustment surface (§5 fallback) plus logging a corrective batch. UI offers no edit/delete affordance; the 2-tap confirm + preview is the mistake-prevention budget. If reinstated later: compensating-event design (void allowed ≤ 15 min, same device, emits `BATCH_VOIDED` + inverse movements through the same outbox) is the sanctioned shape — noted in Open questions, not planned. |
| **Rapid double-tap on confirm** | Confirm disabled while `logBatch` in flight; idempotency ultimately guaranteed by client-minted UUID upsert anyway (01 §6.3). |
| **Clock skew** | `loggedAt` is wall-clock for domain meaning only; ordering is `serverSeq` (01 §3). Nothing to do. |

---

## 10. iOS parity

All of §§1–9 is `commonMain`. Platform touchpoints, each already patterned in the template — **zero new actuals required by this plan**:

| Touchpoint | Template precedent |
|---|---|
| Room driver (BundledSQLiteDriver iOS) | `:core:database` `DatabaseModule.ios.kt` — exists |
| DataStore file path | `:core:datastore` platform actuals — exists |
| Connectivity monitor for SyncStatus | owned by `:data:sync` (03); its `expect val PlatformModule` follows `data/reminder/impl` `ReminderDataModule.{android,ios,jvm}.kt` shape — 03's deliverable, not this plan's |
| Push notifications | none — decision §6; `UNUserNotificationCenter` actuals in `:data:reminder` stay dormant |
| Haptic on confirm (optional polish) | skip or expect-fun-with-Unit-stub per reference-platform — optional, not on critical path |

`:mobile:feature:production:impl` and both `:data:*:impl` pairs need no `iosMain` source set beyond what convention plugins generate. Acceptance: `./gradlew :mobile:feature:production:impl:compileKotlinIosArm64` (+ SimulatorArm64) green with no `actual` declarations in the module.

---

## 11. Testing (per 01 §10)

**Formula math — `:data:production:impl` jvmTest, pure unit + property-style** (defense material, name accordingly):
- `derivation sums exactly to mixGrams for worked example 10kg 16800bp` (asserts 5952/3869/119/60)
- `derivation sums exactly for random formulas` — property loop: random n∈[1,12] lines, bp∈[1,15000], mix∈[1, 200_000] → assert `Σ == mix`, all ≥ 0
- `largest remainder correction is deterministic under tie` (two equal remainders → ingredientId order)
- `flour-only formula returns mix intact`
- `zero-gram lines are dropped and sum still exact` (tiny mix, many lines)
- `invalid bp yields no derivation` (0, negative, empty formula)

**Repository / atomic write — jvmTest with in-memory Room or fake DAOs:**
- `logBatch writes batch, movements, and outbox entries or nothing` (failure injected in side effect → assert no batch row)
- `logBatch with entitlement off writes batch and outbox but zero movements`
- `logBatch with missing formula returns LoggedWithoutDecrement and still enqueues BATCH_LOGGED`
- `movement deltas are negative and reference sourceBatchId`

**ViewModel — commonTest/jvmTest with fakes** (`FakeProductionRepository`, `FakeCatalogRepository`, `FakeEntitlementsProvider`, `FakeSyncStatusProvider` — `FakeNoteRepository` style):
- `confirm emits BatchLogged and clears weight input`
- `no-formula product hides preview and emits BatchLoggedNoDecrement`
- `weight outside soft bounds requires armed confirm`
- `hard-invalid weight disables confirm`
- `pending outbox ids render pending-sync chips`
- `stock glance action gated by entitlement`

**Inventory:** `stock level is sum of movements including negatives`, `appendMovements is append-only (no mutation API compiles — interface review, not a test)`.

Commands: `./gradlew :data:production:impl:jvmTest :data:inventory:impl:jvmTest :mobile:feature:production:impl:testAndroidHostTest` + compile tasks per CLAUDE.md (root `test` does not reach KMP `jvmTest`).

---

## 12. Work items (ordered)

1. **Catalog prerequisites** (coordinates with 01 Phase 1 agent): `FormulaLine` + `Product.mixToLoafRatioMilli/minMixGrams/maxMixGrams` in `:data:catalog:{api,impl}` + Room entities/migration in `core/database/`. ✅ formula readable as `Flow<Formula?>` by productId.
2. **`:data:inventory:{api,impl}`** — `data/inventory/`, entities/DAO in `core/database/`, derived-level SUM query. ✅ jvmTest: level = Σ signed movements; negative allowed; no update/delete on the interface.
3. **`:data:production:api`** — models + `ProductionRepository` + `BatchSideEffects`. ✅ compiles all targets, zero deps on impls.
4. **`FormulaDerivation`** in `:data:production:impl`. ✅ all §11 math tests green, worked example asserted literally.
5. **Atomic `logBatch`** — transaction + side-effect strategy + outbox enqueue (needs 03's outbox writer API; stub behind an interface if 03 lands later). ✅ atomicity tests green; entitlement-off path writes no movements.
6. **`:mobile:feature:production:api`** routes + `productionRoutes()`. ✅ registered in `composeApp` `AppNavigation` polymorphic block.
7. **Batch entry screen + ViewModel** — keypad, product grid, preview, 2-tap confirm, SyncStatus header (component from 03). ✅ ViewModel tests green; `@LocalizedPreview`/`@AdaptivePreview` present; every touch target ≥ 64 dp.
8. **Batch history + status chips** — outbox-join read model. ✅ airplane-mode manual test: log batch offline → chip `pending`; reconnect → chip flips `synced` with no user action.
9. **Stock glance screen** — entitlement-gated route. ✅ toggling `INVENTORY_FORMULAS` in the tenant row hides/shows it with zero code change.
10. **Backend alignment** (with 02 agent): `movementsMinted` flag on batch push payload; worker decrement processor gated on it; batch-ready SSE event shape. ✅ **end-to-end acceptance: batch logged in airplane mode appears in POS front-of-house feed after both devices reconnect**, exactly once (idempotent re-push test: kill app after push before ACK, relaunch, no duplicate).
11. **iOS build gate** — compile + smoke on simulator. ✅ `compileKotlinIosArm64`/`IosSimulatorArm64` green, no new actuals.
12. **Demo hooks** (feeds 11-seeds): second-tenant profile with `INVENTORY_FORMULAS` off exercises §5 cut-line live. ✅ batch logging fully functional, no movements written, no errors.

## Open questions

1. **01 §8 vs client-side minting (§4): RESOLVED.** 01 §8 and 02 now codify this plan's policy: formula decrement is computed **client-primary** — the baker device mints the negative `FORMULA_DECREMENT` movements in the batch's local transaction and pushes them as `STOCK_MOVEMENT` mutations with `sourceBatchId` set (02 §4.1); the batch payload carries `movementsMinted: Boolean` (column `production_batches.movements_minted`); the worker's `FormulaDecrementProcessor` is the **fallback only**, short-circuiting to APPLIED when `movements_minted = true` (02 §6). 03 is aligned. Nothing left to sign off.
2. Outbox writer API surface (same-transaction enqueue) is assumed from 03 — confirm 03 exposes a transactional enqueue rather than post-commit.
3. `StockMovementTxWriter` (same-tx seam between production:impl and inventory:impl) — exact shape depends on Room 2.8 KMP transaction scoping; implementer verifies via context7 and may fold it into a shared internal in `:core:database` if cross-module tx proves awkward.
4. Tenant-default soft bounds (`500 g`–`100 kg`) — confirm with reference-client reality; stored as tenant config rows either way.
5. Batch void: scoped out (§9). If the committee asks, the compensating-event design sketch in §9 is the answer; reinstating it is a new work item against 02/03/05, not a tweak here.
6. Does the desktop POS consume `batchReadyFeed` from `:data:production:api` directly (05's call) or via a dedicated lighter feed? Default: same module — it's the point of one shared app.
