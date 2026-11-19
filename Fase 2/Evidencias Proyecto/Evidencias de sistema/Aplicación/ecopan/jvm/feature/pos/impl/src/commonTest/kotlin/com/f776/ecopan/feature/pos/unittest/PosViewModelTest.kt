@file:OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)

package com.f776.ecopan.feature.pos.unittest

import com.f776.ecopan.domain.hardware.PrintResult
import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.feature.pos.screens.pos.StockBadgeUi
import com.f776.ecopan.feature.pos.screens.pos.StockLevel
import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.hardware.PaymentResult
import com.f776.ecopan.domain.production.BatchSyncState
import com.f776.ecopan.domain.production.BatchReadyAnnouncement
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.feature.pos.screens.pos.ALL_CATEGORY
import com.f776.ecopan.feature.pos.screens.pos.CardAuthPhase
import com.f776.ecopan.domain.hardware.DeviceInterface
import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.feature.pos.screens.pos.DrawerNote
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.ReceiptLayout
import com.f776.ecopan.feature.pos.screens.pos.PosAction
import com.f776.ecopan.feature.pos.screens.pos.PosEvent
import com.f776.ecopan.feature.pos.screens.pos.PosViewModel
import com.f776.ecopan.feature.pos.screens.pos.ScaleMode
import com.f776.ecopan.feature.pos.screens.pos.WeighSource
import com.f776.ecopan.feature.pos.unittest.fake.FakeDriverRegistry
import com.f776.ecopan.feature.pos.unittest.fake.FakeProductRepository
import com.f776.ecopan.feature.pos.unittest.fake.FakeProductionRepository
import com.f776.ecopan.feature.pos.unittest.fake.FakeSaleRepository
import com.f776.ecopan.feature.pos.unittest.fake.FakeShellContext
import com.f776.ecopan.feature.pos.unittest.fake.FakeSyncEngine
import com.f776.ecopan.feature.pos.unittest.fake.FakeAuthRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PosViewModelTest {

    private lateinit var productRepository: FakeProductRepository
    private lateinit var driverRegistry: FakeDriverRegistry
    private lateinit var saleRepository: FakeSaleRepository
    private val authRepository = FakeAuthRepository()
    private lateinit var shellContext: FakeShellContext
    private lateinit var productionRepository: FakeProductionRepository
    private lateinit var syncEngine: FakeSyncEngine
    private lateinit var viewModel: PosViewModel
    private val testDispatcher = MainDispatcherRule.createTestDispatcher()

    private val branchId = Uuid.random()
    private val cashierId = Uuid.random()
    private val bread = product(name = "Marraqueta", productType = "Panes", soldBy = SoldBy.WEIGHT, pricePerKgMinor = 2290)
    private val empanada = product(name = "Empanada de Pino", productType = "Salado", soldBy = SoldBy.UNIT, priceMinor = 2800)
    private val barcodedEmpanada = product(
        name = "Empanada Queso",
        productType = "Salado",
        soldBy = SoldBy.UNIT,
        priceMinor = 1500,
        barcode = "7801234500019",
    )
    private val barcodedBread = product(
        name = "Hallulla",
        productType = "Panes",
        soldBy = SoldBy.WEIGHT,
        pricePerKgMinor = 1150,
        barcode = "7801234500026",
    )

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        productRepository = FakeProductRepository(listOf(bread, empanada, barcodedEmpanada, barcodedBread))
        driverRegistry = FakeDriverRegistry(initialScaleDriverId = "mock")
        saleRepository = FakeSaleRepository()
        shellContext = FakeShellContext(branchId = branchId, userId = cashierId, tenantSlug = "reference", branchName = "Centro")
        productionRepository = FakeProductionRepository()
        syncEngine = FakeSyncEngine()
        viewModel = newViewModel()
    }

    private fun newViewModel() = PosViewModel(
        productRepository = productRepository,
        driverRegistry = driverRegistry,
        saleRepository = saleRepository,
        authRepository = authRepository,
        shellContext = shellContext,
        productionRepository = productionRepository,
        syncEngine = syncEngine,
    )

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    @Test
    fun `products load and categories are derived plus All`() = runTest {
        val state = viewModel.uiState.first { !it.loading }
        assertEquals(listOf(ALL_CATEGORY, "Panes", "Salado"), state.categories.map { it.name })
        assertEquals(4, state.products.size)
    }

    @Test
    fun `category counts cover every active product and All counts them all`() = runTest {
        val state = viewModel.uiState.first { !it.loading }
        assertEquals(4, state.categories.single { it.name == ALL_CATEGORY }.count)
        assertEquals(4, state.categories.filterNot { it.name == ALL_CATEGORY }.sumOf { it.count })
    }

    @Test
    fun `search filters products by name case-insensitively`() = runTest {
        viewModel.uiState.first { !it.loading }

        // Mixed case on purpose: the cashier types fast and lowercase.
        viewModel.onAction(PosAction.OnSearchQueryChanged("pINo"))
        advanceUntilIdle()
        assertEquals(listOf(empanada.id), viewModel.uiState.first().products.map { it.id })

        // A broader stem still matches both empanadas, so the filter is not accidentally exact-match.
        viewModel.onAction(PosAction.OnSearchQueryChanged("empanada"))
        advanceUntilIdle()
        assertEquals(setOf(empanada.id, barcodedEmpanada.id), viewModel.uiState.first().products.map { it.id }.toSet())

        viewModel.onAction(PosAction.OnSearchQueryChanged(""))
        advanceUntilIdle()
        assertEquals(4, viewModel.uiState.first().products.size)
    }

    @Test
    fun `search and category filters compose`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnCategorySelected("Panes"))
        viewModel.onAction(PosAction.OnSearchQueryChanged("empanada"))
        advanceUntilIdle()
        assertEquals(emptyList(), viewModel.uiState.first().products.map { it.id })
    }

    @Test
    fun `stepping a unit line down to zero removes it`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        advanceUntilIdle()
        val lineId = viewModel.uiState.first().cart.lines.single().lineId

        viewModel.onAction(PosAction.OnStepLineQuantity(lineId, 1))
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.first().cart.lines.single().quantity)

        viewModel.onAction(PosAction.OnStepLineQuantity(lineId, -1))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.first().cart.lines.single().quantity)

        viewModel.onAction(PosAction.OnStepLineQuantity(lineId, -1))
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(emptyList(), state.cart.lines)
        assertEquals(0L, state.cart.totalMinor)
    }

    @Test
    fun `discarding a sale clears the cart and any open weigh panel`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        advanceUntilIdle()

        viewModel.onAction(PosAction.OnDiscardSale)
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertEquals(emptyList(), state.cart.lines)
        assertEquals(0L, state.cart.totalMinor)
        assertEquals(null, state.weighPanel)
    }

    @Test
    fun `tapping a unit product adds a line tapping again increments it`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        advanceUntilIdle()
        var state = viewModel.uiState.first()
        assertEquals(1, state.cart.lines.single().quantity)
        assertEquals(2800L, state.cart.totalMinor)

        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        advanceUntilIdle()
        state = viewModel.uiState.first()
        assertEquals(1, state.cart.lines.size)
        assertEquals(2, state.cart.lines.single().quantity)
        assertEquals(5600L, state.cart.totalMinor)
    }

    @Test
    fun `tapping a weight product opens a live weigh panel that adds once stable`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()

        driverRegistry.fakeScale.emit(grams = 845, stable = false)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.first().weighPanel?.stable)

        viewModel.onAction(PosAction.OnAddWeighedLine(845))
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.first().cart.lines.isEmpty(), "unstable reading must not be addable")

        driverRegistry.fakeScale.emit(grams = 845, stable = true)
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnAddWeighedLine(845))
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertNull(state.weighPanel)
        val line = state.cart.lines.single()
        assertEquals(845L, line.weightGrams)
        assertEquals(1935L, line.totalMinor) // 845 * 2290 / 1000, half-up
    }

    @Test
    fun `voiding a line removes it and updates the total`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        advanceUntilIdle()
        val lineId = viewModel.uiState.first().cart.lines.single().lineId

        viewModel.onAction(PosAction.OnVoidLine(lineId))
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertTrue(state.cart.lines.isEmpty())
        assertEquals(0L, state.cart.totalMinor)
    }

    @Test
    fun `charging an empty cart does not open the payment dialog`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnCharge)
        advanceUntilIdle()

        assertNull(viewModel.uiState.first().paymentDialog)
    }

    @Test
    fun `charging opens the payment dialog with the current total`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        advanceUntilIdle()

        viewModel.onAction(PosAction.OnCharge)
        advanceUntilIdle()

        val dialog = viewModel.uiState.first().paymentDialog
        assertEquals(2800L, dialog?.totalMinor)
        assertNull(dialog?.method)
    }

    @Test
    fun `cash payment records the sale kicks the drawer prints and shows the receipt`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnQuickCashChip(3000))
        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()

        val sale = saleRepository.recordedSales.single()
        assertEquals(PaymentMethod.CASH, sale.paymentMethod)
        assertEquals(2800L, sale.totalMinor)
        assertEquals(3000L, sale.cashTenderedMinor)
        assertEquals(200L, sale.changeMinor)
        assertEquals(branchId, sale.branchId)
        assertEquals(cashierId, sale.cashierId)
        assertEquals(1, driverRegistry.fakeDrawer.openCalls)
        assertEquals(1, driverRegistry.fakePrinter.printedDocs.size)

        val state = viewModel.uiState.first { it.receipt != null }
        assertNull(state.paymentDialog)
        assertEquals(2800L, state.receipt?.totalMinor)
        assertEquals(3000L, state.receipt?.cashTenderedMinor)
        assertEquals(200L, state.receipt?.changeMinor)
    }

    @Test
    fun `cash payment is disabled when tendered is short`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnCashTenderedChanged("1000"))
        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()

        assertTrue(saleRepository.recordedSales.isEmpty())
        assertEquals(PaymentMethod.CASH, viewModel.uiState.first().paymentDialog?.method)
    }

    @Test
    fun `dismissing the receipt clears the cart for the next customer`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnQuickCashChip(3000))
        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()

        viewModel.onAction(PosAction.OnDismissReceipt)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertNull(state.receipt)
        assertTrue(state.cart.lines.isEmpty())
        assertEquals(0L, state.cart.totalMinor)
    }

    @Test
    fun `card payment approved via the simulated terminal records the sale without a drawer kick`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CARD))
        viewModel.onAction(PosAction.OnStartCardAuth)
        // Resolve via the demo control before the auto-approve timer fires — advanceUntilIdle
        // would otherwise fast-forward that 2.2s delay too and make this indistinguishable from
        // the auto-approve path.
        driverRegistry.fakeTerminal.resolve(PaymentResult.Approved(PaymentMethod.CARD))
        advanceUntilIdle()

        val sale = saleRepository.recordedSales.single()
        assertEquals(PaymentMethod.CARD, sale.paymentMethod)
        assertNull(sale.cashTenderedMinor)
        assertEquals(0, driverRegistry.fakeDrawer.openCalls, "card approval must not kick the drawer (05 §7)")
        // Await rather than sample: finalizeSale suspends on a Compose Resources lookup for the
        // printed receipt's payment-method label, which resolves off the test scheduler — so
        // advanceUntilIdle() can return before the receipt lands in state.
        assertNotNull(viewModel.uiState.first { it.receipt != null }.receipt)
    }

    @Test
    fun `declining the card auth via the demo control shows retry and switch options`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CARD))
        viewModel.onAction(PosAction.OnStartCardAuth)
        viewModel.onAction(PosAction.OnDeclineCardAuthDemo)
        advanceUntilIdle()

        assertTrue(saleRepository.recordedSales.isEmpty())
        assertEquals(CardAuthPhase.DECLINED, viewModel.uiState.first().paymentDialog?.cardPhase)
    }

    @Test
    fun `transfer payment records the sale without touching the drawer or printer`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.TRANSFER))
        viewModel.onAction(PosAction.OnConfirmOtherPayment)
        advanceUntilIdle()

        val sale = saleRepository.recordedSales.single()
        assertEquals(PaymentMethod.TRANSFER, sale.paymentMethod)
        assertEquals(0, driverRegistry.fakeDrawer.openCalls)
        assertTrue(driverRegistry.fakePrinter.printedDocs.isEmpty())
    }

    @Test
    fun `charging with no active branch or session surfaces an event and records nothing`() = runTest {
        shellContext.setUser(null)
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.TRANSFER))

        val eventDeferred = async { viewModel.eventChannel.first() }
        viewModel.onAction(PosAction.OnConfirmOtherPayment)

        assertEquals(PosEvent.NoActiveBranchOrSession, eventDeferred.await())
        assertTrue(saleRepository.recordedSales.isEmpty())
    }

    @Test
    fun `opening the till shows today's recorded sales for this branch`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.TRANSFER))
        viewModel.onAction(PosAction.OnConfirmOtherPayment)
        advanceUntilIdle()

        viewModel.onAction(PosAction.OnOpenTill)
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertTrue(state.tillOpen)
        assertEquals(1, state.tillSales.size)
        assertEquals(2800L, state.tillSales.single().totalMinor)
    }

    @Test
    fun `the simulated scale is not reported as a working real one`() = runTest {
        viewModel = newViewModel()
        viewModel.uiState.first { !it.loading }
        advanceUntilIdle()

        assertEquals(ScaleMode.SIMULATED, viewModel.uiState.first().scaleMode)
    }

    @Test
    fun `a serial scale that is connected reads as connected`() = runTest {
        driverRegistry = FakeDriverRegistry(initialScaleDriverId = "serial")
        viewModel = newViewModel()
        viewModel.uiState.first { !it.loading }
        advanceUntilIdle()

        assertEquals(ScaleMode.CONNECTED, viewModel.uiState.first().scaleMode)
    }

    @Test
    fun `manual scale driver shows the manual entry panel and submits through the driver`() = runTest {
        driverRegistry = FakeDriverRegistry(initialScaleDriverId = "manual")
        viewModel = newViewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.first().weighPanel?.isManualEntry)
        assertEquals(ScaleMode.MANUAL, viewModel.uiState.first().scaleMode)

        viewModel.onAction(PosAction.OnManualWeightChanged("0,520"))
        advanceUntilIdle()

        val panel = viewModel.uiState.first().weighPanel
        assertEquals(520L, panel?.grams)
        assertEquals(true, panel?.stable)
    }

    @Test
    fun `clearing the manual weight disarms add and never adds a line`() = runTest {
        driverRegistry = FakeDriverRegistry(initialScaleDriverId = "manual")
        viewModel = newViewModel()
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnManualWeightChanged("0,520"))
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.first().weighPanel?.canAdd)

        // The cashier backspaces the field. Previously grams kept 520 and Add stayed armed, so
        // confirming charged for a weight the field no longer showed.
        viewModel.onAction(PosAction.OnManualWeightChanged(""))
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.first().weighPanel?.canAdd)

        viewModel.onAction(PosAction.OnAddWeighedLine(viewModel.uiState.first().weighPanel?.grams ?: 0))
        advanceUntilIdle()
        val state = viewModel.uiState.first()
        assertTrue(state.cart.lines.isEmpty(), "a cleared weight must not become a line")
        assertEquals(0L, state.cart.totalMinor)
    }

    @Test
    fun barcode_knownUnitProduct_addsOrIncrementsLine() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnBarcodeScanned(barcodedEmpanada.barcode!!))
        advanceUntilIdle()
        var state = viewModel.uiState.first()
        assertEquals(1, state.cart.lines.single().quantity)
        assertEquals(1500L, state.cart.totalMinor)

        viewModel.onAction(PosAction.OnBarcodeScanned(barcodedEmpanada.barcode!!))
        advanceUntilIdle()
        state = viewModel.uiState.first()
        assertEquals(1, state.cart.lines.size, "same product barcode scanned twice increments, not a second line")
        assertEquals(2, state.cart.lines.single().quantity)
        assertEquals(3000L, state.cart.totalMinor)
    }

    @Test
    fun barcode_unknown_emitsShowMessage() = runTest {
        viewModel.uiState.first { !it.loading }

        val eventDeferred = async { viewModel.eventChannel.first() }
        viewModel.onAction(PosAction.OnBarcodeScanned("0000000000000"))

        assertEquals(PosEvent.UnknownBarcodeScanned, eventDeferred.await())
        assertTrue(viewModel.uiState.first().cart.lines.isEmpty())
    }

    @Test
    fun barcode_weightProduct_opensWeighPanelInsteadOfUnitLine() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnBarcodeScanned(barcodedBread.barcode!!))
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertTrue(state.cart.lines.isEmpty(), "a WEIGHT product hit by barcode must not add a bare unit line")
        assertEquals(barcodedBread.id, state.weighPanel?.productId)
    }

    @Test
    fun `batch-ready feed surfaces a dismissible notice, and offline (no events) leaves it empty`() = runTest {
        viewModel.uiState.first { !it.loading }
        assertTrue(viewModel.uiState.first().batchNotices.isEmpty(), "no events yet -- offline degrades silently, no error")

        val batch = BatchReadyAnnouncement(
            batchId = Uuid.random(),
            branchId = branchId,
            productId = bread.id,
            productTypeName = "Marraqueta",
            mixWeightGrams = 5000,
            readyAt = Clock.System.now(),
        )
        productionRepository.emitBatchReady(batch)
        advanceUntilIdle()

        val notice = viewModel.uiState.first().batchNotices.single()
        assertEquals(batch.batchId, notice.id)
        val fact = notice.batches.single()
        assertEquals("Marraqueta", fact.productName)
        assertEquals(5000L, fact.massGrams)
        assertEquals("Centro", fact.branchName, "the branch name is looked up, never the id")

        viewModel.onAction(PosAction.OnDismissBatchNotice(notice.id))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.first().batchNotices.isEmpty())
    }

    @Test
    fun `batches ready within ten seconds share one notice, a later one gets its own`() = runTest {
        viewModel.uiState.first { !it.loading }
        val start = Clock.System.now()
        fun batch(name: String, at: kotlin.time.Instant) = BatchReadyAnnouncement(Uuid.random(), branchId, Uuid.random(), name, 4000, at)

        productionRepository.emitBatchReady(batch("Marraqueta", start))
        productionRepository.emitBatchReady(batch("Hallulla", start + 3.seconds))
        productionRepository.emitBatchReady(batch("Pan de molde", start + 60.seconds))
        advanceUntilIdle()

        assertEquals(listOf(2, 1), viewModel.uiState.first().batchNotices.map { it.batches.size })
    }

    @Test
    fun `a batch of an unknown product still gets a notice, without inventing a name`() = runTest {
        viewModel.uiState.first { !it.loading }

        productionRepository.emitBatchReady(
            BatchReadyAnnouncement(Uuid.random(), Uuid.random(), Uuid.random(), "", 0, Clock.System.now()),
        )
        advanceUntilIdle()

        val fact = viewModel.uiState.first().batchNotices.single().batches.single()
        assertNull(fact.productName)
        assertNull(fact.branchName, "a branch this device does not know is left out")
    }

    @Test
    fun `Agregar is gated on a stable, non-zero reading from the scale`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.first().weighPanel?.canAdd, "nothing read yet")

        driverRegistry.fakeScale.emit(grams = 880, stable = false)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.first().weighPanel?.canAdd, "estabilizando")
        assertEquals(false, viewModel.uiState.first().weighPanel?.settled)

        driverRegistry.fakeScale.emit(grams = 0, stable = true)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.first().weighPanel?.canAdd, "a stable zero is not a sale")

        driverRegistry.fakeScale.emit(grams = 842, stable = true)
        advanceUntilIdle()
        assertEquals(true, viewModel.uiState.first().weighPanel?.canAdd)
        assertEquals(true, viewModel.uiState.first().weighPanel?.settled)

        driverRegistry.fakeScale.emit(grams = 845, stable = false)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.first().weighPanel?.canAdd, "it goes off again when the reading moves")
    }

    @Test
    fun `the weigh panel names its source so the demo scale is never mistaken for hardware`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()
        assertEquals(WeighSource.Simulated, viewModel.uiState.first().weighPanel?.source)

        driverRegistry = FakeDriverRegistry(initialScaleDriverId = "manual")
        viewModel = newViewModel()
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()
        assertEquals(WeighSource.Manual, viewModel.uiState.first().weighPanel?.source)

        driverRegistry = FakeDriverRegistry(initialScaleDriverId = "serial")
        viewModel = newViewModel()
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()
        assertEquals(WeighSource.Real, viewModel.uiState.first().weighPanel?.source)
    }

    @Test
    fun `adding sells the weight that was displayed at the click, not a newer reading`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()
        driverRegistry.fakeScale.emit(grams = 557, stable = true)
        advanceUntilIdle()

        // The scale moves on between the click being captured and the action being handled.
        val clicked = viewModel.uiState.first().weighPanel!!.grams
        driverRegistry.fakeScale.emit(grams = 1916, stable = true)
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnAddWeighedLine(clicked))
        advanceUntilIdle()

        val line = viewModel.uiState.first().cart.lines.single()
        assertEquals(557L, line.weightGrams)
    }

    @Test
    fun `manual entry never shows a stabilising state`() = runTest {
        driverRegistry = FakeDriverRegistry(initialScaleDriverId = "manual")
        viewModel = newViewModel()
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.first().weighPanel?.showsStabilising, "empty field")

        viewModel.onAction(PosAction.OnManualWeightChanged("0,520"))
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.first().weighPanel?.showsStabilising, "typed weight")
    }

    @Test
    fun `a typed weight is settled once there is one`() = runTest {
        driverRegistry = FakeDriverRegistry(initialScaleDriverId = "manual")
        viewModel = newViewModel()
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.first().weighPanel?.canAdd)

        viewModel.onAction(PosAction.OnManualWeightChanged("0,520"))
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.first().weighPanel?.canAdd)
    }

    @Test
    fun `batch-ready notice names the catalog product, not the category, and omits a blank name`() = runTest {
        viewModel.uiState.first { !it.loading }
        fun batch(productId: Uuid, typeName: String) = BatchReadyAnnouncement(
            batchId = Uuid.random(),
            branchId = branchId,
            productId = productId,
            productTypeName = typeName,
            mixWeightGrams = 5000,
            readyAt = Clock.System.now(),
        )
        productionRepository.emitBatchReady(batch(bread.id, "Pan"))
        productionRepository.emitBatchReady(batch(Uuid.random(), ""))
        advanceUntilIdle()

        val facts = viewModel.uiState.first().batchNotices.single().batches
        assertEquals(bread.name, facts[0].productName)
        assertEquals(null, facts[1].productName, "unknown product + blank type -> no name, never an id")
    }

    @Test
    fun `sync status starts online idle and reflects pending outbox rows while offline`() = runTest {
        viewModel.uiState.first { !it.loading }
        assertEquals(SyncIndicatorState.OnlineIdle, viewModel.uiState.first().syncIndicator)

        shellContext.setIndicator(SyncIndicatorState.OfflinePending(3))
        advanceUntilIdle()

        assertEquals(SyncIndicatorState.OfflinePending(3), viewModel.uiState.first().syncIndicator)
    }

    @Test
    fun `sync status reflects auth-required as attention with pending count`() = runTest {
        viewModel.uiState.first { !it.loading }

        syncEngine.emit(SyncStatus.AuthRequired(pendingCount = 2, connectivity = ConnectivityStatus.ONLINE))
        shellContext.setIndicator(SyncIndicatorState.Attention(2))
        advanceUntilIdle()

        assertEquals(SyncIndicatorState.Attention(2), viewModel.uiState.first().syncIndicator)
    }

    @Test
    fun `tapping the sync indicator triggers a manual sync`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnSyncIndicatorTapped)
        advanceUntilIdle()

        assertEquals(1, shellContext.syncNowCalls)
    }

    private fun product(
        name: String,
        productType: String,
        soldBy: SoldBy,
        pricePerKgMinor: Long? = null,
        priceMinor: Long? = null,
        barcode: String? = null,
    ) = Product(
        id = Uuid.random(),
        tenantId = Uuid.random(),
        name = name,
        productType = productType,
        soldBy = soldBy,
        pricePerKgMinor = pricePerKgMinor,
        priceMinor = priceMinor,
        barcode = barcode,
        imageUrl = null,
        active = true,
        mixToLoafRatioMilli = null,
        minMixGrams = null,
        maxMixGrams = null,
    )

    // --- Almacén stock badge (#26): informational, from last synced stock, never blocks a sale ---

    private fun almacen(name: String, stock: Long?, reorderLevel: Long? = 5) =
        product(name = name, productType = "Bebidas", soldBy = SoldBy.UNIT, priceMinor = 1500).copy(
            origin = ProductOrigin.ALMACEN, stockUnits = stock, lowStockThresholdUnits = reorderLevel,
        )

    private suspend fun badgesByName(): Map<String, StockBadgeUi?> =
        viewModel.uiState.first { !it.loading }.products.associate { it.name to it.stockBadge }

    @Test
    fun `almacen tiles badge low out and negative stock and stay clean when in stock`() = runTest {
        productRepository.products.value = listOf(
            bread,
            almacen("Plenty", stock = 6),
            almacen("AtLevel", stock = 5),
            almacen("Last", stock = 1),
            almacen("Zero", stock = 0),
            almacen("Below", stock = -3),
            almacen("NoLevel", stock = 2, reorderLevel = null),
            almacen("NeverSynced", stock = null),
            // A Panadería product never carries a badge, whatever stock fields happen to ride along.
            bread.copy(name = "Baked", stockUnits = 0, lowStockThresholdUnits = 5),
        )
        advanceUntilIdle()

        val badges = badgesByName()
        assertNull(badges["Plenty"])
        assertEquals(StockLevel.LOW, badges["AtLevel"]?.level)
        assertEquals(StockLevel.LOW, badges["Last"]?.level)
        assertEquals(StockLevel.OUT, badges["Zero"]?.level)
        assertEquals(StockLevel.NEGATIVE, badges["Below"]?.level)
        assertEquals(-3L, badges["Below"]?.stockUnits)
        assertNull(badges["NoLevel"]?.takeIf { it.level == StockLevel.LOW }, "no reorder level means no low warning")
        assertNull(badges["NeverSynced"], "no stock figure yet means no badge")
        assertNull(badges["Baked"])
        assertNull(badges["Marraqueta"])
    }

    @Test
    fun `an out-of-stock badge is last-known and flagged stale while offline`() = runTest {
        productRepository.products.value = listOf(almacen("Zero", stock = 0))
        advanceUntilIdle()
        assertEquals(false, badgesByName().getValue("Zero")?.stale)

        shellContext.setIndicator(SyncIndicatorState.OfflinePending(2))
        advanceUntilIdle()
        val offline = badgesByName().getValue("Zero")
        assertEquals(StockLevel.OUT, offline?.level)
        assertEquals(true, offline?.stale)

        shellContext.setIndicator(SyncIndicatorState.OnlineIdle)
        advanceUntilIdle()
        assertEquals(false, badgesByName().getValue("Zero")?.stale)
    }

    @Test
    fun `a badge reacts when the stock behind it changes`() = runTest {
        val cola = almacen("Cola", stock = 9)
        productRepository.products.value = listOf(cola)
        advanceUntilIdle()
        assertNull(badgesByName().getValue("Cola"))

        productRepository.products.value = listOf(cola.copy(stockUnits = 2))
        advanceUntilIdle()
        assertEquals(StockLevel.LOW, badgesByName().getValue("Cola")?.level)
    }

    @Test
    fun `selling an out-of-stock almacen product still completes`() = runTest {
        val soldOut = almacen("Zero", stock = 0)
        val negative = almacen("Below", stock = -4)
        productRepository.products.value = listOf(soldOut, negative)
        viewModel.uiState.first { !it.loading }
        advanceUntilIdle()

        viewModel.onAction(PosAction.OnProductTapped(soldOut.id))
        viewModel.onAction(PosAction.OnProductTapped(negative.id))
        assertEquals(2, viewModel.uiState.first().cart.lines.size)
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.TRANSFER))
        viewModel.onAction(PosAction.OnConfirmOtherPayment)
        advanceUntilIdle()

        val sale = saleRepository.recordedSales.single()
        assertEquals(3000L, sale.totalMinor)
        assertEquals(setOf(soldOut.id, negative.id), sale.lines.map { it.productId }.toSet())
    }

    @Test
    fun `a printer failure still records the sale and says the receipt did not print`() = runTest {
        driverRegistry.fakePrinter.nextResult = PrintResult.Failed("no print service found")
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnCharge)
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnCashTenderedChanged("5000"))
        advanceUntilIdle()

        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()

        // The sale commits before any hardware is touched, so a dead printer never loses it.
        assertEquals(1, saleRepository.recordedSales.size)
        // The on-screen receipt is the fallback, it is showing, and it says no paper came out.
        assertEquals(2800L, viewModel.uiState.first().receipt?.totalMinor)
        assertTrue(viewModel.uiState.first().receipt!!.printFailed)
        // Cash still has to be storable when it is the printer that failed.
        assertEquals(1, driverRegistry.fakeDrawer.openCalls)
    }

    @Test
    fun `the printed receipt carries the method itself not a resolved label`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnCharge)
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnCashTenderedChanged("5000"))
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()

        // Building the document no longer suspends on a Compose Resources lookup, which is what
        // made this path non-deterministic under the test scheduler.
        assertEquals(PaymentMethod.CASH, driverRegistry.fakePrinter.printedDocs.single().paymentMethod)
    }

    @Test
    fun `opening the weigh panel tells a scripted scale which product was chosen`() = runTest {
        viewModel.uiState.first { !it.loading }

        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        advanceUntilIdle()

        assertEquals(listOf("Marraqueta"), driverRegistry.fakeScale.selectedProducts)
    }

    @Test
    fun `the on-screen receipt is laid out from the same document the printer was given`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnQuickCashChip(3000))
        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()

        val receipt = viewModel.uiState.first { it.receipt != null }.receipt!!
        assertEquals(ReceiptLayout.render(driverRegistry.fakePrinter.printedDocs.single(), PaperWidth.Mm58), receipt.paperLines)
    }

    @Test
    fun `a receipt for a real printer is not marked as a simulation`() = runTest {
        driverRegistry.select(DeviceInterface.PRINTER, DriverChoice("escpos"))
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnQuickCashChip(3000))
        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()

        assertEquals(false, viewModel.uiState.first { it.receipt != null }.receipt?.paperSimulated)
    }

    @Test
    fun `a cash sale on the simulated drawer says so on the receipt itself`() = runTest {
        driverRegistry.useSimulatedDrawer()
        viewModel.uiState.first { !it.loading }
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnQuickCashChip(3000))
        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()

        assertEquals(DrawerNote.OpenedSimulated, viewModel.uiState.first { it.receipt != null }.receipt?.drawer)
    }

    @Test
    fun `a cash sale on a real drawer does not claim a simulated one opened`() = runTest {
        // A drawer kicked through the printer is only real when the printer is (#88).
        driverRegistry.select(DeviceInterface.PRINTER, DriverChoice("escpos"))
        driverRegistry.select(DeviceInterface.DRAWER, DriverChoice("printer-kick"))
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnQuickCashChip(3000))
        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()

        assertEquals(DrawerNote.Opened, viewModel.uiState.first { it.receipt != null }.receipt?.drawer)
    }
}
