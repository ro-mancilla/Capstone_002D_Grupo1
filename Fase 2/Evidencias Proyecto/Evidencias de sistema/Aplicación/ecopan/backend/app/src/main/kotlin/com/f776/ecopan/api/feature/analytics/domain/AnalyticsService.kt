package com.f776.ecopan.api.feature.analytics.domain

import com.f776.ecopan.api.feature.analytics.controller.dto.ForecastVsActualDto
import com.f776.ecopan.api.feature.analytics.controller.dto.PaymentMethodDto
import com.f776.ecopan.api.feature.analytics.controller.dto.ProductMixDto
import com.f776.ecopan.api.feature.analytics.controller.dto.ProductionSummaryDto
import com.f776.ecopan.api.feature.analytics.controller.dto.RevenueByPeriodDto
import com.f776.ecopan.api.feature.analytics.controller.dto.SalesByDayDto
import com.f776.ecopan.api.feature.analytics.controller.dto.StockLevelWarningDto
import com.f776.ecopan.api.feature.analytics.controller.dto.WasteEstimateDto
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** K1-K8 (02 §3). All ranges are `[from, to]` inclusive, `Instant`s in epoch millis on the wire. */
interface AnalyticsService {
    suspend fun salesByDay(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant, groupByProductType: Boolean): List<SalesByDayDto>
    suspend fun revenue(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant, granularity: String): List<RevenueByPeriodDto>
    suspend fun productMix(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductMixDto>
    suspend fun stockLevels(tenantId: Uuid, branchId: Uuid): List<StockLevelWarningDto>
    suspend fun productionSummary(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductionSummaryDto>
    suspend fun wasteEstimate(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<WasteEstimateDto>
    suspend fun paymentMethods(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<PaymentMethodDto>
    suspend fun forecastVsActual(tenantId: Uuid, branchId: Uuid, productType: String?, productId: Uuid?, from: Instant, to: Instant): List<ForecastVsActualDto>
}
