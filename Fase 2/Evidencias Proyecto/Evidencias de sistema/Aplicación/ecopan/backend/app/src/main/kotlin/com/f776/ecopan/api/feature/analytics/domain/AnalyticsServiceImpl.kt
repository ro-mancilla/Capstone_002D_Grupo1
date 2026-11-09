package com.f776.ecopan.api.feature.analytics.domain

import com.f776.ecopan.data.domain.ProductTypeKey
import com.f776.ecopan.data.domain.ProductTypeSeries
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.api.feature.analytics.controller.dto.ForecastVsActualDto
import com.f776.ecopan.api.feature.analytics.controller.dto.PaymentMethodDto
import com.f776.ecopan.api.feature.analytics.controller.dto.ProductMixDto
import com.f776.ecopan.api.feature.analytics.controller.dto.ProductionSummaryDto
import com.f776.ecopan.api.feature.analytics.controller.dto.RevenueByPeriodDto
import com.f776.ecopan.api.feature.analytics.controller.dto.SalesByDayDto
import com.f776.ecopan.api.feature.analytics.controller.dto.StockLevelWarningDto
import com.f776.ecopan.api.feature.analytics.controller.dto.WasteEstimateDto
import com.f776.ecopan.data.database.repository.AnalyticsRepository
import com.f776.ecopan.data.database.repository.ForecastRepository
import com.f776.ecopan.data.database.repository.IngredientRepository
import com.f776.ecopan.data.database.repository.StockMovementRepository
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * K1-K8 (02 §3). Filtering (tenant/branch/time-range) happens in SQL via
 * [AnalyticsRepository]; "day"/"week"/"month" bucketing against `reportingTz` happens here in
 * Kotlin over the filtered rows (see [AnalyticsRepository]'s doc for why).
 */
internal class AnalyticsServiceImpl(
    private val analyticsRepository: AnalyticsRepository,
    private val ingredientRepository: IngredientRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val forecastRepository: ForecastRepository,
    private val reportingTz: String
) : AnalyticsService {

    private val zone: ZoneId by lazy { ZoneId.of(reportingTz) }
    private val dayFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    private fun Instant.toZonedDate() = java.time.Instant.ofEpochMilli(toEpochMilliseconds()).atZone(zone).toLocalDate()

    override suspend fun salesByDay(
        tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant, groupByProductType: Boolean
    ): List<SalesByDayDto> {
        val facts = analyticsRepository.saleLineFacts(tenantId, branchId, from, to)
        val series = ProductTypeSeries(facts.map { it.productType })
        val grouped = facts.groupBy { fact ->
            Triple(fact.soldAt.toZonedDate(), fact.branchId, if (groupByProductType) series.key(fact.productType) else null)
        }
        return grouped.map { (key, rows) ->
            val (date, groupBranchId, productKey) = key
            SalesByDayDto(
                date = date.format(dayFormatter),
                branchId = groupBranchId.toString(),
                productType = productKey?.let { series.label(rows.first().productType) },
                salesCount = rows.map { it.saleId }.distinct().size.toLong(),
                weightGrams = rows.sumOf { it.weightGrams ?: 0L },
                units = rows.sumOf { (it.quantity ?: 0).toLong() },
                revenueMinor = rows.sumOf { it.lineTotalMinor }
            )
        }.sortedBy { it.date }
    }

    override suspend fun revenue(
        tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant, granularity: String
    ): List<RevenueByPeriodDto> {
        val facts = analyticsRepository.saleLineFacts(tenantId, branchId, from, to)
        val bySale = facts.groupBy { periodLabel(it.soldAt.toZonedDate(), granularity) }
        return bySale.map { (period, rows) ->
            RevenueByPeriodDto(period, rows.sumOf { it.lineTotalMinor }, rows.map { it.saleId }.distinct().size.toLong())
        }.sortedBy { it.period }
    }

    private fun periodLabel(date: java.time.LocalDate, granularity: String): String = when (granularity) {
        "week" -> date.with(DayOfWeek.MONDAY).format(dayFormatter)
        "month" -> date.format(monthFormatter)
        else -> date.format(dayFormatter)
    }

    override suspend fun productMix(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductMixDto> {
        val facts = analyticsRepository.saleLineFacts(tenantId, branchId, from, to)
        val total = facts.sumOf { it.lineTotalMinor }
        val series = ProductTypeSeries(facts.map { it.productType })
        return facts.groupBy { series.key(it.productType) }.map { (_, rows) ->
            val revenue = rows.sumOf { it.lineTotalMinor }
            val shareBp = if (total > 0) ((revenue * 10_000) / total).toInt() else 0
            ProductMixDto(series.label(rows.first().productType), revenue, shareBp)
        }.sortedByDescending { it.revenueMinor }
    }

    override suspend fun stockLevels(tenantId: Uuid, branchId: Uuid): List<StockLevelWarningDto> {
        val sums = stockMovementRepository.sumByIngredient(tenantId, branchId).associateBy { it.ingredientId }
        val ingredients = ingredientRepository.listByTenant(tenantId, includeInactive = true)
        return ingredients.map { ingredient ->
            val currentGrams = sums[ingredient.id]?.currentGrams ?: 0L
            StockLevelWarningDto(
                ingredientId = ingredient.id.toString(),
                name = ingredient.name,
                currentGrams = currentGrams,
                // Same rule as the inventory screen: at or under the reorder level, 0 included.
                lowStock = ingredient.lowStockThresholdGrams?.let { currentGrams <= it } ?: false,
                negativeStock = currentGrams < 0
            )
        }
    }

    override suspend fun productionSummary(
        tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant
    ): List<ProductionSummaryDto> {
        val facts = analyticsRepository.productionFacts(tenantId, branchId, from, to)
        val series = ProductTypeSeries(facts.map { it.productType })
        return facts.groupBy { Triple(it.batchAt.toZonedDate(), it.branchId, it.productId) }.map { (key, rows) ->
            val (date, rowBranchId, productId) = key
            val first = rows.first()
            ProductionSummaryDto(
                date = date.format(dayFormatter),
                branchId = rowBranchId.toString(),
                productId = productId.toString(),
                productName = first.productName,
                productType = series.label(first.productType),
                batchCount = rows.size.toLong(),
                mixWeightGrams = rows.sumOf { it.mixWeightGrams }
            )
        }.sortedWith(compareBy({ it.date }, { it.productName }, { it.branchId }))
    }

    override suspend fun wasteEstimate(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<WasteEstimateDto> =
        estimateWaste(
            production = analyticsRepository.productionFacts(tenantId, branchId, from, to),
            sales = analyticsRepository.saleLineFacts(tenantId, branchId, from, to),
            zone = zone
        )

    override suspend fun paymentMethods(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<PaymentMethodDto> {
        // Payment method lives on the parent sale, not the line -- re-derive per-sale totals by
        // treating each fact row's contribution and grouping by paymentMethod (lines of the same
        // sale share one paymentMethod, so summing lineTotalMinor per method is still correct).
        val facts = analyticsRepository.saleLineFacts(tenantId, branchId, from, to)
        return facts.groupBy { it.paymentMethod }.map { (method, rows) ->
            PaymentMethodDto(method.name, rows.map { it.saleId }.distinct().size.toLong(), rows.sumOf { it.lineTotalMinor })
        }
    }

    override suspend fun forecastVsActual(
        tenantId: Uuid, branchId: Uuid, productType: String?, productId: Uuid?, from: Instant, to: Instant
    ): List<ForecastVsActualDto> {
        val fromDate = from.toZonedDate().toKotlinLocalDate()
        val toDate = to.toZonedDate().toKotlinLocalDate()
        val forecasts = forecastRepository.listByBranch(tenantId, branchId, fromDate, toDate, productType, productId)
        // The series is the product (#51); Almacén is resold, never forecast, so it has no row here.
        val facts = analyticsRepository.saleLineFacts(tenantId, branchId, from, to)
            .filter { it.origin == ProductOrigin.PANADERIA }
            .filter { productId == null || it.productId == productId }
            .filter { productType == null || ProductTypeKey.of(it.productType) == ProductTypeKey.of(productType) }
        val types = ProductTypeSeries(forecasts.map { it.productType } + facts.map { it.productType })

        val actualsByKey = facts.groupBy { it.soldAt.toZonedDate() to it.productId }
        val forecastsByKey = forecasts.groupBy { it.forecastDate.toJavaLocalDate() to it.productId }
        val names = (forecasts.associate { it.productId to (it.productName to it.productType) } +
            facts.associate { it.productId to (it.productName to it.productType) })

        val allKeys = actualsByKey.keys + forecastsByKey.keys
        return allKeys.map { key ->
            val (date, id) = key
            val (name, type) = names.getValue(id)
            val actualRows = actualsByKey[key].orEmpty()
            val forecastRow = forecastsByKey[key]?.firstOrNull()
            ForecastVsActualDto(
                date = date.format(dayFormatter),
                productId = id.toString(),
                productName = name,
                productType = types.label(type),
                predictedGrams = forecastRow?.predictedGrams,
                predictedUnits = forecastRow?.predictedUnits,
                actualGrams = actualRows.sumOf { it.weightGrams ?: 0L },
                actualUnits = actualRows.sumOf { (it.quantity ?: 0).toLong() },
                modelVersion = forecastRow?.modelVersion
            )
        }.sortedWith(compareBy({ it.date }, { it.productName }))
    }
}
