package com.f776.ecopan.feature.shell.unittest

import com.f776.ecopan.domain.sync.BootstrapResult
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.sync.SyncResult
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.SessionProvider
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import com.f776.ecopan.domain.tenant.TenantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

internal class FakeSyncEngine : SyncEngine {
    private val statusFlow = MutableStateFlow<SyncStatus>(SyncStatus.Idle(ConnectivityStatus.ONLINE))
    override val status: Flow<SyncStatus> = statusFlow
    val lastSynced = MutableStateFlow<kotlin.time.Instant?>(null)
    override val lastSyncedAt: Flow<kotlin.time.Instant?> = lastSynced
    var syncNowCalls = 0
        private set

    fun emit(status: SyncStatus) {
        statusFlow.value = status
    }

    override suspend fun syncNow(): SyncResult {
        syncNowCalls++
        return SyncResult(pushed = 0, pulled = 0, error = null)
    }

    override suspend fun bootstrap(): BootstrapResult = error("not needed by ShellContextImplTest")
}

internal class FakeTenantRepository : TenantRepository {
    private val tenantId = Uuid.random()
    private val branches = MutableStateFlow<List<Branch>>(emptyList())
    private val tenant = MutableStateFlow<TenantProfile?>(null)

    fun setBranches(vararg ids: Uuid) {
        branches.value = ids.map { Branch(id = it, tenantId = tenantId, name = "Branch", active = true) }
    }

    fun setTenantName(name: String) {
        tenant.value = TenantProfile(
            id = tenantId,
            slug = "reference",
            name = name,
            currencyCode = "CLP",
            enabledModules = emptySet(),
        )
    }

    override fun observeTenant(): Flow<TenantProfile?> = tenant
    override fun observeBranches(): Flow<List<Branch>> = branches

    override suspend fun upsertTenant(profile: TenantProfile) = error("not needed by ShellContextImplTest")
    override suspend fun upsertBranches(branches: List<BranchUpsert>) = error("not needed by ShellContextImplTest")
    override fun observeUsers(): Flow<List<UserAccount>> = error("not needed by ShellContextImplTest")
    override suspend fun upsertUsers(users: List<UserAccountUpsert>) = error("not needed by ShellContextImplTest")
    override suspend fun verifyPin(userId: Uuid, pin: String): Boolean = error("not needed by ShellContextImplTest")
    override suspend fun clear() = error("not needed by ShellContextImplTest")
}

internal class FakeSessionProvider : SessionProvider {
    private val userId = MutableStateFlow<Uuid?>(null)
    override val currentUserId: Flow<Uuid?> = userId

    fun setUser(id: Uuid?) {
        userId.value = id
    }

    override suspend fun unlock(userId: Uuid) = error("not needed by ShellContextImplTest")
    override suspend fun lock() = error("not needed by ShellContextImplTest")
}
