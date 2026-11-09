package com.f776.ecopan.api

import com.f776.ecopan.api.fakes.FakeForecastRepository
import com.f776.ecopan.api.fakes.FakeIngredientRepository
import com.f776.ecopan.api.fakes.FakeStockMovementRepository
import com.f776.ecopan.api.feature.analytics.domain.AnalyticsServiceImpl
import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.AnalyticsRepository
import com.f776.ecopan.data.database.repository.ForecastRepository
import com.f776.ecopan.data.database.repository.model.Forecast
import com.f776.ecopan.data.database.repository.model.ProductionFact
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Ticket #27: analytics series are decided by the normalised type key, not by string equality. */
class AnalyticsTypeSeriesTest {

    private val tenant = Uuid.random()
    private val branch = Uuid.random()
    private val product = Uuid.random()
    private val at = Instant.parse("2026-03-10T15:00:00Z")
    private val from = Instant.parse("2026-03-01T00:00:00Z")
    private val to = Instant.parse("2026-03-31T00:00:00Z")

    private val marraqueta = Uuid.random()
    private val hallulla = Uuid.random()
    private val cola = Uuid.random()

    private fun line(
        type: String, revenue: Long, units: Int = 1, productId: Uuid = marraqueta, name: String = "Marraqueta",
        origin: ProductOrigin = ProductOrigin.PANADERIA,
    ) = SaleLineFact(
        saleId = Uuid.random(), branchId = branch, soldAt = at, paymentMethod = PaymentMethod.CASH,
        productId = productId, productName = name, productType = type, origin = origin,
        soldBy = SoldBy.UNIT, weightGrams = null, quantity = units, lineTotalMinor = revenue
    )

    private fun service(sales: List<SaleLineFact>, forecasts: List<Forecast> = emptyList(), batches: List<ProductionFact> = emptyList()) =
        AnalyticsServiceImpl(
            analyticsRepository = object : AnalyticsRepository {
                override suspend fun saleLineFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant) = sales
                override suspend fun productionFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant) = batches
            },
            ingredientRepository = FakeIngredientRepository(),
            stockMovementRepository = FakeStockMovementRepository(),
            forecastRepository = FakeForecastRepository(forecasts),
            reportingTz = "America/Santiago"
        )

    @Test
    fun `sales by day grouped by type and the product mix merge case and accent variants`() = runTest {
        val svc = service(listOf(line("Pastelería", 1000), line("PASTELERIA", 500), line("pasteleria", 500), line("Pan", 2000)))

        val byType = svc.salesByDay(tenant, null, from, to, groupByProductType = true)
        assertEquals(setOf("Pan", "Pastelería"), byType.map { it.productType }.toSet())
        assertEquals(2000L, byType.single { it.productType == "Pastelería" }.revenueMinor)

        val mix = svc.productMix(tenant, null, from, to)
        assertEquals(listOf("Pan" to 2000L, "Pastelería" to 2000L).toSet(), mix.map { it.productType to it.revenueMinor }.toSet())
        assertEquals(2, mix.size)
    }

    private fun forecast(productId: Uuid, name: String, type: String, units: Int?, grams: Long? = null, model: String = "baseline-trailing-mean") =
        Forecast(
            Uuid.random(), tenant, branch, productId, name, type, LocalDate(2026, 3, 10), grams, units, model, at
        )

    @Test
    fun `forecast vs actual lines a PAN forecast up with Pan sales and a lowercase filter`() = runTest {
        val svc = service(
            listOf(line("Pan", 1000, units = 25), line("Pan", 1000, units = 5)),
            listOf(forecast(marraqueta, "Marraqueta", "PAN", 40))
        )

        val row = svc.forecastVsActual(tenant, branch, "pan", null, from, to).single()
        assertEquals(40, row.predictedUnits)
        assertEquals(30L, row.actualUnits)
    }

    @Test
    fun `forecast vs actual returns one row per product, with its type and estimator`() = runTest {
        val svc = service(
            listOf(
                line("Pan", 1000, units = 25),
                line("Pan", 500, units = 7, productId = hallulla, name = "Hallulla"),
            ),
            listOf(
                forecast(marraqueta, "Marraqueta", "Pan", 40),
                forecast(hallulla, "Hallulla", "Pan", 9, model = "lgbm-x"),
            )
        )

        val rows = svc.forecastVsActual(tenant, branch, null, null, from, to).associateBy { it.productId }
        assertEquals(2, rows.size)
        val m = rows.getValue(marraqueta.toString())
        assertEquals("Marraqueta", m.productName)
        assertEquals("Pan", m.productType)
        assertEquals(40, m.predictedUnits)
        assertEquals(25L, m.actualUnits)
        assertEquals("baseline-trailing-mean", m.modelVersion)
        assertEquals("lgbm-x", rows.getValue(hallulla.toString()).modelVersion)
        assertEquals(7L, rows.getValue(hallulla.toString()).actualUnits)
    }

    @Test
    fun `forecast vs actual can narrow to one product and never reports Almacen`() = runTest {
        val svc = service(
            listOf(
                line("Pan", 1000, units = 25),
                line("Pan", 500, units = 7, productId = hallulla, name = "Hallulla"),
                line("Almacén", 800, units = 3, productId = cola, name = "Cola", origin = ProductOrigin.ALMACEN),
            ),
            listOf(forecast(marraqueta, "Marraqueta", "Pan", 40), forecast(hallulla, "Hallulla", "Pan", 9))
        )

        val all = svc.forecastVsActual(tenant, branch, null, null, from, to)
        assertEquals(setOf(marraqueta.toString(), hallulla.toString()), all.map { it.productId }.toSet())

        val one = svc.forecastVsActual(tenant, branch, null, hallulla, from, to).single()
        assertEquals("Hallulla", one.productName)
        assertEquals(9, one.predictedUnits)
    }

    @Test
    fun `production summary merges type spellings`() = runTest {
        val svc = service(emptyList(), batches = listOf(ProductionFact(branch, product, "Marraqueta", at, "Pan", 100), ProductionFact(branch, product, "Marraqueta", at, "PAN", 50)))
        val row = svc.productionSummary(tenant, null, from, to).single()
        assertEquals(2L, row.batchCount)
        assertEquals(150L, row.mixWeightGrams)
    }
}
