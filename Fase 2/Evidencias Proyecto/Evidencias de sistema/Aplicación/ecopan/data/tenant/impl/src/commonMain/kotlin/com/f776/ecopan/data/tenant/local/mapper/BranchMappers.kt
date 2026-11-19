package com.f776.ecopan.data.tenant.local.mapper

import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.unigrid.core.database.entity.BranchEntity
import kotlin.time.Clock

internal fun BranchEntity.toDomain() = Branch(
    id = id,
    tenantId = tenantId,
    name = name,
    active = active,
)

internal fun BranchUpsert.toEntity() = BranchEntity(
    id = id,
    tenantId = tenantId,
    name = name,
    active = active,
    serverSeq = serverSeq,
    cachedAt = Clock.System.now(),
)
