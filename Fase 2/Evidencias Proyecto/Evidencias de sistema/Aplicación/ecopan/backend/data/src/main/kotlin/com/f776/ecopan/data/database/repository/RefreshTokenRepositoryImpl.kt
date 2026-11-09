package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.RefreshTokenTable
import com.f776.ecopan.data.database.repository.model.RefreshTokenRecord
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class RefreshTokenRepositoryImpl : RefreshTokenRepository {

    override suspend fun create(
        id: Uuid,
        userId: Uuid,
        tokenHash: String,
        deviceId: String?,
        deviceName: String?,
        branchId: Uuid?,
        issuedAt: Instant,
        expiresAt: Instant,
        familyId: Uuid,
    ): RefreshTokenRecord = suspendTransaction {
        RefreshTokenTable.insert {
            it[RefreshTokenTable.id] = id
            it[RefreshTokenTable.userId] = userId
            it[RefreshTokenTable.tokenHash] = tokenHash
            it[RefreshTokenTable.deviceId] = deviceId
            it[RefreshTokenTable.deviceName] = deviceName
            it[RefreshTokenTable.branchId] = branchId
            it[RefreshTokenTable.issuedAt] = issuedAt
            it[RefreshTokenTable.expiresAt] = expiresAt
            it[RefreshTokenTable.familyId] = familyId
        }
        findByTokenHashInternal(tokenHash)!!
    }

    override suspend fun rotate(
        oldId: Uuid,
        newId: Uuid,
        newTokenHash: String,
        issuedAt: Instant,
        expiresAt: Instant,
    ): RefreshTokenRecord? = suspendTransaction {
        // Under READ COMMITTED a second, concurrent UPDATE waits on the row lock and then re-checks
        // `revoked_at IS NULL` against the committed row, so it matches nothing.
        val won = RefreshTokenTable.update({ (RefreshTokenTable.id eq oldId) and RefreshTokenTable.revokedAt.isNull() }) {
            it[RefreshTokenTable.revokedAt] = issuedAt
        } == 1
        if (!won) return@suspendTransaction null
        val old = RefreshTokenTable.selectAll().where { RefreshTokenTable.id eq oldId }.single().toRecord()
        RefreshTokenTable.insert {
            it[RefreshTokenTable.id] = newId
            it[RefreshTokenTable.userId] = old.userId
            it[RefreshTokenTable.tokenHash] = newTokenHash
            it[RefreshTokenTable.deviceId] = old.deviceId
            it[RefreshTokenTable.deviceName] = old.deviceName
            it[RefreshTokenTable.branchId] = old.branchId
            it[RefreshTokenTable.issuedAt] = issuedAt
            it[RefreshTokenTable.expiresAt] = expiresAt
            it[RefreshTokenTable.familyId] = old.familyId
        }
        findByTokenHashInternal(newTokenHash)
    }

    override suspend fun revokeFamily(familyId: Uuid) {
        suspendTransaction {
            RefreshTokenTable.update({
                ((RefreshTokenTable.familyId eq familyId) or (RefreshTokenTable.id eq familyId)) and RefreshTokenTable.revokedAt.isNull()
            }) {
                it[RefreshTokenTable.revokedAt] = Clock.System.now()
            }
        }
    }

    override suspend fun findByTokenHash(tokenHash: String): RefreshTokenRecord? = suspendTransaction {
        findByTokenHashInternal(tokenHash)
    }

    override suspend fun revoke(id: Uuid) {
        suspendTransaction {
            RefreshTokenTable.update({ RefreshTokenTable.id eq id }) {
                it[RefreshTokenTable.revokedAt] = Clock.System.now()
            }
        }
    }

    override suspend fun revokeAllForUser(userId: Uuid) {
        suspendTransaction {
            RefreshTokenTable.update({ RefreshTokenTable.userId eq userId }) {
                it[RefreshTokenTable.revokedAt] = Clock.System.now()
            }
        }
    }

    private fun findByTokenHashInternal(tokenHash: String): RefreshTokenRecord? =
        RefreshTokenTable.selectAll().where { RefreshTokenTable.tokenHash eq tokenHash }.singleOrNull()?.toRecord()

    private fun ResultRow.toRecord() = RefreshTokenRecord(
        id = this[RefreshTokenTable.id],
        userId = this[RefreshTokenTable.userId],
        tokenHash = this[RefreshTokenTable.tokenHash],
        deviceId = this[RefreshTokenTable.deviceId],
        deviceName = this[RefreshTokenTable.deviceName],
        branchId = this[RefreshTokenTable.branchId],
        issuedAt = this[RefreshTokenTable.issuedAt],
        expiresAt = this[RefreshTokenTable.expiresAt],
        revokedAt = this[RefreshTokenTable.revokedAt],
        familyId = this[RefreshTokenTable.familyId] ?: this[RefreshTokenTable.id],
    )
}
