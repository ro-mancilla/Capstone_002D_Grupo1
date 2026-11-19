@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.feature.settings.screens.settings.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.f776.ecopan.domain.hardware.DeviceDisplay
import com.f776.ecopan.domain.hardware.DeviceInterface
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.DriverRegistry
import com.f776.ecopan.domain.hardware.PaperWidth
import com.f776.ecopan.domain.hardware.RawTextSource
import com.f776.ecopan.domain.hardware.SerialFraming
import com.f776.ecopan.domain.hardware.DeviceDiscovery
import com.f776.ecopan.domain.hardware.DriverIds
import com.f776.ecopan.domain.hardware.PrinterConfig
import com.f776.ecopan.domain.hardware.SerialPortInfo
import com.f776.ecopan.domain.hardware.WeightReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import com.f776.ecopan.domain.hardware.defaultScalePort
import com.f776.ecopan.domain.hardware.deriveDeviceDisplay
import com.f776.ecopan.domain.hardware.userPorts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** What the live test shows of the running scale: the last reading and the last text the scale sent. */
internal data class LiveScaleUi(val grams: Long?, val stable: Boolean, val rawText: String?)

/** What the bottom bar says. The bar is absent when nothing differs from what is applied. */
internal sealed interface ApplyBar {
    data object Unapplied : ApplyBar

    /** Errors inside the collapsed "Avanzado" section, which a person cannot see. */
    data class HiddenErrors(val count: Int) : ApplyBar

    data class VisibleErrors(val count: Int) : ApplyBar
}

internal enum class DevicesNotice {
    /** Leaving "Balanza serial" threw away typed-but-unapplied serial settings. */
    SerialDraftDiscarded,
}

internal data class PrinterUi(
    val drivers: List<DriverDescriptor> = emptyList(),
    val selectedId: String = "",
    val paperWidth: PaperWidth = PaperWidth.Mm58,
    val display: DeviceDisplay = DeviceDisplay.Simulated,
    /** OS print services found for the `escpos` driver; empty on platforms with no real printer driver. */
    val printServices: List<String> = emptyList(),
    /** The chosen OS print service; blank means the OS default printer. */
    val printServiceName: String = "",
) {
    val simulated: Boolean get() = display == DeviceDisplay.Simulated

    /** "Sin impresora": the boleta only shows on screen. Not a simulated device. */
    val none: Boolean get() = display == DeviceDisplay.None
}

internal data class SimpleDeviceUi(
    val drivers: List<DriverDescriptor> = emptyList(),
    val selectedId: String = "",
    val display: DeviceDisplay = DeviceDisplay.Simulated,
    /** Which sentence the card shows; only the drawer varies it, with the printer behind it. */
    val text: DeviceText = DeviceText.Simulated,
) {
    val simulated: Boolean get() = display == DeviceDisplay.Simulated
}

internal data class DevicesUiState(
    val applied: ScaleDraft = ScaleDraft(),
    val draft: ScaleDraft = ScaleDraft(),
    val ports: List<SerialPortInfo> = emptyList(),
    val advancedExpanded: Boolean = false,
    val connection: ScaleConnection = ScaleConnection.Manual,
    val live: LiveScaleUi? = null,
    val probeInput: String = "",
    val notice: DevicesNotice? = null,
    val printer: PrinterUi = PrinterUi(),
    val drawer: SimpleDeviceUi = SimpleDeviceUi(),
    val terminal: SimpleDeviceUi = SimpleDeviceUi(),
) {
    val errors: Map<ScaleField, ScaleFieldError> = draft.validate()

    /** Something differs from what the running scale uses. */
    val dirty: Boolean get() = draft != applied

    /** Errors the person cannot see because "Avanzado" is collapsed. */
    val hiddenErrorCount: Int get() = if (advancedExpanded) 0 else errors.keys.count { it.advanced }

    val canApply: Boolean get() = dirty && errors.isEmpty()

    val bar: ApplyBar?
        get() = when {
            !dirty -> null
            hiddenErrorCount > 0 -> ApplyBar.HiddenErrors(hiddenErrorCount)
            errors.isNotEmpty() -> ApplyBar.VisibleErrors(errors.size)
            else -> ApplyBar.Unapplied
        }

    /** The pasted line read with the draft frame; `null` while nothing is pasted. */
    val probe: ProbeResult? get() = probeInput.takeIf { it.isNotBlank() }?.let { draft.serial.probe(it) }
}

