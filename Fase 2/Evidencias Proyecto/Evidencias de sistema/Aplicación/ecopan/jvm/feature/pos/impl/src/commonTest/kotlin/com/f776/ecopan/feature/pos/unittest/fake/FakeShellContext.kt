package com.f776.ecopan.feature.pos.unittest.fake

import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.feature.shell.ShellContext
import com.f776.ecopan.feature.shell.ShiftContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

/** Replaces the till's local tenant and session fakes, and the indicator half of its sync fake. */
internal class FakeShellContext(
    branchId: Uuid? = null,
    userId: Uuid? = null,
    tenantName: String = "",
    tenantSlug: String = "",
    branchName: String = "",
) : ShellContext {
    private val shiftFlow = MutableStateFlow(ShiftContext(branchId, userId, tenantName, tenantSlug, branchName))
    private val indicatorFlow = MutableStateFlow<SyncIndicatorState>(SyncIndicatorState.OnlineIdle)

    override val shift: Flow<ShiftContext> = shiftFlow
    override val indicator: Flow<SyncIndicatorState> = indicatorFlow

    var syncNowCalls = 0
        private set

    fun setIndicator(state: SyncIndicatorState) {
        indicatorFlow.value = state
    }

    fun setUser(userId: Uuid?) {
        shiftFlow.value = shiftFlow.value.copy(userId = userId)
    }

    override suspend fun syncNow() {
        syncNowCalls++
    }
}
