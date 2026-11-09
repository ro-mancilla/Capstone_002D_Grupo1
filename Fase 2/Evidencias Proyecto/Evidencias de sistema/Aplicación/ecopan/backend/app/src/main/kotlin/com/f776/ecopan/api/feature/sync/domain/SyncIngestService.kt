package com.f776.ecopan.api.feature.sync.domain

import com.f776.ecopan.api.feature.sync.controller.dto.MutationDto
import com.f776.ecopan.api.feature.sync.controller.dto.PushResponse
import kotlin.uuid.Uuid

/** S1 push-ingest (02 §4.1) -- kept separate from [com.f776.ecopan.api.feature.sync.domain.SyncPullService] per the plan's file-plan note. */
interface SyncIngestService {
    suspend fun push(
        tenantId: Uuid,
        principalUserId: Uuid,
        principalBranchId: Uuid?,
        deviceId: String,
        mutations: List<MutationDto>
    ): PushResponse
}
