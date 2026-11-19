package com.f776.ecopan.data.messagebroker.model

import kotlinx.serialization.Serializable

/**
 * Published by `backend:app` after a `production_batches` row lands with a fresh (`Applied`) insert
 * (02 §4.1/§6). The worker's `FormulaDecrementProcessor` is the fallback decrement path -- it only
 * mints movements when [movementsMinted] is false; otherwise it just short-circuits to `APPLIED`.
 */
@Serializable
data class ProductionBatchMessage(
    val batchId: String,
    val tenantId: String,
    val branchId: String,
    val productId: String,
    val mixWeightGrams: Long,
    val movementsMinted: Boolean
)
