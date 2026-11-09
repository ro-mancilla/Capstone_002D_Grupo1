package com.f776.ecopan.feature.pos.unittest.fake

import com.f776.ecopan.domain.hardware.CashDrawer
import com.f776.ecopan.domain.hardware.DeviceInterface
import com.f776.ecopan.domain.hardware.DeviceProfile
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.DriverRegistry
import com.f776.ecopan.domain.hardware.PaymentResult
import com.f776.ecopan.domain.hardware.PaymentTerminal
import com.f776.ecopan.domain.hardware.PrintResult
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.SimulatedDrawer
import com.f776.ecopan.domain.hardware.ReceiptPrinter
import com.f776.ecopan.domain.hardware.Scale
import com.f776.ecopan.domain.hardware.SimulatedTerminalControl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal open class FakeReceiptPrinter : ReceiptPrinter {
    override val status: StateFlow<DeviceStatus> = MutableStateFlow(DeviceStatus.CONNECTED).asStateFlow()
    val printedDocs = mutableListOf<ReceiptDoc>()
    val kicks = mutableListOf<Unit>()

    /** What the next [print] does: succeed, report failure, or throw like a driver with a broken cable. */
    var behaviour: Behaviour = Behaviour.Succeed

    enum class Behaviour { Succeed, Fail, Throw }

    /** Scripted outcome of a successful-path print -- `EscPosPrinter` reports `Failed` for a missing service or any IO error. */
    var nextResult: PrintResult = PrintResult.Ok

    override suspend fun print(doc: ReceiptDoc): PrintResult {
        printedDocs += doc
        return when (behaviour) {
            Behaviour.Succeed -> nextResult
            Behaviour.Fail -> PrintResult.Failed("sin papel")
            Behaviour.Throw -> error("cable desconectado")
        }
    }

    override suspend fun kickDrawer() {
        kicks += Unit
    }
}

internal open class FakeCashDrawer : CashDrawer {
    override val status: StateFlow<DeviceStatus> = MutableStateFlow(DeviceStatus.CONNECTED).asStateFlow()
    var openCalls = 0
        private set

    override suspend fun open() {
        openCalls++
    }
}

/** Mirrors the real MockDrawer: nothing is opened, and it says so. */
internal class FakeSimulatedCashDrawer : FakeCashDrawer(), SimulatedDrawer

/** Mirrors the real `SimulatedTerminal` — `request()` suspends until [resolve] is called. */
internal class FakePaymentTerminal : PaymentTerminal, SimulatedTerminalControl {
    private var pending: CompletableDeferred<PaymentResult>? = null

    override suspend fun request(amountMinor: Long): PaymentResult {
        val deferred = CompletableDeferred<PaymentResult>()
        pending = deferred
        return try {
            deferred.await()
        } finally {
            pending = null
        }
    }

    override fun resolve(result: PaymentResult) {
        pending?.complete(result)
    }
}

internal class FakeDriverRegistry(initialScaleDriverId: String = "mock") : DriverRegistry {
    val fakeScale = FakeScale()
    private val activePrinter = MutableStateFlow<FakeReceiptPrinter>(FakeReceiptPrinter())
    val fakePrinter: FakeReceiptPrinter get() = activePrinter.value

    private val activeDrawer = MutableStateFlow<FakeCashDrawer>(FakeCashDrawer())
    val fakeDrawer: FakeCashDrawer get() = activeDrawer.value

    /** Swap the till's drawer for a simulated one, as choosing the mock driver does. */
    fun useSimulatedDrawer() {
        activeDrawer.value = FakeSimulatedCashDrawer()
    }
    val fakeTerminal = FakePaymentTerminal()
    private val scaleChoice = MutableStateFlow(DriverChoice(initialScaleDriverId))
    private val printerChoice = MutableStateFlow(DriverChoice("mock"))
    private val drawerChoice = MutableStateFlow(DriverChoice("mock"))

    override val profile: Flow<DeviceProfile> = combine(scaleChoice, printerChoice, drawerChoice) { scale, printer, drawer ->
        DeviceProfile(scale = scale, printer = printer, drawer = drawer, terminal = DriverChoice("mock"))
    }

    override val availableScaleDrivers: List<DriverDescriptor> = listOf(
        DriverDescriptor("mock", "Balanza simulada", false, simulated = true),
        DriverDescriptor("manual", "Ingreso manual", false),
        DriverDescriptor("serial", "Balanza serial", true),
    )
    override val availablePrinterDrivers: List<DriverDescriptor> = listOf(
        DriverDescriptor("mock", "Sin impresora", false, none = true),
        DriverDescriptor("escpos", "Impresora ESC/POS", true),
    )
    override val availableDrawerDrivers: List<DriverDescriptor> = listOf(
        DriverDescriptor("mock", "Cajón simulado", false, simulated = true),
        DriverDescriptor("printer-kick", "Cajón vía impresora", true, viaPrinter = true),
    )
    override val availableTerminalDrivers: List<DriverDescriptor> = emptyList()

    override val scale: Flow<Scale> = MutableStateFlow<Scale>(fakeScale).asStateFlow()
    override val printer: Flow<ReceiptPrinter> = activePrinter.asStateFlow()
    override val drawer: Flow<CashDrawer> = activeDrawer.asStateFlow()
    override val terminal: Flow<PaymentTerminal> = MutableStateFlow<PaymentTerminal>(fakeTerminal).asStateFlow()

    val selectCalls = mutableListOf<Pair<DeviceInterface, DriverChoice>>()

    override suspend fun select(iface: DeviceInterface, choice: DriverChoice) {
        selectCalls += iface to choice
        when (iface) {
            DeviceInterface.SCALE -> scaleChoice.value = choice
            DeviceInterface.PRINTER -> printerChoice.value = choice
            DeviceInterface.DRAWER -> drawerChoice.value = choice
            DeviceInterface.TERMINAL -> Unit
        }
    }

    override suspend fun reconnectScale() = Unit
}
