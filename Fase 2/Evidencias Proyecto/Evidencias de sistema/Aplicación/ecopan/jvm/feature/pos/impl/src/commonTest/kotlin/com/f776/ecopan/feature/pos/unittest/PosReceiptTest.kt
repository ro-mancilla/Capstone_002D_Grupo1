@file:OptIn(ExperimentalUuidApi::class, ExperimentalCoroutinesApi::class)

package com.f776.ecopan.feature.pos.unittest

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.hardware.DeviceInterface
import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.hardware.PaymentResult
import com.f776.ecopan.feature.pos.screens.pos.DrawerNote
import com.f776.ecopan.feature.pos.screens.pos.PaymentNote
import com.f776.ecopan.feature.pos.screens.pos.PosAction
import com.f776.ecopan.feature.pos.screens.pos.PosUiState
import com.f776.ecopan.feature.pos.screens.pos.PosViewModel
import com.f776.ecopan.feature.pos.screens.pos.ReceiptUi
import com.f776.ecopan.feature.pos.unittest.fake.FakeAuthRepository
import com.f776.ecopan.feature.pos.unittest.fake.FakeDriverRegistry
import com.f776.ecopan.feature.pos.unittest.fake.FakeProductRepository
import com.f776.ecopan.feature.pos.unittest.fake.FakeProductionRepository
import com.f776.ecopan.feature.pos.unittest.fake.FakeReceiptPrinter
import com.f776.ecopan.feature.pos.unittest.fake.FakeSaleRepository
import com.f776.ecopan.feature.pos.unittest.fake.FakeShellContext
import com.f776.ecopan.feature.pos.unittest.fake.FakeSyncEngine
import com.f776.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** The boleta: the sale number, the paper lines and the print-failure path (spec 4e/4f). */
class PosReceiptTest {

    private val testDispatcher = MainDispatcherRule.createTestDispatcher()
    private lateinit var driverRegistry: FakeDriverRegistry
    private lateinit var saleRepository: FakeSaleRepository
    private lateinit var viewModel: PosViewModel

