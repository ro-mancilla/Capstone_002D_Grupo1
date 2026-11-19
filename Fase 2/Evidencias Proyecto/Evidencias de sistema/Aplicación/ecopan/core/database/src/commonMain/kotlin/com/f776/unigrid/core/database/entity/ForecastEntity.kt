package com.f776.unigrid.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * One forecast day for one Panadería product in one branch, as the server computed it (#51/#52).
 * A server-authoritative config row (03 §4.3 family 1): pulled and overwritten by id, never
 * written locally. It exists so the baker's *producción sugerida* is readable offline.
 *
 * [forecastDate] is the reporting-zone day the prediction is for; exactly one of
 * [predictedGrams]/[predictedUnits] is set (the product's sales unit). [modelVersion] is the
 * server's label (`lgbm-*` / `baseline-*`) and is the only thing the estimator label is derived
 * from. [generatedAt] is when the server produced the row, which is what staleness is measured on.
 */
@Entity(
    tableName = "forecasts",
    indices = [Index(value = ["branchId", "forecastDate"])],
)
data class ForecastEntity(
    @PrimaryKey val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid,
    val productId: Uuid,
    val productName: String,
    val productType: String,
    val forecastDate: LocalDate,
    val predictedGrams: Long?,
    val predictedUnits: Int?,
    val modelVersion: String,
    val generatedAt: Instant,
    val serverSeq: Long,
)
