package com.f776.ecopan.data.inventory.unittest.fake

import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Minimal fake — `:data:inventory` only ever reads [observeTenant] to know "which tenant". */
internal class FakeTenantRepository : TenantRepository {
    private val tenant = MutableStateFlow<TenantProfile?>(null)

    override fun observeTenant() = tenant.asStateFlow()

    override suspend fun upsertTenant(profile: TenantProfile) {
        tenant.value = profile
    }

    override fun observeBranches() = throw NotImplementedError("unused by :data:inventory")
    override suspend fun upsertBranches(branches: List<BranchUpsert>) = throw NotImplementedError("unused by :data:inventory")
    override fun observeUsers() = throw NotImplementedError("unused by :data:inventory")
    override suspend fun upsertUsers(users: List<UserAccountUpsert>) = throw NotImplementedError("unused by :data:inventory")
    override suspend fun verifyPin(userId: kotlin.uuid.Uuid, pin: String) = throw NotImplementedError("unused by :data:inventory")
    override suspend fun clear() = throw NotImplementedError("unused by :data:inventory")
}
