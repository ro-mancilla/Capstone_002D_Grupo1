package com.f776.ecopan.domain.tenant

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

/**
 * Read-mostly cache of the tenant's config surface (01 §2/§6.2: server-authoritative,
 * last-write-wins). `upsert*` is how both the initial full pull (03 §8 bootstrap) and later
 * incremental sync rows are applied — Room's upsert-per-row semantics cover both without needing
 * a separate bulk-replace path, since the backend never deletes config rows (soft `active=false`
 * upserts only, 02 §1).
 */
interface TenantRepository {
    fun observeTenant(): Flow<TenantProfile?>
    suspend fun upsertTenant(profile: TenantProfile)

    fun observeBranches(): Flow<List<Branch>>
    suspend fun upsertBranches(branches: List<BranchUpsert>)

    fun observeUsers(): Flow<List<UserAccount>>
    suspend fun upsertUsers(users: List<UserAccountUpsert>)

    /**
     * Verifies [pin] against [userId]'s cached hash (01 §4 device session + PIN unlock) without
     * ever surfacing the hash itself outside this repository. Returns `false` for an unknown user
     * or one with no PIN set, same as a wrong PIN — callers can't distinguish "no PIN" from
     * "wrong PIN" from this alone, which is intentional (no user-enumeration signal at the lock
     * screen).
     */
    suspend fun verifyPin(userId: Uuid, pin: String): Boolean

    /** Wipes the cached tenant/branches/users — used on logout or device re-enrollment. */
    suspend fun clear()
}
