package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.repository.model.RefreshTokenRecord
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface RefreshTokenRepository {
    suspend fun create(
        id: Uuid,
        userId: Uuid,
        tokenHash: String,
        deviceId: String?,
        deviceName: String?,
        branchId: Uuid?,
        issuedAt: Instant,
        expiresAt: Instant,
        familyId: Uuid = id,
    ): RefreshTokenRecord

    suspend fun findByTokenHash(tokenHash: String): RefreshTokenRecord?
    suspend fun revoke(id: Uuid)
    suspend fun revokeAllForUser(userId: Uuid)

    /**
     * Rotation in one transaction (#141): revokes [oldId] *only if it is not revoked yet* and, if
     * that update matched, inserts its successor in the same family, device and branch. `null`
     * means someone else rotated or revoked it first and nothing was written -- the row count of
     * the conditional update decides, so two concurrent refreshes cannot both succeed.
     */
    suspend fun rotate(oldId: Uuid, newId: Uuid, newTokenHash: String, issuedAt: Instant, expiresAt: Instant): RefreshTokenRecord?

    /** Revokes every still-live token descended from the same login. */
    suspend fun revokeFamily(familyId: Uuid)
}
