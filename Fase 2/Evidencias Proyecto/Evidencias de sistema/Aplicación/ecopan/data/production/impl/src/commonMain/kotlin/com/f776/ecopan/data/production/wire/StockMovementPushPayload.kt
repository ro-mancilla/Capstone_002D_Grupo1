package com.f776.ecopan.data.production.wire

import com.f776.ecopan.domain.inventory.StockMovement
import kotlinx.serialization.Serializable

/** The `payload` for a `STOCK_MOVEMENT` mutation (03 §3.2, reconciled with 02's `stock_movements` table). */
@Serializable
internal data class StockMovementPushPayload(
    val id: String,
    val branchId: String,
    val ingredientId: String,
    val deltaGrams: Long,
    val kind: String,
    val sourceBatchId: String?,
    val note: String?,
    /** Epoch milliseconds, matching the backend's `StockMovementPayload.occurredAt: Long`. */
    val occurredAt: Long,
    val createdBy: String?,
)

internal fun StockMovement.toPushPayload() = StockMovementPushPayload(
    id = id.toString(),
    branchId = branchId.toString(),
    ingredientId = ingredientId.toString(),
    deltaGrams = deltaGrams,
    kind = kind.name,
    sourceBatchId = sourceBatchId?.toString(),
    note = note,
    occurredAt = recordedAt.toEpochMilliseconds(),
    createdBy = recordedBy?.toString(),
)
