package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.f776.ecopan.core.domain.auth.Role
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Client-side cache of a tenant's users (02 §1 `users`). `pinHash` is synced for offline PIN
 * unlock (01 §4 device session + PIN unlock); `password_hash` is never serialized to the client
 * and has no column here.
 */
@Entity(tableName = "users", indices = [Index(value = ["tenantId"])])
data class UserEntity(
    @PrimaryKey val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid?,
    val username: String,
    val displayName: String,
    val role: Role,
    val active: Boolean,
    val pinHash: String?,
    val serverSeq: Long,
    val cachedAt: Instant,
)
