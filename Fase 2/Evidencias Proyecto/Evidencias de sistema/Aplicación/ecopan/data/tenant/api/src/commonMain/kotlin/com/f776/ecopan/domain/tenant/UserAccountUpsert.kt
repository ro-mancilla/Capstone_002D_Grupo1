package com.f776.ecopan.domain.tenant

import com.f776.ecopan.core.domain.auth.Role
import kotlin.uuid.Uuid

/**
 * Write-side counterpart to [UserAccount]: carries the synced `pinHash` in from the wire (02 §4.2
 * `USER` pull payload) so it can be persisted for local unlock verification. Deliberately not the
 * read type — [TenantRepository.observeUsers] must never hand the hash back out to callers.
 */
data class UserAccountUpsert(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid?,
    val username: String,
    val displayName: String,
    val role: Role,
    val active: Boolean,
    val pinHash: String?,
    val serverSeq: Long,
)
