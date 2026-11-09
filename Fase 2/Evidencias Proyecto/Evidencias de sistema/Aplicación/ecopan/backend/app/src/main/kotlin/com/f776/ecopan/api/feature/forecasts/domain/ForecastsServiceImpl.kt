package com.f776.ecopan.api.feature.forecasts.domain

import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.feature.forecasts.controller.dto.ForecastDto
import com.f776.ecopan.data.database.repository.ForecastRepository
import com.f776.ecopan.data.database.repository.model.Forecast
import com.f776.ecopan.data.messagebroker.ForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested
import kotlinx.datetime.toKotlinLocalDate
import java.time.ZoneId
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ForecastsServiceImpl(
    private val forecastRepository: ForecastRepository,
    private val forecastRefreshBroker: ForecastRefreshBroker,
    private val reportingTz: String
) : ForecastsService {

    override suspend fun list(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): ServiceOutcome<List<ForecastDto>> {
        val zone = ZoneId.of(reportingTz)
        val fromDate = java.time.Instant.ofEpochMilli(from.toEpochMilliseconds()).atZone(zone).toLocalDate().toKotlinLocalDate()
        val toDate = java.time.Instant.ofEpochMilli(to.toEpochMilliseconds()).atZone(zone).toLocalDate().toKotlinLocalDate()
        val forecasts = forecastRepository.listByBranch(tenantId, branchId, fromDate, toDate)
        return ServiceOutcome.Success(forecasts.map { it.toDto() })
    }

    override suspend fun requestRefresh(tenantId: Uuid, branchId: Uuid, productType: String?) {
        forecastRefreshBroker.publish(
            ForecastRefreshRequested(tenantId = tenantId.toString(), branchId = branchId.toString(), productType = productType)
        )
    }

    private fun Forecast.toDto() = ForecastDto(
        id = id.toString(),
        branchId = branchId.toString(),
        productId = productId.toString(),
        productName = productName,
        productType = productType,
        forecastDate = forecastDate.toString(),
        predictedGrams = predictedGrams,
        predictedUnits = predictedUnits,
        modelVersion = modelVersion,
        generatedAt = generatedAt.toEpochMilliseconds()
    )
}
