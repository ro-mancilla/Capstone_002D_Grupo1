package com.f776.ecopan.unittest.fake

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

/** Only [observeTenant] matters to `AppViewModel` — it is the session-ready signal (03 §7). */
internal class FakeTenantRepository(initialTenant: TenantProfile? = null) : TenantRepository {

    private val tenant = MutableStateFlow(initialTenant)

    fun enroll() {
        tenant.value = TenantProfile(
            id = Uuid.random(),
            slug = "el-sol",
            name = "Panadería El Sol",
            currencyCode = "CLP",
            enabledModules = setOf(EntitlementModule.POS_SALES),
        )
    }

    override fun observeTenant(): Flow<TenantProfile?> = tenant

    override suspend fun upsertTenant(profile: TenantProfile) {
        tenant.value = profile
    }

    override fun observeBranches(): Flow<List<Branch>> = MutableStateFlow(emptyList())

    override suspend fun upsertBranches(branches: List<BranchUpsert>) = Unit

    override fun observeUsers(): Flow<List<UserAccount>> = MutableStateFlow(emptyList())

    override suspend fun upsertUsers(users: List<UserAccountUpsert>) = Unit

    override suspend fun verifyPin(userId: Uuid, pin: String): Boolean = false

    override suspend fun clear() {
        tenant.value = null
    }
}
