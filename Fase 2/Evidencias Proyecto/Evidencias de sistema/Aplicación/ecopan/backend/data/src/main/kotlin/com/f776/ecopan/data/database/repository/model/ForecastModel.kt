package com.f776.ecopan.data.database.repository.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Server-side ML cache row (02 §1) -- server-minted id, upserted on `(tenantId, branchId, productId, forecastDate)`. */
data class Forecast(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val productId: Uuid,
    val productName: String,
    /** The product's type, for grouping and filtering; never the series key. */
    val productType: String,
    val forecastDate: LocalDate,
    val predictedGrams: Long?,
    val predictedUnits: Int?,
    val modelVersion: String,
    val generatedAt: Instant,
    val serverSeq: Long = 0
)
