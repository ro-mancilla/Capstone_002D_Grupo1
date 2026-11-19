package com.f776.ecopan.feature.pos.unittest.fake

import com.f776.ecopan.domain.sync.BootstrapResult
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.sync.SyncResult
import com.f776.ecopan.domain.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class FakeSyncEngine(initial: SyncStatus = SyncStatus.Idle(ConnectivityStatus.ONLINE)) : SyncEngine {
    private val _status = MutableStateFlow(initial)
    override val status: Flow<SyncStatus> = _status.asStateFlow()

    var syncNowCalls: Int = 0
        private set

    fun emit(status: SyncStatus) {
        _status.value = status
    }

    override suspend fun syncNow(): SyncResult {
        syncNowCalls += 1
        return SyncResult(pushed = 0, pulled = 0, error = null)
    }

    override suspend fun bootstrap(): BootstrapResult = BootstrapResult(success = true, error = null)
}
