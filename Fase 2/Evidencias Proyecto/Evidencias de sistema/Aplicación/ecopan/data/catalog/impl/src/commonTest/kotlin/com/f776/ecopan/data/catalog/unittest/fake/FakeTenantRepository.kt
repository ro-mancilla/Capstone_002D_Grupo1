package com.f776.ecopan.data.catalog.unittest.fake

import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.uuid.Uuid

/** Minimal fake — `:data:catalog` only ever reads [observeTenant] to know "which tenant". */
internal class FakeTenantRepository : TenantRepository {
    private val tenant = MutableStateFlow<TenantProfile?>(null)

    override fun observeTenant() = tenant.asStateFlow()

    override suspend fun upsertTenant(profile: TenantProfile) {
        tenant.value = profile
    }

    override fun observeBranches() = throw NotImplementedError("unused by :data:catalog")
    override suspend fun upsertBranches(branches: List<BranchUpsert>) = throw NotImplementedError("unused by :data:catalog")
    override fun observeUsers() = throw NotImplementedError("unused by :data:catalog")
    override suspend fun upsertUsers(users: List<UserAccountUpsert>) = throw NotImplementedError("unused by :data:catalog")
    override suspend fun verifyPin(userId: Uuid, pin: String): Boolean = throw NotImplementedError("unused by :data:catalog")
    override suspend fun clear() = throw NotImplementedError("unused by :data:catalog")
}
