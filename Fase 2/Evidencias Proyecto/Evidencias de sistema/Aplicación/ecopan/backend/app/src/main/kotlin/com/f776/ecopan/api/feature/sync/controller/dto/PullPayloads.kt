package com.f776.ecopan.api.feature.sync.controller.dto

import kotlinx.serialization.Serializable

/**
 * Config/fact payload shapes for `GET /sync/pull` (02 §4.2). `SALE` reuses [SalePayload]/
 * [SaleLinePayload] and `STOCK_MOVEMENT` reuses [StockMovementPayload] from the push side --
 * the field sets are identical on both directions of the wire.
 */
@Serializable
data class BranchPullPayload(val id: String, val name: String, val active: Boolean)

/** `pinHash` rides the pull (offline PIN unlock, 01 §4); `passwordHash` never does (02 §4.2). */
@Serializable
data class UserPullPayload(
    val id: String,
    val branchId: String?,
    val username: String,
    val displayName: String,
    val role: String,
    val active: Boolean,
    val pinHash: String?
)

@Serializable
data class ProductPullPayload(
    val id: String,
    val name: String,
    val productType: String,
    val soldBy: String,
    val pricePerKgMinor: Long?,
    val priceMinor: Long?,
    val barcode: String?,
    val imageUrl: String?,
    val active: Boolean,
    val origin: String = "PANADERIA",
    /** Almacén only: the reorder level. Optional on the wire, so older clients ignore it. */
    val lowStockThresholdUnits: Long? = null,
    /**
     * Almacén only: current stock *in the pulling principal's branch* (opening count + movements -
     * sold), negative allowed. Absent for Panadería products and for all-branch principals.
     */
    val stockUnits: Long? = null
)

/** One forecast day for one product in the pulling principal's branch (the baker's *producción sugerida*). */
@Serializable
data class ForecastPullPayload(
    val id: String,
    val branchId: String,
    val productId: String,
    val productName: String,
    val productType: String,
    /** `yyyy-MM-dd`, reporting-zone day. */
    val forecastDate: String,
    val predictedGrams: Long?,
    val predictedUnits: Int?,
    /** `lgbm-*` model vs `baseline-*`; the client labels the estimator from this alone. */
    val modelVersion: String,
    val generatedAt: Long
)

@Serializable
data class IngredientPullPayload(
    val id: String,
    val name: String,
    val lowStockThresholdGrams: Long?,
    val active: Boolean
)

@Serializable
data class FormulaLinePullPayload(val id: String, val ingredientId: String, val basisPoints: Int)

@Serializable
data class FormulaPullPayload(
    val id: String,
    val productId: String,
    val active: Boolean,
    val lines: List<FormulaLinePullPayload>
)

@Serializable
data class ProductionBatchPullPayload(
    val id: String,
    val branchId: String,
    val bakerId: String,
    val productId: String,
    val mixWeightGrams: Long,
    val batchAt: Long,
    val movementsMinted: Boolean,
    val decrementStatus: String
)
