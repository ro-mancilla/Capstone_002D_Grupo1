package com.f776.ecopan.feature.production.unittest

import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.core.domain.formula.estimateLoaves
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncError
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.ecopan.domain.catalog.Formula
import com.f776.ecopan.domain.catalog.FormulaLine
import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.production.BatchSyncState
import com.f776.ecopan.domain.production.DerivedIngredient
import com.f776.ecopan.domain.production.NoDecrementReason
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.feature.production.screens.batchentry.BatchEntryAction
import com.f776.ecopan.feature.production.screens.batchentry.BatchEntryEvent
import com.f776.ecopan.feature.production.screens.batchentry.BatchEntryViewModel
import com.f776.ecopan.feature.production.screens.batchentry.model.WeightValidation
import com.f776.ecopan.feature.production.unittest.fake.FakeEntitlementsProvider
import com.f776.ecopan.feature.production.unittest.fake.FakeFormulaRepository
import com.f776.ecopan.feature.production.unittest.fake.FakeProductRepository
import com.f776.ecopan.feature.production.unittest.fake.FakeProductionRepository
import com.f776.ecopan.feature.production.unittest.fake.FakeShellContext
import com.f776.ecopan.feature.production.unittest.fake.FakeSuggestedProductionRepository
import com.f776.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class BatchEntryViewModelTest {

    private lateinit var productRepository: FakeProductRepository
    private lateinit var formulaRepository: FakeFormulaRepository
    private lateinit var productionRepository: FakeProductionRepository
    private lateinit var entitlementsProvider: FakeEntitlementsProvider
    private lateinit var shellContext: FakeShellContext
    private lateinit var viewModel: BatchEntryViewModel

    private val branchId = Uuid.random()
    private val bakerId = Uuid.random()
    private val marraquetaId = Uuid.random()
    private val doobladaId = Uuid.random()

    private val marraqueta = Product(
        id = marraquetaId, tenantId = Uuid.random(), name = "Marraqueta", productType = "Pan",
        soldBy = SoldBy.WEIGHT, pricePerKgMinor = 2000, priceMinor = null, barcode = null, imageUrl = null, active = true,
        mixToLoafRatioMilli = 2200, minMixGrams = 500, maxMixGrams = 100_000,
    )
    private val doblada = Product(
        id = doobladaId, tenantId = Uuid.random(), name = "Doblada", productType = "Pan",
        soldBy = SoldBy.WEIGHT, pricePerKgMinor = 2200, priceMinor = null, barcode = null, imageUrl = null, active = true,
        mixToLoafRatioMilli = null, minMixGrams = null, maxMixGrams = null,
    )

    private val testDispatcher = MainDispatcherRule.createTestDispatcher()

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        productRepository = FakeProductRepository().apply { products.value = listOf(marraqueta, doblada) }
        formulaRepository = FakeFormulaRepository()
        productionRepository = FakeProductionRepository()
        entitlementsProvider = FakeEntitlementsProvider()
        shellContext = FakeShellContext(branchId = branchId, userId = bakerId)
        viewModel = BatchEntryViewModel(
            productionRepository = productionRepository,
            productRepository = productRepository,
            formulaRepository = formulaRepository,
            entitlementsProvider = entitlementsProvider,
            shellContext = shellContext,
            suggestedProductionRepository = FakeSuggestedProductionRepository(),
        )
    }

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    @Test
    fun `the picker lists only Panaderia products`() = runTest {
        val cola = marraqueta.copy(
            id = Uuid.random(), name = "Bebida cola", soldBy = SoldBy.UNIT, pricePerKgMinor = null, priceMinor = 1500,
            origin = ProductOrigin.ALMACEN,
        )
        productRepository.products.value = listOf(marraqueta, cola, doblada)

        val state = viewModel.uiState.first { it.products.isNotEmpty() }
        assertEquals(setOf(marraquetaId, doobladaId), state.products.map { it.id }.toSet())

        // A product reclassified to Almacén by a later pull leaves the picker.
        productRepository.products.value = listOf(marraqueta.copy(origin = ProductOrigin.ALMACEN), doblada)
        advanceUntilIdle()
        assertEquals(listOf(doobladaId), viewModel.uiState.first().products.map { it.id })
    }

    @Test
    fun `product with no formula shows the warning badge`() = runTest {
        formulaRepository.formulasByProduct.value = emptyMap()
        val state = viewModel.uiState.first { it.products.isNotEmpty() }
        val badge = state.products.first { it.id == doobladaId }
        assertTrue(badge.hasFormula.not())
    }

    @Test
    fun `hard-invalid weight disables confirm and hides preview`() = runTest {
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        viewModel.onAction(BatchEntryAction.OnKeypadDigit('0'))
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(WeightValidation.HARD_INVALID, state.validation)
        assertNull(state.derivedPreview)
    }

    @Test
    fun `weight outside soft bounds requires armed confirm before logging`() = runTest {
        productionRepository.previews = mapOf(marraquetaId to listOf(DerivedIngredient(Uuid.random(), "Harina", 100)))
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "150".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()

        var state = viewModel.uiState.first()
        assertEquals(WeightValidation.OUT_OF_SOFT_BOUNDS, state.validation)

        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()
        state = viewModel.uiState.first()
        assertTrue(state.confirmArmed)
        assertTrue(productionRepository.loggedCommands.isEmpty())

        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()
        assertEquals(1, productionRepository.loggedCommands.size)
    }

    @Test
    fun `a 10 kg preview shows the exact grams and its lines add up to the batch weight`() = runTest {
        productionRepository.previews = mapOf(
            marraquetaId to listOf(
                DerivedIngredient(Uuid.random(), "Harina", 5_952),
                DerivedIngredient(Uuid.random(), "Agua", 3_869),
                DerivedIngredient(Uuid.random(), "Sal", 119),
                DerivedIngredient(Uuid.random(), "Levadura", 60),
            )
        )
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()

        val labels = viewModel.uiState.first().derivedPreview.orEmpty().map { it.label }
        assertEquals(listOf("5,952 kg", "3,869 kg", "119 g", "60 g"), labels)
        val total = labels.sumOf { label ->
            if (label.endsWith(" kg")) label.removeSuffix(" kg").replace(".", "").replace(",", "").toLong()
            else label.removeSuffix(" g").toLong()
        }
        assertEquals(10_000L, total)
    }

    @Test
    fun `confirm logs the batch and clears the weight input`() = runTest {
        productionRepository.previews = mapOf(marraquetaId to listOf(DerivedIngredient(Uuid.random(), "Harina", 5952)))
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()

        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()

        assertEquals(1, productionRepository.loggedCommands.size)
        val cmd = productionRepository.loggedCommands.single()
        assertEquals(branchId, cmd.branchId)
        assertEquals(bakerId, cmd.bakerId)
        assertEquals(10_000L, cmd.mixWeightGrams)

        val state = viewModel.uiState.first()
        assertEquals("", state.weightInput)
        assertNull(state.mixWeightGrams)
        assertNull(state.derivedPreview)
    }

    @Test
    fun `no-formula product hides preview and reports no decrement`() = runTest {
        productionRepository.noDecrementReasons = mapOf(doobladaId to NoDecrementReason.NO_FORMULA)
        viewModel.onAction(BatchEntryAction.OnSelectProduct(doobladaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()

        assertNull(viewModel.uiState.first().derivedPreview)

        val eventDeferred = async { viewModel.eventChannel.first() }
        viewModel.onAction(BatchEntryAction.OnConfirmRequested)

        assertTrue(eventDeferred.await() is BatchEntryEvent.BatchLoggedNoDecrement)
    }

    @Test
    fun `today's batches render with their sync state`() = runTest {
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()
        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(1, state.todaysBatches.size)
        assertEquals(BatchSyncState.PENDING, state.todaysBatches.single().syncState)
    }

    /**
     * `ProductionBatch.productTypeName` snapshots the *category* ("Pan"), so three different
     * breads all rendered as one indistinguishable row. The ledger resolves the real name from
     * the catalog instead.
     */
    @Test
    fun `history rows show the product name not its category`() = runTest {
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()
        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()

        assertEquals("Marraqueta", viewModel.uiState.first().todaysBatches.single().productName)
    }

    /** A product dropped from the catalog still has to render: that is what the snapshot is for. */
    @Test
    fun `a batch whose product left the catalog falls back to the stored snapshot`() = runTest {
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()
        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()

        productRepository.products.value = emptyList()
        advanceUntilIdle()

        // "Product" is what FakeProductionRepository writes into productTypeName — the point is
        // that the row falls back to the stored snapshot rather than going blank.
        assertEquals("Product", viewModel.uiState.first().todaysBatches.single().productName)
    }

    /** The "Hoy" heading carries a running figure, so the sum has to be state, not a label. */
    @Test
    fun `today's total sums the day's mixes`() = runTest {
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()
        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()

        "5".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()
        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(2, state.todaysBatches.size)
        assertEquals(15_000L, state.todaysTotalGrams)
    }

    /**
     * The banner has to name what left stock, and the preview is cleared on confirm — so a
     * regression here would silently produce a confirmation with an empty ingredient list.
     */
    @Test
    fun `confirming leaves an inline receipt naming what was decremented`() = runTest {
        productionRepository.previews =
            mapOf(marraquetaId to listOf(DerivedIngredient(Uuid.random(), "Harina", 5952)))
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()
        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()

        val logged = viewModel.uiState.first().lastLogged
        assertEquals("Marraqueta", logged?.productName)
        assertEquals("10,000 kg", logged?.kgLabel)
        assertEquals(listOf("Harina"), logged?.decremented?.map { it.name })

        viewModel.onAction(BatchEntryAction.OnDismissLogged)
        assertNull(viewModel.uiState.first().lastLogged)
    }

    @Test
    fun `a no-formula batch reports a receipt with nothing decremented`() = runTest {
        productionRepository.noDecrementReasons = mapOf(doobladaId to NoDecrementReason.NO_FORMULA)
        viewModel.onAction(BatchEntryAction.OnSelectProduct(doobladaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()
        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.first { it.lastLogged != null }.lastLogged!!.decremented.isEmpty())
    }

    /** The picker's hint reads "· 4 materias primas", which needs the line count, not just a flag. */
    @Test
    fun `formula line count reaches the product row`() = runTest {
        val flour = Uuid.random()
        formulaRepository.formulasByProduct.value = mapOf(
            marraquetaId to Formula(
                id = Uuid.random(),
                tenantId = Uuid.random(),
                productId = marraquetaId,
                active = true,
                lines = listOf(FormulaLine(flour, 10_000), FormulaLine(Uuid.random(), 3_800)),
            ),
        )
        val state = viewModel.uiState.first { it.products.any { p -> p.materialCount > 0 } }
        val row = state.products.first { it.id == marraquetaId }
        assertTrue(row.hasFormula)
        assertEquals(2, row.materialCount)
    }

    @Test
    fun `stock glance is gated by the INVENTORY_FORMULAS entitlement`() = runTest {
        entitlementsProvider.enabled.value = emptySet()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.first().stockGlanceEnabled.not())

        entitlementsProvider.enabled.value = setOf(com.f776.ecopan.core.domain.tenant.EntitlementModule.INVENTORY_FORMULAS)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.first().stockGlanceEnabled)
    }

    /**
     * 03 §6.1: the indicator must be live on the baker's logging screen. What each `SyncStatus`
     * maps to is `ShellContextImplTest`'s business now -- this only proves the screen is plumbed
     * to it, which is the part that could break here.
     */
    @Test
    fun `the shell's indicator drives the always-visible one`() = runTest {
        advanceUntilIdle()
        assertEquals(SyncIndicatorState.OnlineIdle, viewModel.uiState.first().syncIndicator)

        shellContext.setIndicator(SyncIndicatorState.OfflinePending(4))
        advanceUntilIdle()
        assertEquals(SyncIndicatorState.OfflinePending(4), viewModel.uiState.first().syncIndicator)

        shellContext.setIndicator(SyncIndicatorState.Attention(1))
        advanceUntilIdle()
        assertEquals(SyncIndicatorState.Attention(1), viewModel.uiState.first().syncIndicator)
    }

    @Test
    fun `tapping the indicator triggers a manual sync`() = runTest {
        advanceUntilIdle()
        assertEquals(0, shellContext.syncNowCalls)

        viewModel.onAction(BatchEntryAction.OnSyncIndicatorTapped)
        advanceUntilIdle()

        assertEquals(1, shellContext.syncNowCalls)
    }

    @Test
    fun `resuming the app pulls server changes with a sync`() = runTest {
        advanceUntilIdle()
        assertEquals(0, shellContext.syncNowCalls)

        viewModel.onAction(BatchEntryAction.OnResumed)
        advanceUntilIdle()

        assertEquals(1, shellContext.syncNowCalls)
    }

    @Test
    fun `the estimate shown is the one the shared rule produces not a truncated copy`() = runTest {
        // 12.5 kg at ratio 2200 is 27.5 loaves. The keypad rounded while the logged row and the
        // pull applier truncated, so the baker approved 28 and the database kept 27.
        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "12.5".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(12_500L, state.mixWeightGrams)
        assertEquals(estimateLoaves(12_500L, marraqueta.mixToLoafRatioMilli), state.estimatedUnits)
        assertEquals(28, state.estimatedUnits)
    }

    @Test
    fun `a product with no ratio shows no estimate`() = runTest {
        viewModel.onAction(BatchEntryAction.OnSelectProduct(doobladaId))
        "10".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()

        assertNull(viewModel.uiState.first().estimatedUnits)
    }

    @Test
    fun `the confirmation reports what the write recorded not the preview it raced`() = runTest {
        val harina = Uuid.random()
        // The preview still holds the result for an earlier weight -- `recomputePreview` relaunches
        // on every keystroke and does not clear the old value while the new one is in flight, so a
        // baker who taps Registrar right after the last digit used to get a receipt for the
        // previous mix.
        productionRepository.previews = mapOf(marraquetaId to listOf(DerivedIngredient(harina, "Harina", 2_500)))
        productionRepository.loggedDerivations = mapOf(marraquetaId to listOf(DerivedIngredient(harina, "Harina", 25_000)))

        viewModel.onAction(BatchEntryAction.OnSelectProduct(marraquetaId))
        "25".forEach { viewModel.onAction(BatchEntryAction.OnKeypadDigit(it)) }
        advanceUntilIdle()
        viewModel.onAction(BatchEntryAction.OnConfirmRequested)
        advanceUntilIdle()

        val logged = viewModel.uiState.first().lastLogged
        assertEquals(1, logged?.decremented?.size)
        assertEquals("25,000 kg", logged?.decremented?.first()?.label)
        // The stale preview would have said "2,500 kg" -- the amounts for the previous weight.
        assertTrue(logged?.decremented?.none { it.label == "2,500 kg" } == true)
    }
}
