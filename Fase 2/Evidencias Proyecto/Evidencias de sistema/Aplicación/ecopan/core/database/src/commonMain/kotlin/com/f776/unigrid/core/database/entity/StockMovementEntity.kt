package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Append-only delta ledger row (01 §6.1) — current stock is Σ [deltaGrams], never a stored
 * mutable quantity; negative levels are allowed (01 §6.4), so there is no CHECK constraint here.
 * [kind] is stored as a plain string, not a typed enum — `MovementKind`'s real definition lives in
 * `:data:inventory:api`, and `:core:database` must never depend on a data-layer module (mirrors
 * [OutboxEntity.entityType]/[OutboxEntity.opType]); `:data:inventory:impl` maps it at the edge.
 */
@Entity(
    tableName = "stock_movements",
    indices = [
        Index(value = ["tenantId", "branchId"]),
        Index(value = ["ingredientId"]),
        Index(value = ["sourceBatchId"]),
    ],
)
data class StockMovementEntity(
    @PrimaryKey val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val ingredientId: Uuid,
    val deltaGrams: Long,
    val kind: String,
    val sourceBatchId: Uuid?,
    val note: String?,
    val recordedAt: Instant,
    val recordedBy: Uuid?,
)
