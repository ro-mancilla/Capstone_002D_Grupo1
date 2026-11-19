package com.f776.ecopan.data.hardware.jvm

import com.f776.ecopan.domain.hardware.CashDrawer
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.DriverRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * The physical drawer has no port of its own -- it kicks off the printer (04 §2, spec §6.1).
 * [open] delegates to the currently active [DriverRegistry.printer]'s `kickDrawer()`; [status]
 * mirrors that same printer's status, live, as the printer driver is swapped.
 *
 * [registryProvider] is a deferred lookup (not a direct `DriverRegistry` constructor param) so the
 * Koin factory binding this driver doesn't eagerly resolve `DriverRegistry` while `DriverRegistry`
 * itself is still being assembled from `getAll<DrawerDriverFactory>()` -- by the time [open]/[status]
 * actually call it, the registry singleton is fully constructed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PrinterKickDrawer(
    registryProvider: () -> DriverRegistry,
    ioCoroutineScope: CoroutineScope,
) : CashDrawer {

    private val registry by lazy(registryProvider)

    // Eagerly, not WhileSubscribed: this must reflect the active printer's status the instant it's
    // read (e.g. right after construction, before the POS screen subscribes to anything).
    override val status: StateFlow<DeviceStatus> = registry.printer
        .flatMapLatest { it.status }
        .stateIn(ioCoroutineScope, SharingStarted.Eagerly, DeviceStatus.DISCONNECTED)

    override suspend fun open() {
        registry.printer.first().kickDrawer()
    }
}
