package com.f776.ecopan.domain.sync

import kotlinx.coroutines.flow.Flow

enum class ConnectivityStatus { ONLINE, OFFLINE }

/** Implemented per-platform in `:data:sync:impl` (03 §6) — a hint that triggers sync attempts. */
fun interface ConnectivityMonitor {
    fun observe(): Flow<ConnectivityStatus>
}