internal sealed interface DevicesAction {
    data class SelectMode(val mode: ScaleModeOption) : DevicesAction
    data class SelectBrand(val brand: ScaleBrand) : DevicesAction
    data class EditField(val field: ScaleField, val value: String) : DevicesAction
    data class SelectFraming(val framing: SerialFraming) : DevicesAction
    data class SelectProtocol(val protocol: String) : DevicesAction
    data object RescanPorts : DevicesAction
    data object ToggleAdvanced : DevicesAction
    data object ResetAdvanced : DevicesAction
    data class ProbeInputChanged(val text: String) : DevicesAction
    data object Discard : DevicesAction
    data object Apply : DevicesAction
    data object RetryConnection : DevicesAction
    data object DismissNotice : DevicesAction
    data class SelectPrinter(val driverId: String) : DevicesAction
    data class SelectPaperWidth(val width: PaperWidth) : DevicesAction
    data class SelectPrintService(val name: String) : DevicesAction
    data class SelectDrawer(val driverId: String) : DevicesAction
    data class SelectTerminal(val driverId: String) : DevicesAction
}

/**
 * Settings → Dispositivos. The scale is edited as a draft and only reaches the running driver on
 * [DevicesAction.Apply] (the connection restarts, so it must not happen on every keystroke); the
 * printer, drawer and terminal rows apply as soon as they are chosen, as they always did.
 */
