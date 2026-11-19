package com.f776.ecopan.data.production.local.mapper

import com.f776.ecopan.domain.production.Estimator
import com.f776.ecopan.domain.production.SuggestedProduction
import com.f776.ecopan.domain.production.SuggestedQuantity
import com.f776.unigrid.core.database.entity.ForecastEntity

/**
 * Null when the row carries no quantity at all. The server writes exactly one of the two, so such
 * a row is corrupt, and a suggestion with no number is worse than none. Grams win if both are set
 * (the server's own tie-break), so a row is never shown in two units.
 */
internal fun ForecastEntity.toDomain(): SuggestedProduction? {
    val grams = predictedGrams
    val units = predictedUnits
    val quantity = when {
        grams != null -> SuggestedQuantity.Grams(grams)
        units != null -> SuggestedQuantity.Units(units)
        else -> return null
    }
    return SuggestedProduction(
        id = id,
        branchId = branchId,
        productId = productId,
        productName = productName,
        productType = productType,
        forecastDate = forecastDate,
        quantity = quantity,
        estimator = Estimator.fromModelVersion(modelVersion),
        generatedAt = generatedAt,
    )
}
