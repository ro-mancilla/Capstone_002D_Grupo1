package com.f776.ecopan.api.feature.forecasts.domain

import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.feature.forecasts.controller.dto.ForecastDto
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** F1/F2 (02 §3). */
interface ForecastsService {
    suspend fun list(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): ServiceOutcome<List<ForecastDto>>

    /** Publishes `ForecastRefreshRequested`; the worker handler is a stub until `08` (02 §9 item 7). */
    suspend fun requestRefresh(tenantId: Uuid, branchId: Uuid, productType: String?)
}
