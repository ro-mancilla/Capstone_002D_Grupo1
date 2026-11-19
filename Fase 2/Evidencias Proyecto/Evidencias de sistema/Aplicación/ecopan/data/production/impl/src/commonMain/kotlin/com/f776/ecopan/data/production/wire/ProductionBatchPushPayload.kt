package com.f776.ecopan.data.production.wire

import com.f776.ecopan.domain.production.ProductionBatch
import kotlinx.serialization.Serializable

/**
 * The `payload` for a `PRODUCTION_BATCH` mutation (03 §3.2, reconciled with 02's
 * `production_batches` table). Excludes `tenantId` (JWT principal) and `deviceId` (push-envelope
 * top-level field) for the same reason `:data:sales`'s `SalePushPayload` does. [movementsMinted]
 * is always `true` here — 06 §4's resolved open question makes client-side minting the primary
 * path; the worker's decrement fallback only runs when a thinner future client sends `false`.
 */
@Serializable
internal data class ProductionBatchPushPayload(
    val id: String,
    val branchId: String,
    val bakerId: String,
    val productId: String,
    val mixWeightGrams: Long,
    /** Epoch milliseconds, matching the backend's `ProductionBatchPayload.batchAt: Long`. */
    val batchAt: Long,
    val movementsMinted: Boolean,
)

internal fun ProductionBatch.toPushPayload() = ProductionBatchPushPayload(
    id = id.toString(),
    branchId = branchId.toString(),
    bakerId = bakerId.toString(),
    productId = productId.toString(),
    mixWeightGrams = mixWeightGrams,
    batchAt = loggedAt.toEpochMilliseconds(),
    movementsMinted = true,
)
