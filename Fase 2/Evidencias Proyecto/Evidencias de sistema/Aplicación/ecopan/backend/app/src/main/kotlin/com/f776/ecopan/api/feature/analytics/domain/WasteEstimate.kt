package com.f776.ecopan.api.feature.analytics.domain

import com.f776.ecopan.data.domain.ProductTypeSeries
import com.f776.ecopan.api.feature.analytics.controller.dto.WasteEstimateDto
import com.f776.ecopan.data.database.repository.model.ProductionFact
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Instant

/**
 * K9 (HU-ANA-0005): bread produced and not sold, per day and product type.
 *
 * The comparison is deliberately crude and says so. Production is logged as **dough** weight and
 * the till sells **finished** bread, so `produced - sold` counts the water that evaporates in the
 * oven as waste; the number is an upper bound, not a measurement. A product type sold by unit that
 * day cannot be compared against dough grams at all, so its waste is `null` instead of a figure
 * that would be wrong by construction. A day with sales and no batch is reported with zero
 * produced (stock from the day before), never as negative waste.
 */
internal fun estimateWaste(production: List<ProductionFact>, sales: List<SaleLineFact>, zone: ZoneId): List<WasteEstimateDto> {
    fun Instant.day(): LocalDate = java.time.Instant.ofEpochMilli(toEpochMilliseconds()).atZone(zone).toLocalDate()

    val series = ProductTypeSeries(production.map { it.productType } + sales.map { it.productType })
    val produced = production.groupBy { it.batchAt.day() to series.key(it.productType) }
    val sold = sales.groupBy { it.soldAt.day() to series.key(it.productType) }

    return (produced.keys + sold.keys).map { key ->
        val (date, productKey) = key
        val productType = series.label((produced[key].orEmpty().map { it.productType } + sold[key].orEmpty().map { it.productType }).first())
        val producedGrams = produced[key].orEmpty().sumOf { it.mixWeightGrams }
        val soldGrams = sold[key].orEmpty().sumOf { it.weightGrams ?: 0L }
        val soldUnits = sold[key].orEmpty().sumOf { (it.quantity ?: 0).toLong() }
        val comparable = soldGrams > 0 || soldUnits == 0L
        WasteEstimateDto(
            date = date.toString(),
            productType = productType,
            producedGrams = producedGrams,
            soldGrams = soldGrams,
            soldUnits = soldUnits,
            estimatedWasteGrams = if (comparable) (producedGrams - soldGrams).coerceAtLeast(0L) else null,
            estimated = true
        )
    }.sortedWith(compareBy({ it.date }, { it.productType }))
}
