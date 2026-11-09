package com.f776.ecopan.data.messagebroker.model

import kotlinx.serialization.Serializable

/**
 * Published by F2 `POST /forecasts/refresh` (02 §3/§9 item 7) and by the worker's
 * `DailyForecastScheduler` (once per branch per reporting-timezone day). Consumed by
 * `ForecastRefreshProcessorImpl`, which calls the ML service and upserts `forecasts`.
 */
@Serializable
data class ForecastRefreshRequested(
    val tenantId: String,
    val branchId: String,
    val productType: String? = null
)