    private val branchId = Uuid.random()
    private val tenantId = Uuid.random()
    private val bread = product("Marraqueta", SoldBy.WEIGHT, pricePerKgMinor = 3200)
    private val empanada = product("Empanada de Pino", SoldBy.UNIT, priceMinor = 1800)

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        driverRegistry = FakeDriverRegistry(initialScaleDriverId = "mock")
        saleRepository = FakeSaleRepository()
        viewModel = PosViewModel(
            productRepository = FakeProductRepository(listOf(bread, empanada)),
            driverRegistry = driverRegistry,
            saleRepository = saleRepository,
            authRepository = FakeAuthRepository(),
            shellContext = FakeShellContext(
                branchId = branchId,
                userId = Uuid.random(),
                tenantName = "Panadería El Sol",
                tenantSlug = "el-sol",
                branchName = "Sucursal Principal",
            ),
            productionRepository = FakeProductionRepository(),
            syncEngine = FakeSyncEngine(),
        )
    }

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    private suspend fun TestScope.sellWeighedBreadAndTwoEmpanadasForCash(): ReceiptUi {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(bread.id))
        driverRegistry.fakeScale.emit(grams = 842, stable = true)
        advanceUntilIdle()
        viewModel.onAction(PosAction.OnAddWeighedLine(842))
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CASH))
        viewModel.onAction(PosAction.OnQuickCashChip(10_000))
        viewModel.onAction(PosAction.OnConfirmCashPayment)
        advanceUntilIdle()
        return awaitReceipt()
    }

    private suspend fun awaitReceipt(): ReceiptUi = assertNotNull(viewModel.uiState.first { it.receipt != null }.receipt)

    private fun state(): PosUiState = viewModel.uiState.value

    @Test
    fun `the boleta carries the till's sale number, and the paper prints it as Venta N-o`() = runTest {
        val receipt = sellWeighedBreadAndTwoEmpanadasForCash()

        assertEquals(1, receipt.receiptNumber)
        assertTrue("Venta N.º 0001" in receipt.paperLines)
        assertEquals(1, saleRepository.recordedSales.single().receiptNumber)
    }

    @Test
    fun `the sale number advances with every sale on this till`() = runTest {
        sellWeighedBreadAndTwoEmpanadasForCash()
        viewModel.onAction(PosAction.OnDismissReceipt)
        val second = sellWeighedBreadAndTwoEmpanadasForCash()

        assertEquals(2, second.receiptNumber)
        assertTrue("Venta N.º 0002" in second.paperLines)
    }

    @Test
    fun `no identifier reaches the paper or the doc handed to the printer`() = runTest {
        val receipt = sellWeighedBreadAndTwoEmpanadasForCash()
        val saleId = receipt.saleId.toString()

        assertFalse(receipt.paperLines.any { saleId in it })
        val printed = driverRegistry.fakePrinter.printedDocs.single()
        assertEquals(receipt.paperLines, com.f776.ecopan.domain.hardware.ReceiptLayout.render(printed, receipt.paperWidth))
        assertFalse(receipt.paperLines.any { Regex("[0-9a-f]{8}-[0-9a-f]{4}-").containsMatchIn(it) })
    }

    @Test
    fun `the paper is laid out at 32 columns with the bakery and branch on top`() = runTest {
        val receipt = sellWeighedBreadAndTwoEmpanadasForCash()
        val lines = receipt.paperLines

        assertEquals(32, receipt.paperWidth.columns)
        assertTrue(lines.all { it.length <= 32 })
        assertEquals("Panadería El Sol", lines[0].trim())
        assertEquals("Sucursal Principal", lines[1].trim())
        assertTrue("Marraqueta" in lines)
        assertTrue(lines.any { it.startsWith("  0,842 kg @ \$3.200/kg") && it.endsWith("\$2.694") })
        assertTrue(lines.any { it.startsWith("  2 @ \$1.800") && it.endsWith("\$3.600") })
        assertTrue(lines.any { it.startsWith("Total") && it.endsWith("\$6.294") })
        assertTrue(lines.any { it.startsWith("Efectivo") && it.endsWith("\$10.000") })
        assertTrue(lines.any { it.startsWith("Vuelto") && it.endsWith("\$3.706") })
        assertTrue(Regex("""\d{2}-\d{2}-\d{4} \d{2}:\d{2}""").matches(lines[3]), "issue date and time, got '${lines[3]}'")
    }

    @Test
    fun `an 80 mm printer gets 48 column paper`() = runTest {
        driverRegistry.select(DeviceInterface.PRINTER, DriverChoice("mock", mapOf("paperWidthMm" to "80")))
        advanceUntilIdle()

        val receipt = sellWeighedBreadAndTwoEmpanadasForCash()

        assertEquals(80, receipt.paperWidth.millimetres)
        assertTrue(receipt.paperLines.all { it.length <= 48 })
        assertTrue(receipt.paperLines.any { it.length > 32 }, "the rules fill the wider paper")
    }

    @Test
    fun `a demo printer marks the paper simulated, a real one does not`() = runTest {
        assertTrue(sellWeighedBreadAndTwoEmpanadasForCash().paperSimulated)

        viewModel.onAction(PosAction.OnDismissReceipt)
        driverRegistry.select(DeviceInterface.PRINTER, DriverChoice("escpos"))
        advanceUntilIdle()
        assertFalse(sellWeighedBreadAndTwoEmpanadasForCash().paperSimulated)
    }

    @Test
    fun `a cash sale on the demo drawer says the drawer was simulated`() = runTest {
        assertEquals(DrawerNote.OpenedSimulated, sellWeighedBreadAndTwoEmpanadasForCash().drawer)
    }

    @Test
    fun `a cash sale on a drawer kicked by a real printer just says it opened`() = runTest {
        driverRegistry.select(DeviceInterface.PRINTER, DriverChoice("escpos"))
        driverRegistry.select(DeviceInterface.DRAWER, DriverChoice("printer-kick"))
        advanceUntilIdle()

        assertEquals(DrawerNote.Opened, sellWeighedBreadAndTwoEmpanadasForCash().drawer)
    }

    @Test
    fun `a drawer kicked through no printer is still simulated`() = runTest {
        driverRegistry.select(DeviceInterface.DRAWER, DriverChoice("printer-kick"))
        advanceUntilIdle()

        assertEquals(DrawerNote.OpenedSimulated, sellWeighedBreadAndTwoEmpanadasForCash().drawer)
    }

    @Test
    fun `a sale paid without cash opens no drawer`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.TRANSFER))
        viewModel.onAction(PosAction.OnConfirmOtherPayment)
        advanceUntilIdle()

        assertEquals(DrawerNote.None, awaitReceipt().drawer)
    }

    @Test
    fun `a card sale carries a payment note like the cash drawer note`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.CARD))
        viewModel.onAction(PosAction.OnStartCardAuth)
        driverRegistry.fakeTerminal.resolve(PaymentResult.Approved(PaymentMethod.CARD))
        advanceUntilIdle()

        assertEquals(PaymentNote.CardApprovedSimulated, awaitReceipt().paymentNote)
    }

    @Test
    fun `a transfer sale carries a payment note`() = runTest {
        viewModel.uiState.first { !it.loading }
        viewModel.onAction(PosAction.OnProductTapped(empanada.id))
        viewModel.onAction(PosAction.OnCharge)
        viewModel.onAction(PosAction.OnPaymentMethodSelected(PaymentMethod.TRANSFER))
        viewModel.onAction(PosAction.OnConfirmOtherPayment)
        advanceUntilIdle()

        assertEquals(PaymentNote.TransferRecorded, awaitReceipt().paymentNote)
    }

    @Test
    fun `a cash sale has no payment note, the drawer note covers it`() = runTest {
        assertEquals(PaymentNote.None, sellWeighedBreadAndTwoEmpanadasForCash().paymentNote)
    }

    @Test
    fun `a printer that reports failure keeps the sale and offers Reimprimir`() = runTest {
        driverRegistry.fakePrinter.behaviour = FakeReceiptPrinter.Behaviour.Fail

        val receipt = sellWeighedBreadAndTwoEmpanadasForCash()

        assertTrue(receipt.printFailed)
        assertEquals(1, saleRepository.recordedSales.size, "the sale is recorded before and regardless of the print")
        assertEquals(10_000L, receipt.cashTenderedMinor)
    }

    @Test
    fun `a printer that throws is the same failure, not a crash`() = runTest {
        driverRegistry.fakePrinter.behaviour = FakeReceiptPrinter.Behaviour.Throw

        assertTrue(sellWeighedBreadAndTwoEmpanadasForCash().printFailed)
        assertEquals(1, saleRepository.recordedSales.size)
    }

    @Test
    fun `reprinting sends the same doc again and clears the failure when it works`() = runTest {
        driverRegistry.fakePrinter.behaviour = FakeReceiptPrinter.Behaviour.Fail
        sellWeighedBreadAndTwoEmpanadasForCash()

        driverRegistry.fakePrinter.behaviour = FakeReceiptPrinter.Behaviour.Succeed
        viewModel.onAction(PosAction.OnReprintReceipt)
        advanceUntilIdle()

        assertFalse(state().receipt!!.printFailed)
        val docs = driverRegistry.fakePrinter.printedDocs
        assertEquals(2, docs.size)
        assertEquals(docs[0], docs[1])
        assertEquals(1, saleRepository.recordedSales.size, "a reprint never records a second sale")
    }

    @Test
    fun `reprinting that fails again keeps the alert`() = runTest {
        driverRegistry.fakePrinter.behaviour = FakeReceiptPrinter.Behaviour.Fail
        sellWeighedBreadAndTwoEmpanadasForCash()

        viewModel.onAction(PosAction.OnReprintReceipt)
        advanceUntilIdle()

        assertTrue(state().receipt!!.printFailed)
    }

    @Test
    fun `a successful print shows no failure`() = runTest {
        assertFalse(sellWeighedBreadAndTwoEmpanadasForCash().printFailed)
    }

    @Test
    fun `the till list identifies a sale by its number`() = runTest {
        sellWeighedBreadAndTwoEmpanadasForCash()
        viewModel.onAction(PosAction.OnOpenTill)
        advanceUntilIdle()

        assertEquals(listOf(1), state().tillSales.map { it.receiptNumber })
    }

    private fun product(name: String, soldBy: SoldBy, pricePerKgMinor: Long? = null, priceMinor: Long? = null) = Product(
        id = Uuid.random(),
        tenantId = tenantId,
        name = name,
        productType = "Panes",
        soldBy = soldBy,
        pricePerKgMinor = pricePerKgMinor,
        priceMinor = priceMinor,
        barcode = null,
        imageUrl = null,
        active = true,
        mixToLoafRatioMilli = null,
        minMixGrams = null,
        maxMixGrams = null,
    )
}