internal class DevicesViewModel(
    private val driverRegistry: DriverRegistry,
    private val deviceDiscovery: DeviceDiscovery,
) : ViewModel() {

    private data class Local(
        /** `null` = nothing edited: the draft is whatever is applied. */
        val draft: ScaleDraft? = null,
        val advancedExpanded: Boolean = false,
        val probeInput: String = "",
        val notice: DevicesNotice? = null,
        /** The serial settings as they were when the person entered "Balanza serial", before typing anything. */
        val serialBaseline: SerialDraft? = null,
    )

    /** Everything known about the running driver instance. */
    private data class Live(
        val status: DeviceStatus = DeviceStatus.DISCONNECTED,
        val reading: WeightReading? = null,
        /** The latest text received, parsed or not; only shown while no reading has arrived. */
        val lastRawText: String? = null,
        val waitingForFirstReading: Boolean = true,
    )

    private val local = MutableStateFlow(Local())

    /** What is physically attached; `scanned` tells "nothing found" apart from "not looked yet". */
    private data class Discovered(
        val ports: List<SerialPortInfo> = emptyList(),
        val printServices: List<String> = emptyList(),
        val scanned: Boolean = false,
    )

    private val discovered = MutableStateFlow(Discovered())
    private val ports: List<SerialPortInfo> get() = discovered.value.ports

    private val live: Flow<Live> = driverRegistry.scale.flatMapLatest { scale ->
        val readings: Flow<WeightReading?> = scale.readings.map<WeightReading, WeightReading?> { it }.onStart { emit(null) }
        val raw: Flow<String?> = (scale as? RawTextSource)?.lastRawText ?: flowOf(null)
        combine(scale.status, readings, raw, firstReadingWindow()) { status, reading, lastRawText, waiting ->
            Live(status, reading, lastRawText, waiting)
        }
    }.onStart { emit(Live()) }

    val uiState: StateFlow<DevicesUiState> = combine(
        driverRegistry.profile,
        live,
        discovered,
        local,
    ) { profile, live, found, local ->
        val ports = found.ports
        val scanned = found.scanned
        val applied = ScaleDraft.fromChoice(profile.scale)
        val draft = local.draft ?: applied
        val printerDescriptor = driverRegistry.availablePrinterDrivers.find(profile.printer.driverId)
        val drawerDescriptor = driverRegistry.availableDrawerDrivers.find(profile.drawer.driverId)
        val connection = deriveScaleConnection(
            mode = applied.mode,
            status = live.status,
            hasReading = live.reading != null,
            waitingForFirstReading = live.waitingForFirstReading,
            noPorts = scanned && ports.isEmpty(),
        )
        DevicesUiState(
            applied = applied,
            draft = draft,
            ports = ports,
            advancedExpanded = local.advancedExpanded,
            connection = connection,
            live = if (applied.mode == ScaleModeOption.Manual) null else deriveLiveScale(live.reading, live.lastRawText),
            probeInput = local.probeInput,
            notice = local.notice,
            printer = PrinterUi(
                drivers = driverRegistry.availablePrinterDrivers,
                selectedId = profile.printer.driverId,
                paperWidth = PaperWidth.fromConfig(profile.printer.config),
                display = deriveDeviceDisplay(printerDescriptor),
                printServices = found.printServices,
                printServiceName = profile.printer.config[PrinterConfig.KEY_NAME].orEmpty(),
            ),
            drawer = SimpleDeviceUi(
                drivers = driverRegistry.availableDrawerDrivers,
                selectedId = profile.drawer.driverId,
                display = deriveDeviceDisplay(drawerDescriptor, printer = printerDescriptor),
                text = deriveDrawerText(drawerDescriptor, printerDescriptor),
            ),
            terminal = SimpleDeviceUi(
                drivers = driverRegistry.availableTerminalDrivers,
                selectedId = profile.terminal.driverId,
                display = deriveDeviceDisplay(driverRegistry.availableTerminalDrivers.find(profile.terminal.driverId)),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds), DevicesUiState())

    init {
        rescan()
    }

    fun onAction(action: DevicesAction) {
        when (action) {
            is DevicesAction.SelectMode -> selectMode(action.mode)
            is DevicesAction.SelectBrand -> editSerial { it.withBrand(action.brand) }
            is DevicesAction.EditField -> editSerial { it.with(action.field, action.value) }
            is DevicesAction.SelectFraming -> editSerial { it.copy(framing = action.framing) }
            is DevicesAction.SelectProtocol -> editSerial { it.copy(protocol = action.protocol) }
            DevicesAction.RescanPorts -> rescan()
            DevicesAction.ToggleAdvanced -> local.update { it.copy(advancedExpanded = !it.advancedExpanded) }
            DevicesAction.ResetAdvanced -> editSerial { it.withAdvancedDefaults() }
            is DevicesAction.ProbeInputChanged -> local.update { it.copy(probeInput = action.text) }
            DevicesAction.Discard -> local.update { it.copy(draft = null, notice = null, serialBaseline = null) }
            DevicesAction.Apply -> apply()
            DevicesAction.RetryConnection -> viewModelScope.launch { driverRegistry.reconnectScale() }
            DevicesAction.DismissNotice -> local.update { it.copy(notice = null) }
            is DevicesAction.SelectPrinter -> selectPrinter(action.driverId)
            is DevicesAction.SelectPaperWidth -> selectPaperWidth(action.width)
            is DevicesAction.SelectPrintService -> selectPrintService(action.name)
            is DevicesAction.SelectDrawer -> select(DeviceInterface.DRAWER, DriverChoice(action.driverId))
            is DevicesAction.SelectTerminal -> select(DeviceInterface.TERMINAL, DriverChoice(action.driverId))
        }
    }

    /** The draft being edited, or the applied scale when nothing has been touched yet. */
    private val currentDraft: ScaleDraft get() = local.value.draft ?: uiState.value.applied

    private fun selectMode(mode: ScaleModeOption) {
        val state = uiState.value
        val draft = currentDraft
        if (mode == draft.mode) return
        val baseline = local.value.serialBaseline ?: state.applied.serial
        val leavingEditedSerial = draft.mode == ScaleModeOption.Serial && draft.serial != baseline
        if (mode == ScaleModeOption.Serial) {
            val serial = state.applied.serial.takeIf { draft.serial == state.applied.serial } ?: draft.serial
            val filled = if (serial.port.isBlank()) serial.copy(port = ports.defaultScalePort()?.name.orEmpty()) else serial
            local.update { it.copy(draft = ScaleDraft(mode, filled), serialBaseline = filled, notice = null) }
        } else {
            // Serial settings typed but never applied do not follow the person into another mode.
            local.update {
                it.copy(
                    draft = ScaleDraft(mode, state.applied.serial),
                    serialBaseline = null,
                    notice = if (leavingEditedSerial) DevicesNotice.SerialDraftDiscarded else null,
                )
            }
        }
    }

    private fun editSerial(transform: (SerialDraft) -> SerialDraft) {
        val draft = currentDraft
        local.update { it.copy(draft = draft.copy(serial = transform(draft.serial)), notice = null) }
    }

    private fun apply() {
        val state = uiState.value
        if (!state.canApply) return
        viewModelScope.launch {
            driverRegistry.select(DeviceInterface.SCALE, state.draft.toChoice())
            local.update { it.copy(draft = null, notice = null, serialBaseline = null) }
        }
    }

    /**
     * Independent lookups: a broken print subsystem must not hide the serial ports, and vice versa. A
     * failure keeps the last good list. Native enumeration can fail in ways well beyond `Exception`
     * (a missing JNI library is an `Error`), and an uncaught one in a `viewModelScope` coroutine
     * would take the app down; cancellation must still propagate.
     */
    private fun rescan() {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            // The one place ports are filtered: system and debug ports (macOS `cu.debug-console`,
            // Bluetooth, `tty.*` twins of a `cu.*`) are never a scale.
            lookup { deviceDiscovery.serialPorts() }?.let { found -> discovered.update { it.copy(ports = found.userPorts()) } }
            lookup { deviceDiscovery.printServices() }?.let { found -> discovered.update { it.copy(printServices = found) } }
            discovered.update { it.copy(scanned = true) }
        }
    }

    private var scanJob: Job? = null

    private suspend fun <T> lookup(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }

    private fun selectPrinter(driverId: String) {
        val config = uiState.value.printer.paperWidth.let { mapOf(PaperWidth.CONFIG_KEY to it.millimetres.toString()) }
        select(DeviceInterface.PRINTER, DriverChoice(driverId, config))
    }

    /** Selects the ESC/POS printer on the given OS print service; blank means the OS default printer. */
    private fun selectPrintService(name: String) {
        select(DeviceInterface.PRINTER, DriverChoice(DriverIds.ESCPOS_PRINTER, mapOf(PrinterConfig.KEY_NAME to name)))
    }

    private fun selectPaperWidth(width: PaperWidth) {
        select(
            DeviceInterface.PRINTER,
            DriverChoice(uiState.value.printer.selectedId, mapOf(PaperWidth.CONFIG_KEY to width.millimetres.toString())),
        )
    }

    private fun select(iface: DeviceInterface, choice: DriverChoice) {
        viewModelScope.launch { driverRegistry.select(iface, choice) }
    }

    private fun List<DriverDescriptor>.find(id: String): DriverDescriptor? = firstOrNull { it.id == id }

    /** `true` for [CONNECT_WINDOW] after a driver instance starts, then `false`: "hasta 10 s". */
    private fun firstReadingWindow(): Flow<Boolean> = flow {
        emit(true)
        delay(CONNECT_WINDOW)
        emit(false)
    }

    private companion object {
        val CONNECT_WINDOW = 10.seconds
    }
}
