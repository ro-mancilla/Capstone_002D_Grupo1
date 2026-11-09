package com.f776.ecopan.api.feature.inventory.controller.dto

import kotlinx.serialization.Serializable

@Serializable
data class IngredientDto(val id: String, val name: String, val lowStockThresholdGrams: Long?, val active: Boolean)

@Serializable
data class CreateIngredientRequest(
    val id: String,
    val name: String,
    val lowStockThresholdGrams: Long? = null
)

@Serializable
data class UpdateIngredientRequest(
    val name: String,
    val lowStockThresholdGrams: Long? = null,
    val active: Boolean = true
)

@Serializable
data class StockLevelDto(val ingredientId: String, val name: String, val currentGrams: Long, val lowStock: Boolean)

/**
 * One ingredient's ledger over a closed day range (HU-ANA-0006). All grams are signed Σ of the
 * ledger's deltas, so [decrementedGrams] is <= 0 and
 * `openingGrams + purchasedGrams + decrementedGrams + adjustedGrams == closingGrams` always holds.
 * [batchCount] is the number of production batches behind [decrementedGrams].
 */
@Serializable
data class ReconciliationRowDto(
    val ingredientId: String,
    val name: String,
    val openingGrams: Long,
    val purchasedGrams: Long,
    val decrementedGrams: Long,
    val adjustedGrams: Long,
    val closingGrams: Long,
    val batchCount: Int
)

/**
 * `GET /inventory/reconciliation`: the per-ingredient [rows] plus the period-level [batchCount] --
 * distinct production batches in the window. It is NOT the sum of the rows' `batchCount`: one batch
 * decrements every ingredient in its formula, so that sum counts each batch once per ingredient.
 */
@Serializable
data class ReconciliationReportDto(
    val batchCount: Int,
    val rows: List<ReconciliationRowDto>
)

@Serializable
data class StockMovementDto(
    val id: String,
    val branchId: String,
    val ingredientId: String,
    val deltaGrams: Long,
    val kind: String,
    val sourceBatchId: String?,
    val note: String?,
    val createdBy: String?,
    val occurredAt: Long,
    val serverSeq: Long
)

@Serializable
data class CreateStockMovementRequest(
    val id: String,
    val branchId: String,
    val ingredientId: String,
    val deltaGrams: Long,
    val kind: String,
    val note: String? = null,
    val occurredAt: Long
)

@Serializable
data class CreateStockMovementResponse(val serverSeq: Long)

/**
 * Product stock of one Almacén product in one branch, in whole units. `currentUnits = receivedUnits
 * − soldUnits`: `receivedUnits` is Σ of every movement (the opening count, purchases and manual
 * adjustments) and `soldUnits` is read from `sale_lines` from `trackedSince` (the branch's opening
 * count, or the product's creation) onward. Negative stock is legal and only reported
 * (`currentUnits < 0`); it never blocks a sale.
 */
@Serializable
data class ProductStockLevelDto(
    val productId: String,
    val name: String,
    val receivedUnits: Long,
    val soldUnits: Long,
    val currentUnits: Long,
    val trackedSince: Long,
    /** Reorder level, or null when none is set. */
    val lowStockThresholdUnits: Long?,
    /** True when [currentUnits] has dropped below [lowStockThresholdUnits]; false without one. */
    val lowStock: Boolean,
    /** False for a retired (soft-deleted) product; its row stays so its history is still readable. */
    val active: Boolean
)

/** `thresholdUnits: null` clears the reorder level. */
@Serializable
data class SetProductLowStockThresholdRequest(val thresholdUnits: Long? = null)

@Serializable
data class ProductLowStockThresholdDto(val productId: String, val lowStockThresholdUnits: Long?)

@Serializable
data class ProductStockMovementDto(
    val id: String,
    val branchId: String,
    val productId: String,
    val deltaUnits: Long,
    val kind: String,
    val note: String?,
    val createdBy: String?,
    val occurredAt: Long
)

@Serializable
data class CreateProductStockMovementRequest(
    val id: String,
    val branchId: String,
    val productId: String,
    val deltaUnits: Long,
    val kind: String,
    val note: String? = null,
    val occurredAt: Long
)
