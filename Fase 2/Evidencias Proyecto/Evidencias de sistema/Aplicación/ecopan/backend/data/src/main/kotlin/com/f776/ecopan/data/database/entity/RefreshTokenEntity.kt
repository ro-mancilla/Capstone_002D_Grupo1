package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * Doubles as the device-session store (02 §1/§2): a device session is a long-lived refresh chain,
 * distinguished by a non-null [deviceId]. Server-minted id/rows -- not synced, not client-minted.
 */
object RefreshTokenTable : Table("refresh_tokens") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UserTable.id)
    val tokenHash = varchar("token_hash", 128).uniqueIndex()
    val deviceId = varchar("device_id", 64).nullable()
    val deviceName = text("device_name").nullable()
    val branchId = uuid("branch_id").nullable()
    val issuedAt = timestamp("issued_at")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()

    /**
     * The login this token descends from (#141): a fresh id at login, inherited on every rotation,
     * so presenting a token that was already rotated away can revoke the whole chain. Nullable only
     * so the column can be added to a table that already has rows; a null family is the row's own id.
     */
    val familyId = uuid("family_id").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_refresh_tokens_user", false, userId)
        index("idx_refresh_tokens_family", false, familyId)
    }
}
