package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * A logged production batch (06 §1). Append-only — no update/delete DAO methods exist, mirroring
 * [SaleEntity]. [productTypeName] is a denormalized snapshot; [estimatedUnits] is informational
 * only (06 §2 — "displayed, never ledgered"), never backs a stock movement.
 */
@Entity(tableName = "production_batches", indices = [Index(value = ["branchId"]), Index(value = ["loggedAt"])])
data class ProductionBatchEntity(
    @PrimaryKey val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val bakerId: Uuid,
    val productId: Uuid,
    val productTypeName: String,
    val mixWeightGrams: Long,
    val estimatedUnits: Int?,
    val loggedAt: Instant,
)
