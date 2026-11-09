package com.f776.ecopan.api.feature.forecasts.controller.dto

import kotlinx.serialization.Serializable

/** F1 (02 §3). */
@Serializable
data class ForecastDto(
    val id: String,
    val branchId: String,
    val productId: String,
    val productName: String,
    val productType: String,
    val forecastDate: String,
    val predictedGrams: Long?,
    val predictedUnits: Int?,
    val modelVersion: String,
    val generatedAt: Long
)

/** F2 request body. */
@Serializable
data class RefreshForecastsRequest(val branchId: String, val productType: String? = null)
