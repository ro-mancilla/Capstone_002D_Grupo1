package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Client-side cache of a tenant's raw materials (02 §1 `ingredients`). Unit is always grams (01 §5). */
@Entity(tableName = "ingredients", indices = [Index(value = ["tenantId"])])
data class IngredientEntity(
    @PrimaryKey val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val lowStockThresholdGrams: Long?,
    val active: Boolean,
    val serverSeq: Long,
    val cachedAt: Instant,
)
