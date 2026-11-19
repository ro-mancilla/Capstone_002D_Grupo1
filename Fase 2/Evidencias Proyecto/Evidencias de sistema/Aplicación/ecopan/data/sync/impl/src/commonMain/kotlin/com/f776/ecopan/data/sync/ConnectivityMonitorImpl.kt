package com.f776.ecopan.data.sync

import com.f776.ecopan.domain.sync.ConnectivityMonitor
import com.f776.ecopan.domain.sync.ConnectivityStatus
import kotlinx.coroutines.flow.Flow

/**
 * Platform actuals for `ConnectivityMonitor` (03 §6, work item 6):
 * - androidMain: `ConnectivityManager.registerDefaultNetworkCallback`, gated on
 *   `NET_CAPABILITY_VALIDATED` to avoid captive-portal false positives (needs a Koin-injected
 *   `Context`, per the `data/reminder/impl` `ReminderSchedulerImpl` androidMain-constructor-param
 *   pattern this mirrors).
 * - iosMain: the `platform.Network.nw_path_monitor_*` C API (Swift's `NWPathMonitor` class is not
 *   visible from Kotlin/Native — symbol names/signatures re-verified via context7 before writing,
 *   per 03 §6's explicit instruction not to trust the plan's sketch as final).
 * - jvmMain: no OS-level callback worth depending on, so a periodic `GET /health` probe against
 *   the backend using the already-injected Ktor `HttpClient` stands in instead (03 §6: "desktop
 *   'ONLINE' means 'backend reachable', which is actually the more useful signal for sync anyway").
 *
 * Every actual wraps its platform callback/loop in `callbackFlow`/`flow` + `distinctUntilChanged`
 * (03 §6). The engine only ever treats this as a *hint* (triggers attempts, drives the indicator) —
 * a real push/pull failure while this reports `ONLINE` still backs off normally regardless.
 */
internal expect class ConnectivityMonitorImpl : ConnectivityMonitor {
    override fun observe(): Flow<ConnectivityStatus>
}
