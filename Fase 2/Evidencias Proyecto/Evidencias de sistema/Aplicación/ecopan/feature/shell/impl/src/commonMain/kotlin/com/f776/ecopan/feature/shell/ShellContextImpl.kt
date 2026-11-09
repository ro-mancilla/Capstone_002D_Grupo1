package com.f776.ecopan.feature.shell

import com.f776.core.designsystem.component.SyncIndicatorState
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.ecopan.domain.tenant.SessionProvider
import com.f776.ecopan.domain.tenant.TenantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal class ShellContextImpl(
    private val tenantRepository: TenantRepository,
    private val sessionProvider: SessionProvider,
    private val syncEngine: SyncEngine,
) : ShellContext {

    override val shift: Flow<ShiftContext> = combine(
        tenantRepository.observeBranches(),
        sessionProvider.currentUserId,
        tenantRepository.observeTenant(),
    ) { branches, userId, tenant ->
        ShiftContext(
            // A device caches exactly one branch (01 §4-style single-tenant device), so the first
            // one is the one being worked. Written here once instead of in three ViewModels.
            branchId = branches.firstOrNull()?.id,
            userId = userId,
            tenantName = tenant?.name.orEmpty(),
            tenantSlug = tenant?.slug.orEmpty(),
            branchName = branches.firstOrNull()?.name.orEmpty(),
        )
    }

    override val indicator: Flow<SyncIndicatorState> = syncEngine.status.map { it.toIndicatorState() }

    override val lastSyncedAt: Flow<Instant?> = syncEngine.lastSyncedAt

    override suspend fun syncNow() {
        syncEngine.syncNow()
    }
}

/**
 * `SyncStatus -> SyncIndicatorState` (03 §6.1), once. `:core:ui` is domain-free by design so the
 * translation cannot live beside the indicator itself, but it does not follow that every consumer
 * must own a copy — this tier can see both sides.
 */
internal fun SyncStatus.toIndicatorState(): SyncIndicatorState = when (this) {
    // Offline with an empty outbox is still offline: "Sincronizado" there would claim the data is
    // current when the device simply cannot ask.
    is SyncStatus.Idle ->
        if (connectivity == ConnectivityStatus.OFFLINE) SyncIndicatorState.OfflinePending(0) else SyncIndicatorState.OnlineIdle
    is SyncStatus.Syncing -> SyncIndicatorState.Syncing
    is SyncStatus.Pending -> SyncIndicatorState.OfflinePending(count)
    is SyncStatus.AuthRequired -> SyncIndicatorState.Attention(pendingCount)
    is SyncStatus.Error -> SyncIndicatorState.Attention(pendingCount)
}
