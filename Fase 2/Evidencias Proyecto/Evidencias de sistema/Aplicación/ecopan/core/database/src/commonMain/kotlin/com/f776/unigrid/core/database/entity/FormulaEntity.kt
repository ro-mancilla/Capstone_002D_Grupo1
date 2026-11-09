package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** One active formula per product (02 §1 `formulas`); lines live in [FormulaLineEntity]. */
@Entity(
    tableName = "formulas",
    indices = [Index(value = ["tenantId", "productId"], unique = true)],
)
data class FormulaEntity(
    @PrimaryKey val id: Uuid,
    val tenantId: Uuid,
    val productId: Uuid,
    val active: Boolean,
    val serverSeq: Long,
    val cachedAt: Instant,
)
