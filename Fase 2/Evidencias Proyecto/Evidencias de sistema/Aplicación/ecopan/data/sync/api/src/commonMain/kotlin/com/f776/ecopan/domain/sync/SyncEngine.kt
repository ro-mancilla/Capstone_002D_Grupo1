package com.f776.ecopan.domain.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

data class SyncResult(val pushed: Int, val pulled: Int, val error: SyncError?)

data class BootstrapResult(val success: Boolean, val error: SyncError?)

/** Implemented by `:data:sync:impl`'s `SyncEngineImpl`, bound in `SyncDataModule` (03 items 4/5/9). */
interface SyncEngine {
    val status: Flow<SyncStatus>

    /**
     * When this device last completed a pull from the server (persisted, so it survives a restart),
     * or null if it never has. Offline screens use it to say how old the data they show is.
     */
    val lastSyncedAt: Flow<Instant?> get() = flowOf(null)

    suspend fun syncNow(): SyncResult
    suspend fun bootstrap(): BootstrapResult
}

/** Implemented by `:data:sync:impl`'s `SyncSchedulerImpl`, bound in `SyncDataModule` (03 item 7). */
interface SyncScheduler {
    fun start()
    fun stop()
}
