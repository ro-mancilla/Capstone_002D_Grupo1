package com.f776.ecopan.data.tenant.local.mapper

import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.unigrid.core.database.entity.TenantEntity
import kotlin.time.Clock

internal fun TenantEntity.toDomain() = TenantProfile(
    id = id,
    slug = slug,
    name = name,
    currencyCode = currencyCode,
    enabledModules = enabledModules,
)

internal fun TenantProfile.toEntity() = TenantEntity(
    id = id,
    slug = slug,
    name = name,
    currencyCode = currencyCode,
    enabledModules = enabledModules,
    cachedAt = Clock.System.now(),
)
