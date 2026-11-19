package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.jvm.EscPosPrinterDriverFactory
import com.f776.ecopan.data.hardware.jvm.PrinterKickDrawerFactory
import com.f776.ecopan.data.hardware.jvm.SerialScaleDriverFactory
import com.f776.ecopan.domain.hardware.CashDrawer
import com.f776.ecopan.domain.hardware.DeviceInterface
import com.f776.ecopan.domain.hardware.DeviceProfile
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.DriverRegistry
import com.f776.ecopan.domain.hardware.PaymentTerminal
import com.f776.ecopan.domain.hardware.PrintResult
import com.f776.ecopan.domain.hardware.ReceiptDoc
import com.f776.ecopan.domain.hardware.ReceiptPrinter
import com.f776.ecopan.domain.hardware.Scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Item 6 (04 §7): the JVM-only factories don't change `DriverRegistryImpl`'s swap/fallback logic
 * (already covered by `DriverRegistryImplTest`), but they are new registry-visible descriptors --
 * verify they build cleanly and are correctly flagged `platformOnly` for the Settings picker (04 §6).
 */
internal class JvmDriverFactoriesTest {

    @Test
    fun `serial scale factory descriptor is platform-only and creates a disconnected driver`() {
        val factory = SerialScaleDriverFactory(Dispatchers.Default)

        assertEquals(DriverDescriptor("serial", "Balanza serial (jSerialComm)", platformOnly = true), factory.descriptor)
        val scale = factory.create(emptyMap())
        assertEquals(DeviceStatus.DISCONNECTED, scale.status.value)
    }

    @Test
    fun `escpos printer factory descriptor is platform-only and creates a disconnected driver`() {
        val factory = EscPosPrinterDriverFactory(Dispatchers.Unconfined)

        assertEquals(DriverDescriptor("escpos", "Impresora ESC/POS", platformOnly = true), factory.descriptor)
        val printer = factory.create(emptyMap())
        assertEquals(DeviceStatus.DISCONNECTED, printer.status.value)
    }

    @Test
    fun `printer-kick drawer factory mirrors the active printer status and delegates open to kickDrawer`() = kotlinx.coroutines.test.runTest {
        val fakePrinter = FakePrinter()
        val fakeRegistry = FakeDriverRegistry(fakePrinter)
        val ioScope = CoroutineScope(Dispatchers.Unconfined + Job())
        val factory = PrinterKickDrawerFactory(registryProvider = { fakeRegistry }, ioCoroutineScope = ioScope)

        assertEquals(DriverDescriptor("printer-kick", "Cajón vía impresora (RJ11)", platformOnly = true, viaPrinter = true), factory.descriptor)
        val drawer = factory.create(emptyMap())

        assertEquals(DeviceStatus.CONNECTED, drawer.status.value)
        drawer.open()
        assertTrue(fakePrinter.kicked)
        ioScope.cancel()
    }

    private class FakePrinter : ReceiptPrinter {
        var kicked = false
            private set
        private val _status = MutableStateFlow(DeviceStatus.CONNECTED)
        override val status = _status.asStateFlow()
        override suspend fun print(doc: ReceiptDoc): PrintResult = PrintResult.Ok
        override suspend fun kickDrawer() {
            kicked = true
        }
    }

    private class FakeDriverRegistry(printer: ReceiptPrinter) : DriverRegistry {
        override val profile: Flow<DeviceProfile> get() = throw NotImplementedError()
        override val availableScaleDrivers: List<DriverDescriptor> = emptyList()
        override val availablePrinterDrivers: List<DriverDescriptor> = emptyList()
        override val availableDrawerDrivers: List<DriverDescriptor> = emptyList()
        override val availableTerminalDrivers: List<DriverDescriptor> = emptyList()
        override val scale: Flow<Scale> get() = throw NotImplementedError()
        override val printer: Flow<ReceiptPrinter> = flowOf(printer)
        override val drawer: Flow<CashDrawer> get() = throw NotImplementedError()
        override val terminal: Flow<PaymentTerminal> get() = throw NotImplementedError()
        override suspend fun select(iface: DeviceInterface, choice: DriverChoice) = throw NotImplementedError()
        override suspend fun reconnectScale() = throw NotImplementedError()
    }
}
