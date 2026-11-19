package com.f776.ecopan.feature.auth.unittest.fake

import com.f776.ecopan.domain.sync.BootstrapResult
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncResult
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.ecopan.domain.sync.SyncEngine
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeSyncEngine : SyncEngine {
    override val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(ConnectivityStatus.ONLINE))

    var bootstrapResult: BootstrapResult = BootstrapResult(success = true, error = null)
    var syncNowResult: SyncResult = SyncResult(pushed = 0, pulled = 0, error = null)
    var bootstrapCallCount = 0
        private set

    override suspend fun syncNow(): SyncResult = syncNowResult

    override suspend fun bootstrap(): BootstrapResult {
        bootstrapCallCount++
        return bootstrapResult
    }
}
