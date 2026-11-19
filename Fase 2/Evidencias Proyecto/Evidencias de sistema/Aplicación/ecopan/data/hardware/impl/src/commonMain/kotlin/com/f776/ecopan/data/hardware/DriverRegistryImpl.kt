@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.hardware

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import com.f776.ecopan.domain.hardware.CashDrawer
import com.f776.ecopan.domain.hardware.DeviceInterface
import com.f776.ecopan.domain.hardware.DeviceProfile
import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.DriverRegistry
import com.f776.ecopan.domain.hardware.PaymentTerminal
import com.f776.ecopan.domain.hardware.PrinterConfig
import com.f776.ecopan.domain.hardware.ReceiptPrinter
import com.f776.ecopan.domain.hardware.Scale
import com.f776.ecopan.domain.hardware.SerialScaleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.io.IOException

/**
 * Live-swap mechanics (04 §1.2): [scale]/[printer]/[drawer]/[terminal] each re-derive from
 * [profile], emitting a fresh driver instance whenever their slice of the profile changes.
 * [Scale] is the only [AutoCloseable] driver, so it is the only one whose previous instance gets
 * explicitly `close()`d on swap. An unknown/unavailable `driverId` (e.g. a synced `serial` setting
 * on a platform with no serial driver bound) falls back to the interface's default id and logs a
 * warning instead of crashing.
 */
