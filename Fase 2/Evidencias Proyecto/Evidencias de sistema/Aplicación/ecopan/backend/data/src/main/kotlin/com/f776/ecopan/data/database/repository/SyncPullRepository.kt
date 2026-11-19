package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.domain.SyncEntityType
import com.f776.ecopan.data.database.repository.model.SyncPullPage
import kotlin.uuid.Uuid

interface SyncPullRepository {
    /**
     * Merge-sorted (ascending `server_seq`) page across every synced table (02 §4.2): iterates the
     * per-table `WHERE tenant_id=? AND server_seq>? ORDER BY server_seq LIMIT ?` queries and merges.
     * [entityTypes] `null` means every type; otherwise only those tables are queried.
     * [branchId], when set, scopes the branch-scoped types (`FORECAST`) to that branch; the
     * tenant-wide types ignore it.
     */
    suspend fun pull(
        tenantId: Uuid,
        since: Long,
        limit: Int,
        entityTypes: Set<SyncEntityType>?,
        branchId: Uuid? = null
    ): SyncPullPage
}
