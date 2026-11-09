package com.f776.ecopan.domain.production

import kotlin.time.Instant
import kotlin.uuid.Uuid

/** [productTypeName] is a denormalized snapshot (survives later catalog edits, 01 §5 convention). */
data class ProductionBatch(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val bakerId: Uuid,
    val productId: Uuid,
    val productTypeName: String,
    val mixWeightGrams: Long,
    val estimatedUnits: Int?,
    val loggedAt: Instant,
    val syncState: BatchSyncState,
)
