package com.f776.ecopan.api.feature.analytics.controller.dto

import kotlinx.serialization.Serializable

/** K1 (02 §3). */
@Serializable
data class SalesByDayDto(
    val date: String,
    val branchId: String?,
    val productType: String?,
    val salesCount: Long,
    val weightGrams: Long,
    val units: Long,
    val revenueMinor: Long
)

/** K2. */
@Serializable
data class RevenueByPeriodDto(val period: String, val revenueMinor: Long, val salesCount: Long)

/** K3. */
@Serializable
data class ProductMixDto(val productType: String, val revenueMinor: Long, val shareBp: Int)

/** K5 -- I3 shape + [negativeStock] reconciliation warning (01 §6.4). */
@Serializable
data class StockLevelWarningDto(
    val ingredientId: String,
    val name: String,
    val currentGrams: Long,
    val lowStock: Boolean,
    val negativeStock: Boolean
)

/**
 * K6 (HU-ANA-0003): one row per reporting-tz day x branch x product. [mixWeightGrams] is *dough*
 * weight (the batch's mix), not finished bread -- clients must label it as such.
 */
@Serializable
data class ProductionSummaryDto(
    val date: String,
    val branchId: String,
    val productId: String,
    val productName: String,
    val productType: String,
    val batchCount: Long,
    val mixWeightGrams: Long
)

/**
 * K9 -- the day's estimated waste (HU-ANA-0005): bread produced and not sold.
 *
 * [producedGrams] is **dough** weight (what the baker logs), and [soldGrams] is finished bread at
 * the till, so the difference overstates the loss by the water that bakes off; [estimated] is
 * always `true` to make that unmissable to a client. [estimatedWasteGrams] is `null` when the
 * product type is sold by unit that day, because dough grams cannot be compared with a unit count.
 */
@Serializable
data class WasteEstimateDto(
    val date: String,
    val productType: String,
    val producedGrams: Long,
    val soldGrams: Long,
    val soldUnits: Long,
    val estimatedWasteGrams: Long?,
    val estimated: Boolean
)

/** K7. */
@Serializable
data class PaymentMethodDto(val paymentMethod: String, val salesCount: Long, val revenueMinor: Long)

/** K8. */
@Serializable
data class ForecastVsActualDto(
    val date: String,
    /** The series: one (branch, Panadería product) per row set (#51). */
    val productId: String,
    val productName: String,
    /** The product's type, for grouping and filtering only. */
    val productType: String,
    val predictedGrams: Long?,
    val predictedUnits: Int?,
    val actualGrams: Long,
    val actualUnits: Long,
    /** Estimator behind the prediction (`lgbm-*` model vs `baseline-*` cold start); null when the row has no forecast. */
    val modelVersion: String? = null
)
