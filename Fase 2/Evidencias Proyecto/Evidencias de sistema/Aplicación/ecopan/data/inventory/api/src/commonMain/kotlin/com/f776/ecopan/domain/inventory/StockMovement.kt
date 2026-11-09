package com.f776.ecopan.domain.inventory

import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Append-only ledger row (01 §6.1) — current stock is Σ [deltaGrams], never a stored mutable qty. */
data class StockMovement(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val ingredientId: Uuid,
    val deltaGrams: Long,
    val kind: MovementKind,
    val sourceBatchId: Uuid?,
    val note: String?,
    val recordedAt: Instant,
    val recordedBy: Uuid?,
)
