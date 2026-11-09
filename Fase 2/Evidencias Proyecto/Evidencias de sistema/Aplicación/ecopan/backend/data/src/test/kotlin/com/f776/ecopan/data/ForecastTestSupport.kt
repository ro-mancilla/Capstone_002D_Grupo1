package com.f776.ecopan.data

import com.f776.ecopan.data.database.repository.ForecastRepository
import com.f776.ecopan.data.database.repository.ForecastUpsert
import com.f776.ecopan.data.database.repository.model.Forecast
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** Test shorthand: store one row with no "past day" refusal, then read it back. */
suspend fun ForecastRepository.upsert(
    tenantId: Uuid,
    branchId: Uuid,
    productId: Uuid,
    forecastDate: LocalDate,
    predictedGrams: Long?,
    predictedUnits: Int?,
    modelVersion: String
): Forecast {
    upsertAll(
        tenantId, branchId,
        listOf(ForecastUpsert(productId, forecastDate, predictedGrams, predictedUnits, modelVersion)),
        notBefore = LocalDate(2000, 1, 1)
    )
    return listByBranch(tenantId, branchId, forecastDate, forecastDate, productId = productId).single()
}
