package com.f776.ecopan.feature.auth.unittest.fake

import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

internal class FakeTenantRepository : TenantRepository {
    val tenant = MutableStateFlow<TenantProfile?>(null)
    val branches = MutableStateFlow<List<Branch>>(emptyList())
    val users = MutableStateFlow<List<UserAccount>>(emptyList())

    /** Keyed by user id, mirrors the hash the impl would never surface through [observeUsers]. */
    val pinHashesByUserId = mutableMapOf<Uuid, String>()
    var verifyPinResultOverride: Boolean? = null

    override fun observeTenant() = tenant
    override suspend fun upsertTenant(profile: TenantProfile) {
        tenant.value = profile
    }

    override fun observeBranches() = branches
    override suspend fun upsertBranches(branches: List<BranchUpsert>) {
        this.branches.value = this.branches.value + branches.map { Branch(it.id, it.tenantId, it.name, it.active) }
    }

    override fun observeUsers() = users
    override suspend fun upsertUsers(users: List<UserAccountUpsert>) {
        users.forEach { u -> u.pinHash?.let { pinHashesByUserId[u.id] = it } }
        this.users.value = this.users.value + users.map {
            UserAccount(it.id, it.tenantId, it.branchId, it.username, it.displayName, it.role, it.active, hasPin = it.pinHash != null)
        }
    }

    override suspend fun verifyPin(userId: Uuid, pin: String): Boolean {
        verifyPinResultOverride?.let { return it }
        return pinHashesByUserId[userId] == pin
    }

    override suspend fun clear() {
        tenant.value = null
        branches.value = emptyList()
        users.value = emptyList()
    }
}
