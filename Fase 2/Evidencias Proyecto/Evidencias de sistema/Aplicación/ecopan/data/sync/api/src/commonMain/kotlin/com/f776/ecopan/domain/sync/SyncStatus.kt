package com.f776.ecopan.domain.sync

/** Consumed by the `:core:ui` network-state indicator (03 §6.1) — mapped, never used directly. */
sealed interface SyncStatus {
    val connectivity: ConnectivityStatus

    data class Idle(override val connectivity: ConnectivityStatus) : SyncStatus
    data class Syncing(override val connectivity: ConnectivityStatus) : SyncStatus
    data class Pending(val count: Int, override val connectivity: ConnectivityStatus) : SyncStatus
    data class AuthRequired(val pendingCount: Int, override val connectivity: ConnectivityStatus) : SyncStatus
    data class Error(
        val pendingCount: Int,
        val lastError: SyncError,
        override val connectivity: ConnectivityStatus,
    ) : SyncStatus
}
