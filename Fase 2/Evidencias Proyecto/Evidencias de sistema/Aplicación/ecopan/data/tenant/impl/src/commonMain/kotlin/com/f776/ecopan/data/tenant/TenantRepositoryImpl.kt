@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.tenant

import com.f776.ecopan.core.crypto.PinVerifier
import com.f776.ecopan.data.tenant.local.mapper.toDomain
import com.f776.ecopan.data.tenant.local.mapper.toEntity
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import com.f776.unigrid.core.database.dao.BranchDao
import com.f776.unigrid.core.database.dao.TenantDao
import com.f776.unigrid.core.database.dao.UserDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/**
 * A device caches exactly one tenant at a time (01 §4) — [observeBranches]/[observeUsers] derive
 * "which tenant" reactively from [observeTenant] rather than needing a separate session concept,
 * and read as empty until a tenant has been cached (e.g. before the first bootstrap pull, 03 §8).
 */
internal class TenantRepositoryImpl(
    private val tenantDao: TenantDao,
    private val branchDao: BranchDao,
    private val userDao: UserDao,
    private val pinVerifier: PinVerifier,
) : TenantRepository {

    override fun observeTenant(): Flow<TenantProfile?> =
        tenantDao.observe().map { it?.toDomain() }

    override suspend fun upsertTenant(profile: TenantProfile) {
        tenantDao.upsert(profile.toEntity())
    }

    override fun observeBranches(): Flow<List<Branch>> =
        observeTenant().flatMapLatest { tenant ->
            if (tenant == null) {
                flowOf(emptyList())
            } else {
                branchDao.observeByTenant(tenant.id).map { list -> list.map { it.toDomain() } }
            }
        }

    override suspend fun upsertBranches(branches: List<BranchUpsert>) {
        branchDao.upsertAll(branches.map { it.toEntity() })
    }

    override fun observeUsers(): Flow<List<UserAccount>> =
        observeTenant().flatMapLatest { tenant ->
            if (tenant == null) {
                flowOf(emptyList())
            } else {
                userDao.observeByTenant(tenant.id).map { list -> list.map { it.toDomain() } }
            }
        }

    override suspend fun upsertUsers(users: List<UserAccountUpsert>) {
        userDao.upsertAll(users.map { it.toEntity() })
    }

    override suspend fun verifyPin(userId: Uuid, pin: String): Boolean {
        val hash = userDao.findById(userId)?.pinHash ?: return false
        return pinVerifier.verify(pin, hash)
    }

    override suspend fun clear() {
        tenantDao.clear()
        branchDao.clear()
        userDao.clear()
    }
}
