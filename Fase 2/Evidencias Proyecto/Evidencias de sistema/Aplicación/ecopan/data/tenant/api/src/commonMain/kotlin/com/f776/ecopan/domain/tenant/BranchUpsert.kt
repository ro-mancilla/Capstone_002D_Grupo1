package com.f776.ecopan.domain.tenant

import kotlin.uuid.Uuid

/**
 * Write-side counterpart to [Branch]: carries the row's `serverSeq` in from the wire so it's
 * persisted for the sync layer's own bookkeeping (03), even though nothing reads it back out
 * through [TenantRepository.observeBranches] — ordering is already resolved server-side (01 §6.2).
 */
data class BranchUpsert(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val active: Boolean,
    val serverSeq: Long,
)
