package com.f776.ecopan.data.sync.unittest.fake

import com.f776.ecopan.domain.sync.BootstrapResult
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.sync.SyncResult
import com.f776.ecopan.domain.sync.SyncStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeSyncEngine : SyncEngine {
    override val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(ConnectivityStatus.ONLINE))

    /** Simulates a slow in-flight sync, so scheduler tests can assert coalescing (03 §7). */
    var syncDelayMillis: Long = 0

    val syncNowCalls = mutableListOf<Unit>()

    override suspend fun syncNow(): SyncResult {
        syncNowCalls += Unit
        if (syncDelayMillis > 0) delay(syncDelayMillis)
        return SyncResult(pushed = 0, pulled = 0, error = null)
    }

    override suspend fun bootstrap(): BootstrapResult = BootstrapResult(success = true, error = null)
}
