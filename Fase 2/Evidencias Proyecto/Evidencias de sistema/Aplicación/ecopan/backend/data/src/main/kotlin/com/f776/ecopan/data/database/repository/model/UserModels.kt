package com.f776.ecopan.data.database.repository.model

import com.f776.ecopan.data.domain.Role
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class User(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid?,
    val username: String,
    val passwordHash: String,
    val pinHash: String?,
    val displayName: String,
    val role: Role,
    val active: Boolean,
    val serverSeq: Long,
    val createdAt: Instant
)

data class RefreshTokenRecord(
    val id: Uuid,
    val userId: Uuid,
    val tokenHash: String,
    val deviceId: String?,
    val deviceName: String?,
    val branchId: Uuid?,
    val issuedAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    /** See `RefreshTokenTable.familyId`. */
    val familyId: Uuid,
)
