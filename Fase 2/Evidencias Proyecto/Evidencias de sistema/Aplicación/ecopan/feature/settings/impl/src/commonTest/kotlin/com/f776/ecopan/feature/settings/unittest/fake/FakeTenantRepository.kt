package com.f776.ecopan.feature.settings.unittest.fake

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

class FakeTenantRepository(
    initialTenant: TenantProfile? = null,
    initialBranches: List<Branch> = emptyList(),
) : TenantRepository {
    val tenant = MutableStateFlow(initialTenant)
    val branches = MutableStateFlow(initialBranches)

    override fun observeTenant() = tenant
    override suspend fun upsertTenant(profile: TenantProfile) { tenant.value = profile }

    override fun observeBranches() = branches
    override suspend fun upsertBranches(branches: List<BranchUpsert>) = error("not needed by SettingsViewModelTest")

    override fun observeUsers() = MutableStateFlow(emptyList<UserAccount>())
    override suspend fun upsertUsers(users: List<UserAccountUpsert>) = error("not needed by SettingsViewModelTest")

    override suspend fun verifyPin(userId: Uuid, pin: String): Boolean = false

    override suspend fun clear() {
        tenant.value = null
        branches.value = emptyList()
    }

    companion object {
        fun profile(name: String) = TenantProfile(
            id = Uuid.random(),
            slug = "reference",
            name = name,
            currencyCode = "CLP",
            enabledModules = emptySet<EntitlementModule>(),
        )
    }
}
