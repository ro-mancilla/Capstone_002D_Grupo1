package com.f776.ecopan.worker.ml

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types for the ML service's `POST /forecast` (08 §5). Field names mirror
 * `ml/src/ecopan_ml/service/schemas.py` exactly -- that file is the contract's single owner, this
 * one just transliterates it. Dates travel as ISO-8601 strings because Pydantic's `date` fields
 * parse/emit exactly that.
 *
 * `is_holiday`/`is_day_before_holiday` are deliberately absent: the backend has no holiday
 * calendar to source them from, and the schema defaults both to false. That zero-fill is the
 * documented degradation in `schemas.py` (it biases exactly the days demand moves most), so
 * plumbing a real calendar through here is the known next improvement, not an oversight.
 *
 * Since #51 the series is a product: the ML service treats `product_type` as an opaque label, so
 * the worker sends the product id in it and reads the same id back.
 */
@Serializable
data class MlHistoryPoint(
    val day: String,
    val demand: Double,
)

@Serializable
data class MlSeriesHistory(
    @SerialName("branch_id") val branchId: String,
    @SerialName("product_type") val productType: String,
    val history: List<MlHistoryPoint>,
)

@Serializable
data class MlForecastRequest(
    val series: List<MlSeriesHistory>,
    @SerialName("horizon_days") val horizonDays: Int,
    @SerialName("start_day") val startDay: String,
)

@Serializable
data class MlForecastPoint(
    val day: String,
    val predicted: Double,
)

@Serializable
data class MlSeriesForecast(
    @SerialName("branch_id") val branchId: String,
    @SerialName("product_type") val productType: String,
    @SerialName("model_version") val modelVersion: String,
    @SerialName("observed_days") val observedDays: Int,
    val predictions: List<MlForecastPoint>,
)

@Serializable
data class MlForecastResponse(
    val forecasts: List<MlSeriesForecast>,
    @SerialName("generated_at") val generatedAt: String,
    @SerialName("service_model_version") val serviceModelVersion: String,
)
