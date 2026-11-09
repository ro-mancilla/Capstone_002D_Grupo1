package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Client-side cache of a tenant's branches (02 §1 `branches`), pulled config data. */
@Entity(tableName = "branches", indices = [Index(value = ["tenantId"])])
data class BranchEntity(
    @PrimaryKey val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val active: Boolean,
    val serverSeq: Long,
    val cachedAt: Instant,
)
