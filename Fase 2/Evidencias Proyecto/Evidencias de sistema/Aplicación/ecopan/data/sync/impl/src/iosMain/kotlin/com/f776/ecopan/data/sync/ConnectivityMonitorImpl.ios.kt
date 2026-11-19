@file:OptIn(ExperimentalForeignApi::class)

package com.f776.ecopan.data.sync

import com.f776.ecopan.domain.sync.ConnectivityMonitor
import com.f776.ecopan.domain.sync.ConnectivityStatus
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * Swift's `NWPathMonitor` class is Swift-only sugar over the Network framework; from Kotlin/Native
 * the framework is only reachable via the pre-imported C API in `platform.Network.*` (03 §6). Every
 * symbol/signature here (`nw_path_monitor_create()`, `nw_path_monitor_set_update_handler(monitor,
 * handler)` where `handler: (nw_path_t) -> Unit`, `nw_path_monitor_set_queue(monitor, queue)`,
 * `nw_path_monitor_start(monitor)`, `nw_path_get_status(path) -> nw_path_status_t`,
 * `nw_path_status_satisfied`, `nw_path_monitor_cancel(monitor)`) was re-verified via context7
 * against Apple's Network framework docs before writing this file, per 03 §6's explicit instruction.
 */
internal actual class ConnectivityMonitorImpl : ConnectivityMonitor {

    actual override fun observe(): Flow<ConnectivityStatus> = callbackFlow {
        val monitor = nw_path_monitor_create()
        val queue = dispatch_queue_create("com.f776.ecopan.sync.connectivity", null)
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_set_update_handler(monitor) { path ->
            val online = path != null && nw_path_get_status(path) == nw_path_status_satisfied
            trySend(if (online) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE)
        }
        nw_path_monitor_start(monitor)

        awaitClose { nw_path_monitor_cancel(monitor) }
    }.distinctUntilChanged()
}
