package com.f776.ecopan.api.feature.sync.domain

import com.f776.ecopan.api.feature.sync.controller.dto.PullResponse
import com.f776.ecopan.data.domain.SyncEntityType
import kotlin.uuid.Uuid

/** S2 pull-read (02 §4.2) -- kept separate from [SyncIngestService] per the plan's file-plan note. */
interface SyncPullService {
    suspend fun pull(
        tenantId: Uuid,
        principalBranchId: Uuid?,
        since: Long,
        limit: Int,
        entityTypes: Set<SyncEntityType>?
    ): PullResponse
}