internal class DriverRegistryImpl(
    private val dataStore: DataStore<Preferences>,
    private val scaleFactories: List<ScaleDriverFactory>,
    private val printerFactories: List<PrinterDriverFactory>,
    private val drawerFactories: List<DrawerDriverFactory>,
    private val terminalFactories: List<TerminalDriverFactory>,
    private val ioCoroutineScope: CoroutineScope,
) : DriverRegistry {

    override val profile: Flow<DeviceProfile> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            DeviceProfile(
                scale = DriverChoice(
                    driverId = prefs[SCALE_DRIVER_KEY] ?: DEFAULT_SCALE_DRIVER,
                    config = buildMap {
                        prefs[SCALE_MODE_KEY]?.let { put("mode", it) }
                        prefs[SCALE_SEQUENCE_KEY]?.let { put("sequence", it) }
                        prefs[SCALE_MAX_GRAMS_KEY]?.let { put("maxGrams", it) }
                        prefs[SCALE_SCRIPT_KEY]?.let { put(CONFIG_SCRIPT, it) }
                        SerialScaleSettings.CONFIG_KEYS.forEach { key -> prefs[serialKey(key)]?.let { put(key, it) } }
                    },
                ),
                printer = DriverChoice(
                    driverId = prefs[PRINTER_DRIVER_KEY] ?: DEFAULT_PRINTER_DRIVER,
                    config = buildMap {
                        prefs[PRINTER_NAME_KEY]?.let { put(PrinterConfig.KEY_NAME, it) }
                        prefs[PRINTER_PAPER_WIDTH_KEY]?.let { put(CONFIG_PAPER_WIDTH_MM, it) }
                    },
                ),
                drawer = DriverChoice(prefs[DRAWER_DRIVER_KEY] ?: DEFAULT_DRAWER_DRIVER),
                terminal = DriverChoice(prefs[TERMINAL_DRIVER_KEY] ?: DEFAULT_TERMINAL_DRIVER),
            )
        }

    override val availableScaleDrivers: List<DriverDescriptor> = scaleFactories.map { it.descriptor }
    override val availablePrinterDrivers: List<DriverDescriptor> = printerFactories.map { it.descriptor }
    override val availableDrawerDrivers: List<DriverDescriptor> = drawerFactories.map { it.descriptor }
    override val availableTerminalDrivers: List<DriverDescriptor> = terminalFactories.map { it.descriptor }

    /** Bumped by [reconnectScale]; folded into the scale flow so an unchanged choice still gets a fresh instance. */
    private val scaleRestarts = MutableStateFlow(0)

    override val scale: Flow<Scale> = combine(profile.map { it.scale }.distinctUntilChanged(), scaleRestarts) { choice, _ -> choice }
        .flatMapLatest { choice -> swappingScale(choice) }
        .shareIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), replay = 1)

    override val printer: Flow<ReceiptPrinter> = profile.map { it.printer }
        .distinctUntilChanged()
        .map { choice -> factoryFor(choice, printerFactories, DEFAULT_PRINTER_DRIVER).create(choice.config) }
        .shareIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), replay = 1)

    override val drawer: Flow<CashDrawer> = profile.map { it.drawer }
        .distinctUntilChanged()
        .map { choice -> factoryFor(choice, drawerFactories, DEFAULT_DRAWER_DRIVER).create(choice.config) }
        .shareIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), replay = 1)

    override val terminal: Flow<PaymentTerminal> = profile.map { it.terminal }
        .distinctUntilChanged()
        .map { choice -> factoryFor(choice, terminalFactories, DEFAULT_TERMINAL_DRIVER).create(choice.config) }
        .shareIn(ioCoroutineScope, SharingStarted.WhileSubscribed(), replay = 1)

    override suspend fun select(iface: DeviceInterface, choice: DriverChoice) {
        dataStore.edit { prefs ->
            when (iface) {
                DeviceInterface.SCALE -> {
                    prefs[SCALE_DRIVER_KEY] = choice.driverId
                    choice.config["mode"]?.let { prefs[SCALE_MODE_KEY] = it }
                    choice.config["sequence"]?.let { prefs[SCALE_SEQUENCE_KEY] = it }
                    choice.config["maxGrams"]?.let { prefs[SCALE_MAX_GRAMS_KEY] = it }
                    prefs.putOrRemove(SCALE_SCRIPT_KEY, choice.config, CONFIG_SCRIPT)
                    // The serial block is written as a unit: a key the new settings no longer carry
                    // is removed so it reverts to its default instead of lingering from a past edit.
                    // A choice with no serial keys at all (e.g. a plain driver switch) leaves it alone.
                    if (choice.config.keys.any { it in SerialScaleSettings.CONFIG_KEYS }) {
                        SerialScaleSettings.CONFIG_KEYS.forEach { key ->
                            val value = choice.config[key]
                            if (value != null) prefs[serialKey(key)] = value else prefs.remove(serialKey(key))
                        }
                    }
                }
                DeviceInterface.PRINTER -> {
                    prefs[PRINTER_DRIVER_KEY] = choice.driverId
                    prefs.putOrRemove(PRINTER_NAME_KEY, choice.config, PrinterConfig.KEY_NAME)
                    prefs.putOrRemove(PRINTER_PAPER_WIDTH_KEY, choice.config, CONFIG_PAPER_WIDTH_MM)
                }
                DeviceInterface.DRAWER -> prefs[DRAWER_DRIVER_KEY] = choice.driverId
                DeviceInterface.TERMINAL -> prefs[TERMINAL_DRIVER_KEY] = choice.driverId
            }
        }
    }

    override suspend fun reconnectScale() {
        scaleRestarts.update { it + 1 }
    }

    /**
     * `emit` returns as soon as the value is delivered, so without [awaitCancellation] this flow
     * would complete (running `finally` -> closing the driver) right after emitting instead of
     * staying open for the driver's whole active lifetime. It must only ever finish by being
     * cancelled -- which is exactly what `flatMapLatest` does the moment a new distinct choice
     * arrives, so `close()` fires precisely on swap, never before.
     */
    private fun swappingScale(choice: DriverChoice): Flow<Scale> = flow {
        val instance = factoryFor(choice, scaleFactories, DEFAULT_SCALE_DRIVER).create(choice.config)
        try {
            instance.start()
            emit(instance)
            awaitCancellation()
        } finally {
            instance.close()
        }
    }

    private fun <F : DriverFactory> factoryFor(choice: DriverChoice, factories: List<F>, fallbackId: String): F {
        factories.find { it.descriptor.id == choice.driverId }?.let { return it }
        Logger.w { "Hardware driver '${choice.driverId}' unavailable on this platform; falling back to '$fallbackId'" }
        return factories.find { it.descriptor.id == fallbackId }
            ?: error("no driver registered for fallback id '$fallbackId' either -- misconfigured DI graph")
    }

    private companion object {
        const val DEFAULT_SCALE_DRIVER = "manual"
        const val DEFAULT_PRINTER_DRIVER = "mock"
        const val DEFAULT_DRAWER_DRIVER = "mock"
        const val DEFAULT_TERMINAL_DRIVER = "mock"

        val SCALE_DRIVER_KEY = stringPreferencesKey("hw.scale.driver")
        val SCALE_MODE_KEY = stringPreferencesKey("hw.scale.mode")
        val SCALE_SEQUENCE_KEY = stringPreferencesKey("hw.scale.sequence")
        val SCALE_MAX_GRAMS_KEY = stringPreferencesKey("hw.scale.maxGrams")

        fun serialKey(key: String) = stringPreferencesKey("hw.scale.serial.$key")
        val PRINTER_DRIVER_KEY = stringPreferencesKey("hw.printer.driver")
        val PRINTER_NAME_KEY = stringPreferencesKey("hw.printer.name")
        val PRINTER_PAPER_WIDTH_KEY = stringPreferencesKey("hw.printer.paperWidthMm")
        val SCALE_SCRIPT_KEY = stringPreferencesKey("hw.scale.script")

        // Passed through to the mock drivers (demo mode): a scripted weight sequence and the paper
        // width the on-screen receipt preview is drawn at.
        const val CONFIG_SCRIPT = "script"
        const val CONFIG_PAPER_WIDTH_MM = "paperWidthMm"

        /** Sets [key] from [config]'s [configKey] when present (blank removes it); an absent entry leaves it untouched. */
        fun MutablePreferences.putOrRemove(key: Preferences.Key<String>, config: Map<String, String>, configKey: String) {
            val value = config[configKey] ?: return
            if (value.isBlank()) remove(key) else this[key] = value
        }
        val DRAWER_DRIVER_KEY = stringPreferencesKey("hw.drawer.driver")
        val TERMINAL_DRIVER_KEY = stringPreferencesKey("hw.terminal.driver")
    }
}
