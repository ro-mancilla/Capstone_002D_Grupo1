package com.f776.ecopan.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.f776.ecopan.domain.sync.ConnectivityMonitor
import com.f776.ecopan.domain.sync.ConnectivityStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * `registerDefaultNetworkCallback` follows the currently-active default network (whichever one
 * the system picked, wifi/cellular/etc) and reports `ONLINE` only once it is `NET_CAPABILITY_VALIDATED`
 * (has actually reached the internet, not just associated with a captive-portal Wi-Fi AP) — 03 §6.
 */
internal actual class ConnectivityMonitorImpl(private val context: Context) : ConnectivityMonitor {

    actual override fun observe(): Flow<ConnectivityStatus> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                trySend(ConnectivityStatus.OFFLINE)
            }

            override fun onUnavailable() {
                trySend(ConnectivityStatus.OFFLINE)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(connectivityFrom(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)))
            }
        }

        // The callback only speaks when something *changes*. Starting with no network at all
        // changes nothing, so nothing was ever sent and the engine's optimistic default (online)
        // stood: "Sincronizado" on a phone in airplane mode. Say where we are before listening.
        val current = connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
        trySend(connectivityFrom(current?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)))

        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

/** [validated] is null when there is no active network to ask, which is offline like any other. */
internal fun connectivityFrom(validated: Boolean?): ConnectivityStatus =
    if (validated == true) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE
