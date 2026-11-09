package com.f776.ecopan.data.database.repository.model

import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One raw sale-line fact row for analytics aggregation (02 §3 K1-K3/K7/K8). Day/week/month
 * bucketing is done in Kotlin against [AnalyticsRepository]'s `reportingTz` (02 §3's open Q2 --
 * deliberately *not* a SQL `date_trunc`, see [AnalyticsRepositoryImpl] doc) rather than in SQL, so
 * these rows are the finest grain the repository fetches -- the caller aggregates.
 */
data class SaleLineFact(
    val saleId: Uuid,
    val branchId: Uuid,
    val soldAt: Instant,
    val paymentMethod: PaymentMethod,
    val productId: Uuid,
    val productName: String,
    val productType: String,
    /** ALMACEN products are resold, never baked, so they have no forecast series (#51). */
    val origin: ProductOrigin,
    val soldBy: SoldBy,
    val weightGrams: Long?,
    val quantity: Int?,
    val lineTotalMinor: Long
)

/** One raw production-batch fact row for K6/K8. */
data class ProductionFact(
    val branchId: Uuid,
    val productId: Uuid,
    val productName: String,
    val batchAt: Instant,
    val productType: String,
    val mixWeightGrams: Long
)
