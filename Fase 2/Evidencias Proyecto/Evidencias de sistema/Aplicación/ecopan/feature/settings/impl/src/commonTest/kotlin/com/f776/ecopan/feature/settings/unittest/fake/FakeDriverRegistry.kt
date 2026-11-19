package com.f776.ecopan.feature.settings.unittest.fake

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
import com.f776.ecopan.domain.hardware.RawTextSource
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptPrinter
import com.f776.ecopan.domain.hardware.Scale
import com.f776.ecopan.domain.hardware.WeightReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

/** A scale a test drives by hand: status, readings and the last raw text. */
class FakeScale(val choice: DriverChoice) : Scale, RawTextSource {
    val statusFlow = MutableStateFlow(DeviceStatus.DISCONNECTED)
    override val status: StateFlow<DeviceStatus> = statusFlow.asStateFlow()
    private val readingFlow = MutableSharedFlow<WeightReading>(replay = 1, extraBufferCapacity = 8)
    override val readings: Flow<WeightReading> = readingFlow
    private val rawFlow = MutableStateFlow<String?>(null)
    override val lastRawText: StateFlow<String?> = rawFlow.asStateFlow()

    var closed = false
        private set

    override fun start() = Unit
    override fun close() {
        closed = true
    }

    fun emit(grams: Long, stable: Boolean, raw: String? = null) {
        raw?.let { rawFlow.value = it }
        readingFlow.tryEmit(WeightReading(grams, stable, Clock.System.now()))
    }
}

private object FakeReceiptPrinter : ReceiptPrinter {
    override val status: StateFlow<DeviceStatus> = MutableStateFlow(DeviceStatus.CONNECTED).asStateFlow()
    override suspend fun print(doc: ReceiptDoc): PrintResult = PrintResult.Ok
    override suspend fun kickDrawer() = Unit
}

private object FakeCashDrawer : CashDrawer {
    override val status: StateFlow<DeviceStatus> = MutableStateFlow(DeviceStatus.CONNECTED).asStateFlow()
    override suspend fun open() = Unit
}

private object FakePaymentTerminal : PaymentTerminal {
    override suspend fun request(amountMinor: Long): PaymentResult = PaymentResult.Cancelled
}

class FakeDriverRegistry : DriverRegistry {
    private val scaleChoice = MutableStateFlow(DriverChoice("mock"))
    private val printerChoice = MutableStateFlow(DriverChoice("mock"))
    private val drawerChoice = MutableStateFlow(DriverChoice("mock"))
    private val terminalChoice = MutableStateFlow(DriverChoice("mock"))
    private val restarts = MutableStateFlow(0)

    /** Every scale instance the registry opened, oldest first. */
    val scales = mutableListOf<FakeScale>()
    val currentScale: FakeScale get() = scales.last()

    override val profile: Flow<DeviceProfile> = combine(scaleChoice, printerChoice, drawerChoice, terminalChoice) { scale, printer, drawer, terminal ->
        DeviceProfile(scale = scale, printer = printer, drawer = drawer, terminal = terminal)
    }

    override val availableScaleDrivers: List<DriverDescriptor> = listOf(
        DriverDescriptor("mock", "Balanza simulada (demo)", false, simulated = true),
        DriverDescriptor("manual", "Ingreso manual de peso", false),
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
    override val availableTerminalDrivers: List<DriverDescriptor> = listOf(DriverDescriptor("mock", "Terminal simulado", false, simulated = true))

    override val scale: Flow<Scale> = combine(scaleChoice, restarts) { choice, _ -> FakeScale(choice).also { scales += it } }
    override val printer: Flow<ReceiptPrinter> = MutableStateFlow<ReceiptPrinter>(FakeReceiptPrinter).asStateFlow()
    override val drawer: Flow<CashDrawer> = MutableStateFlow<CashDrawer>(FakeCashDrawer).asStateFlow()
    override val terminal: Flow<PaymentTerminal> = MutableStateFlow<PaymentTerminal>(FakePaymentTerminal).asStateFlow()

    val selectCalls = mutableListOf<Pair<DeviceInterface, DriverChoice>>()
    var reconnects = 0
        private set

    override suspend fun select(iface: DeviceInterface, choice: DriverChoice) {
        selectCalls += iface to choice
        when (iface) {
            DeviceInterface.SCALE -> scaleChoice.value = choice
            DeviceInterface.PRINTER -> printerChoice.value = choice
            DeviceInterface.DRAWER -> drawerChoice.value = choice
            DeviceInterface.TERMINAL -> terminalChoice.value = choice
        }
    }

    override suspend fun reconnectScale() {
        reconnects++
        restarts.value += 1
    }

    /** Pre-seeds the applied scale, as if it had been saved in an earlier session. */
    fun seedScale(choice: DriverChoice) {
        scaleChoice.value = choice
    }
}
