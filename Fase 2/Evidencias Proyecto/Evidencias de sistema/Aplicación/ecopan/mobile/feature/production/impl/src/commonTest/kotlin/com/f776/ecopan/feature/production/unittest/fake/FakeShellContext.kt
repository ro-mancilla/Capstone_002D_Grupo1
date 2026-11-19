package com.f776.ecopan.feature.production.unittest.fake

import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.feature.shell.ShellContext
import com.f776.ecopan.feature.shell.ShiftContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Replaces the local tenant, session and sync-engine fakes these screens used to need. */
internal class FakeShellContext(
    branchId: Uuid? = null,
    userId: Uuid? = null,
    tenantName: String = "",
    tenantSlug: String = "",
) : ShellContext {
    private val shiftFlow = MutableStateFlow(ShiftContext(branchId, userId, tenantName, tenantSlug))
    private val indicatorFlow = MutableStateFlow<SyncIndicatorState>(SyncIndicatorState.OnlineIdle)

    override val shift: Flow<ShiftContext> = shiftFlow
    override val indicator: Flow<SyncIndicatorState> = indicatorFlow

    private val lastSyncedFlow = MutableStateFlow<Instant?>(null)
    override val lastSyncedAt: Flow<Instant?> = lastSyncedFlow

    fun setLastSyncedAt(at: Instant?) {
        lastSyncedFlow.value = at
    }

    var syncNowCalls = 0
        private set

    fun setIndicator(state: SyncIndicatorState) {
        indicatorFlow.value = state
    }

    fun setTenantName(name: String) {
        shiftFlow.value = shiftFlow.value.copy(tenantName = name)
    }

    override suspend fun syncNow() {
        syncNowCalls++
    }
}
