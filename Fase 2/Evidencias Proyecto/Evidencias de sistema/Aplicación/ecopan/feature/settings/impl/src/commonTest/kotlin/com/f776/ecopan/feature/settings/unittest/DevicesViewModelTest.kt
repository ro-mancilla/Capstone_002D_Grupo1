@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.feature.settings.unittest

import com.f776.ecopan.domain.hardware.DeviceDisplay
import com.f776.ecopan.domain.hardware.DeviceInterface
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.SerialScaleSettings
import com.f776.ecopan.domain.hardware.SerialPortInfo
import com.f776.ecopan.domain.hardware.WeightReading
import com.f776.ecopan.feature.settings.screens.settings.devices.ApplyBar
import com.f776.ecopan.feature.settings.screens.settings.devices.DeviceText
import com.f776.ecopan.feature.settings.screens.settings.devices.DevicesAction
import com.f776.ecopan.feature.settings.screens.settings.devices.DevicesNotice
import com.f776.ecopan.feature.settings.screens.settings.devices.DevicesUiState
import com.f776.ecopan.feature.settings.screens.settings.devices.DevicesViewModel
import com.f776.ecopan.feature.settings.screens.settings.devices.ProbeResult
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleBrand
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleConnection
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleField
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleFieldError
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleModeOption
import com.f776.ecopan.feature.settings.screens.settings.devices.deriveLiveScale
import com.f776.ecopan.feature.settings.unittest.fake.FakeDriverRegistry
import com.f776.ecopan.feature.settings.unittest.fake.FakeDeviceDiscovery
import com.f776.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DevicesViewModelTest {

    private val testDispatcher = MainDispatcherRule.createTestDispatcher()
    private lateinit var registry: FakeDriverRegistry
    private lateinit var scanner: FakeDeviceDiscovery
    private val com3 = SerialPortInfo("COM3", "Prolific USB-to-Serial")

    @BeforeTest
    fun setUp() {
        MainDispatcherRule.setMainDispatcher(testDispatcher)
        registry = FakeDriverRegistry()
        scanner = FakeDeviceDiscovery(ports = listOf(com3))
    }

    @AfterTest
    fun tearDown() {
        MainDispatcherRule.resetMainDispatcher()
    }

    /** A view model with a live subscriber, as the screen has. */
    private fun TestScope.viewModel(): Pair<DevicesViewModel, () -> DevicesUiState> {
        val vm = DevicesViewModel(registry, scanner)
        backgroundScope.launch(testDispatcher) { vm.uiState.collect {} }
        return vm to { vm.uiState.value }
    }

    @Test
    fun `nothing is pending when the draft equals the applied scale`() = runTest {
        val (_, state) = viewModel()

        assertFalse(state().dirty)
        assertNull(state().bar)
    }

    @Test
    fun `picking serial pre-selects the first port and raises the unapplied bar`() = runTest {
        val (vm, state) = viewModel()

        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))

        assertEquals("COM3", state().draft.serial.port)
        assertTrue(state().dirty)
        assertEquals(ApplyBar.Unapplied, state().bar)
        assertTrue(state().canApply)
        assertTrue(registry.selectCalls.isEmpty(), "editing must not touch the running scale")
    }

    @Test
    fun `errors hidden in the collapsed Avanzado section are counted and disable apply`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))

        vm.onAction(DevicesAction.EditField(ScaleField.FrameRegex, "["))
        vm.onAction(DevicesAction.EditField(ScaleField.UnitFactors, "kilos"))

        assertEquals(ApplyBar.HiddenErrors(2), state().bar)
        assertEquals(2, state().hiddenErrorCount)
        assertFalse(state().canApply)
    }

    @Test
    fun `expanding Avanzado turns hidden errors into visible ones`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))
        vm.onAction(DevicesAction.EditField(ScaleField.FrameRegex, "["))
        vm.onAction(DevicesAction.EditField(ScaleField.WindowMs, "1"))

        vm.onAction(DevicesAction.ToggleAdvanced)

        assertEquals(0, state().hiddenErrorCount)
        assertEquals(ApplyBar.VisibleErrors(2), state().bar)
        assertFalse(state().canApply)
    }

    @Test
    fun `a basic error is never counted as hidden`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))
        vm.onAction(DevicesAction.EditField(ScaleField.Baud, "abc"))
        vm.onAction(DevicesAction.EditField(ScaleField.FrameRegex, "["))

        assertEquals(1, state().hiddenErrorCount)
        assertEquals(ApplyBar.HiddenErrors(1), state().bar)
    }

    @Test
    fun `apply with errors does nothing`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))
        vm.onAction(DevicesAction.EditField(ScaleField.Baud, "1"))

        vm.onAction(DevicesAction.Apply)

        assertTrue(registry.selectCalls.isEmpty())
        assertTrue(state().dirty)
    }

    @Test
    fun `apply sends the whole serial configuration to the registry and clears the draft`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))
        vm.onAction(DevicesAction.SelectBrand(ScaleBrand.Toledo))

        vm.onAction(DevicesAction.Apply)

        val (iface, choice) = registry.selectCalls.single()
        assertEquals(DeviceInterface.SCALE, iface)
        assertEquals("serial", choice.driverId)
        assertEquals("COM3", choice.config["port"])
        assertEquals("poll-generic", choice.config["protocol"])
        assertEquals("8N1", choice.config["framing"])
        assertEquals(SerialScaleSettings.CONFIG_KEYS.toSet(), choice.config.keys, "the whole serial block is written as a unit")
        assertEquals(ScaleBrand.Toledo, state().applied.serial.brand, "the brand is read back from the line parameters")
        assertFalse(state().dirty)
        assertNull(state().bar)
    }

    @Test
    fun `discard throws the draft away`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))

        vm.onAction(DevicesAction.Discard)

        assertFalse(state().dirty)
        assertEquals(ScaleModeOption.Simulated, state().draft.mode)
    }

    @Test
    fun `leaving serial with typed settings discards them and says so`() = runTest {
        registry.seedScale(DriverChoice("manual"))
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))
        vm.onAction(DevicesAction.EditField(ScaleField.Baud, "4800"))

        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Simulated))
        assertEquals(DevicesNotice.SerialDraftDiscarded, state().notice)

        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))
        assertEquals("9600", state().draft.serial.baud, "the typed baud must not come back")
    }

    @Test
    fun `leaving serial without edits says nothing`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))

        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Manual))

        assertNull(state().notice)
    }

    @Test
    fun `the probe reads a pasted line with the draft frame`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))

        vm.onAction(DevicesAction.ProbeInputChanged("ST,GS, 1.234 kg"))

        assertEquals(ProbeResult.Read(1234, true), state().probe)
    }

    @Test
    fun `after applying serial the chip walks from connecting to responding`() = runTest {
        registry.seedScale(DriverChoice("serial", mapOf("port" to "COM3")))
        val (_, state) = viewModel()
        assertEquals(ScaleConnection.Connecting, state().connection)

        registry.currentScale.statusFlow.value = DeviceStatus.CONNECTED
        registry.currentScale.emit(842, stable = true, raw = "ST,GS, 0.842 kg")

        assertEquals(ScaleConnection.Responding, state().connection)
        assertEquals(842L, state().live?.grams)
        assertEquals(true, state().live?.stable)
        assertEquals("ST,GS, 0.842 kg", state().live?.rawText)
    }

    @Test
    fun `an open port that never sends is no data after the ten second window`() = runTest {
        registry.seedScale(DriverChoice("serial", mapOf("port" to "COM3")))
        val (_, state) = viewModel()
        registry.currentScale.statusFlow.value = DeviceStatus.CONNECTED

        advanceTimeBy(9.seconds)
        assertEquals(ScaleConnection.Connecting, state().connection)
        advanceTimeBy(2.seconds)
        assertEquals(ScaleConnection.NoData, state().connection)
    }

    @Test
    fun `a port that cannot be opened is reported as such`() = runTest {
        registry.seedScale(DriverChoice("serial", mapOf("port" to "COM3")))
        val (_, state) = viewModel()

        registry.currentScale.statusFlow.value = DeviceStatus.ERROR

        assertEquals(ScaleConnection.PortBusy, state().connection)
    }

    @Test
    fun `no serial ports found is its own state, and rescanning picks up a plugged scale`() = runTest {
        scanner.ports = emptyList()
        registry.seedScale(DriverChoice("serial", mapOf("port" to "COM3")))
        val (vm, state) = viewModel()
        assertEquals(ScaleConnection.NoPorts, state().connection)

        scanner.ports = listOf(com3)
        vm.onAction(DevicesAction.RescanPorts)

        assertEquals(listOf(com3), state().ports)
        assertEquals(2, scanner.scans)
        assertFalse(state().connection == ScaleConnection.NoPorts)
    }

    @Test
    fun `trying again restarts the scale`() = runTest {
        registry.seedScale(DriverChoice("serial", mapOf("port" to "COM3")))
        val (vm, _) = viewModel()
        val first = registry.currentScale

        vm.onAction(DevicesAction.RetryConnection)

        assertEquals(1, registry.reconnects)
        assertTrue(registry.currentScale !== first)
    }

    @Test
    fun `the simulated scale shows the simulated chip and a live reading panel`() = runTest {
        val (_, state) = viewModel()

        assertEquals(ScaleConnection.Simulated, state().connection)
        assertTrue(state().live != null)
    }

    @Test
    fun `manual entry has no live panel`() = runTest {
        registry.seedScale(DriverChoice("manual"))
        val (_, state) = viewModel()

        assertEquals(ScaleConnection.Manual, state().connection)
        assertNull(state().live)
    }

    @Test
    fun `printer, drawer and terminal apply immediately and report which are simulated`() = runTest {
        val (vm, state) = viewModel()
        assertEquals(DeviceDisplay.None, state().printer.display)
        assertFalse(state().printer.simulated, "Sin impresora is not a simulated device")
        assertEquals(DeviceDisplay.Simulated, state().drawer.display)
        assertEquals(DeviceDisplay.Simulated, state().terminal.display)

        vm.onAction(DevicesAction.SelectPrinter("escpos"))
        assertEquals(DeviceDisplay.Ready, state().printer.display)
        assertEquals(DriverChoice("escpos", mapOf("paperWidthMm" to "58")), registry.selectCalls.last().second)
    }

    @Test
    fun `the drawer text follows the printer setting`() = runTest {
        val (vm, state) = viewModel()
        assertEquals(DeviceText.Simulated, state().drawer.text)

        vm.onAction(DevicesAction.SelectPrinter("escpos"))
        assertEquals(DeviceText.DrawerSimulatedWithRealPrinter, state().drawer.text)

        vm.onAction(DevicesAction.SelectDrawer("printer-kick"))
        assertEquals(DeviceText.DrawerViaPrinter, state().drawer.text)
        assertEquals(DeviceDisplay.Ready, state().drawer.display)

        vm.onAction(DevicesAction.SelectPrinter("mock"))
        assertEquals(DeviceText.DrawerViaPrinterUnavailable, state().drawer.text)
        assertEquals(DeviceDisplay.Simulated, state().drawer.display, "a drawer kicked through no printer opens nothing")
    }

    @Test
    fun `picking serial never preselects a system port`() = runTest {
        scanner.ports = listOf(SerialPortInfo("cu.Bluetooth-Incoming-Port", ""), SerialPortInfo("cu.debug-console", ""), SerialPortInfo("cu.usbserial-1410", ""))
        val (vm, state) = viewModel()

        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))

        assertEquals("cu.usbserial-1410", state().draft.serial.port)
        assertEquals(listOf("cu.usbserial-1410"), state().ports.map { it.name })
    }

    @Test
    fun `with only system ports the port is left empty`() = runTest {
        scanner.ports = listOf(SerialPortInfo("cu.debug-console", ""))
        val (vm, state) = viewModel()

        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))

        assertEquals("", state().draft.serial.port)
    }

    @Test
    fun `the live test shows the text of the frame it parsed, not a newer one`() {
        val reading = WeightReading(2458, stable = false, at = Instant.fromEpochSeconds(0), rawText = "US,GS, 2.458 kg")

        val live = deriveLiveScale(reading, lastRawText = "ST,GS, 2.395 kg")

        assertEquals(2458L, live.grams)
        assertEquals("US,GS, 2.458 kg", live.rawText)
    }

    @Test
    fun `before any reading the live test shows whatever the scale sent`() {
        val live = deriveLiveScale(null, lastRawText = "garbage")

        assertNull(live.grams)
        assertEquals("garbage", live.rawText)
    }

    @Test
    fun `the paper width is kept with the printer choice`() = runTest {
        val (vm, state) = viewModel()
        assertEquals(PaperWidth.Mm58, state().printer.paperWidth)

        vm.onAction(DevicesAction.SelectPaperWidth(PaperWidth.Mm80))

        assertEquals(PaperWidth.Mm80, state().printer.paperWidth)
        assertEquals("mock", state().printer.selectedId)
        assertEquals(DeviceInterface.PRINTER to DriverChoice("mock", mapOf("paperWidthMm" to "80")), registry.selectCalls.last())
    }

    @Test
    fun `the detected print services reach the printer card`() = runTest {
        scanner.services = listOf("EPSON TM-T20", "POS-58")
        val (_, state) = viewModel()

        assertEquals(listOf("EPSON TM-T20", "POS-58"), state().printer.printServices)
    }

    @Test
    fun `choosing a print service selects escpos on that printer and keeps the rest of the profile`() = runTest {
        val (vm, state) = viewModel()

        vm.onAction(DevicesAction.SelectPrintService("EPSON TM-T20"))

        assertEquals(
            DeviceInterface.PRINTER to DriverChoice("escpos", mapOf("printerName" to "EPSON TM-T20")),
            registry.selectCalls.single(),
        )
        assertEquals("EPSON TM-T20", state().printer.printServiceName)
    }

    @Test
    fun `a failing print service lookup keeps the serial ports, and a failing port lookup keeps the print services`() = runTest {
        scanner.services = listOf("POS-58")
        val (vm, state) = viewModel()
        assertEquals(listOf(com3), state().ports)

        scanner.servicesError = UnsatisfiedLinkError("no cups")
        vm.onAction(DevicesAction.RescanPorts)
        assertEquals(listOf(com3), state().ports)
        assertEquals(listOf("POS-58"), state().printer.printServices, "keeps the last good list")

        scanner.servicesError = null
        scanner.portsError = IllegalStateException("libusb exploded")
        vm.onAction(DevicesAction.RescanPorts)
        assertEquals(listOf("POS-58"), state().printer.printServices)
    }

    @Test
    fun `a regex that would hang the reader is refused before it can be applied`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))
        vm.onAction(DevicesAction.EditField(ScaleField.FrameRegex, "^(?<value>(\\2x|x)+y)(?<unit>z)"))

        vm.onAction(DevicesAction.Apply)

        assertTrue(registry.selectCalls.isEmpty(), "nothing slow reaches the driver")
        assertEquals(ScaleFieldError.FrameTooComplex, state().errors[ScaleField.FrameRegex])
    }

    @Test
    fun `a poll scale needs its command and interval, a continuous one does not`() = runTest {
        val (vm, state) = viewModel()
        vm.onAction(DevicesAction.SelectMode(ScaleModeOption.Serial))
        vm.onAction(DevicesAction.EditField(ScaleField.PollCommand, ""))
        assertFalse(ScaleField.PollCommand in state().errors)

        vm.onAction(DevicesAction.SelectBrand(ScaleBrand.Toledo))
        assertEquals(ScaleFieldError.Required, state().errors[ScaleField.PollCommand])
    }
}
