package com.f776.ecopan.data.tenant.local.mapper

import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import com.f776.unigrid.core.database.entity.UserEntity
import kotlin.time.Clock

internal fun UserEntity.toDomain() = UserAccount(
    id = id,
    tenantId = tenantId,
    branchId = branchId,
    username = username,
    displayName = displayName,
    role = role,
    active = active,
    hasPin = pinHash != null,
)

internal fun UserAccountUpsert.toEntity() = UserEntity(
    id = id,
    tenantId = tenantId,
    branchId = branchId,
    username = username,
    displayName = displayName,
    role = role,
    active = active,
    pinHash = pinHash,
    serverSeq = serverSeq,
    cachedAt = Clock.System.now(),
)
