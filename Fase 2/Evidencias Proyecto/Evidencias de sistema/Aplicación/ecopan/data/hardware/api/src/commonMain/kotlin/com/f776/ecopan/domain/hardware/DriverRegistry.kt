package com.f776.ecopan.domain.hardware

import kotlinx.coroutines.flow.Flow

/**
 * Live-swap is the whole point (04 §1.2): [select] writes the profile to DataStore, and every
 * `Flow` below emits a fresh driver instance the moment its slice of the profile changes — the
 * previous instance is `close()`d. Changing a Settings row is therefore the entire "swap driver
 * with no restart" story; nothing downstream needs to know a swap happened beyond re-collecting
 * the flow it already held.
 */
interface DriverRegistry {
    val profile: Flow<DeviceProfile>

    val availableScaleDrivers: List<DriverDescriptor>
    val availablePrinterDrivers: List<DriverDescriptor>
    val availableDrawerDrivers: List<DriverDescriptor>
    val availableTerminalDrivers: List<DriverDescriptor>

    val scale: Flow<Scale>
    val printer: Flow<ReceiptPrinter>
    val drawer: Flow<CashDrawer>
    val terminal: Flow<PaymentTerminal>

    suspend fun select(iface: DeviceInterface, choice: DriverChoice)

    /**
     * Closes the active scale and opens a fresh instance of the same choice ("Probar de nuevo").
     * [select] with an unchanged choice does nothing by design, so a retry needs its own entry.
     */
    suspend fun reconnectScale()
}
