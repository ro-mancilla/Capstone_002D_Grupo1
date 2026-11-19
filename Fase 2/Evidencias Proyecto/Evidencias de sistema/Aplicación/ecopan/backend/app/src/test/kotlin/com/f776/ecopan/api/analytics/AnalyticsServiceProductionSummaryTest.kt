package com.f776.ecopan.api.analytics

import com.f776.ecopan.api.fakes.FakeForecastRepository
import com.f776.ecopan.api.fakes.FakeIngredientRepository
import com.f776.ecopan.api.fakes.FakeStockMovementRepository
import com.f776.ecopan.api.feature.analytics.domain.AnalyticsServiceImpl
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

private class ProductionOnlyAnalyticsRepository(private val facts: List<ProductionFact>) : AnalyticsRepository {
    var lastBranchFilter: Uuid? = null

    override suspend fun saleLineFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<SaleLineFact> =
        emptyList()

    override suspend fun productionFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductionFact> {
        lastBranchFilter = branchId
        return facts.filter { branchId == null || it.branchId == branchId }
    }
}



/** HU-ANA-0003: K6 groups by reporting-tz day x branch x product, not just product type. */
class AnalyticsServiceProductionSummaryTest {
    private val tenant = Uuid.random()
    private val north = Uuid.random()
    private val south = Uuid.random()
    private val marraqueta = Uuid.random()
    private val croissant = Uuid.random()

    private fun fact(branch: Uuid, product: Uuid, name: String, type: String, at: String, grams: Long) =
        ProductionFact(branch, product, name, Instant.parse(at), type, grams)

    private fun service(repo: AnalyticsRepository, tz: String = "America/Santiago") = AnalyticsServiceImpl(
        repo, FakeIngredientRepository(), FakeStockMovementRepository(), FakeForecastRepository(), tz
    )

    private val range = Instant.parse("2026-01-01T00:00:00Z") to Instant.parse("2026-01-31T00:00:00Z")

    @Test
    fun `sums dough weight and counts batches per day branch and product`() = runTest {
        val repo = ProductionOnlyAnalyticsRepository(
            listOf(
                fact(north, marraqueta, "Marraqueta", "PAN", "2026-01-10T13:00:00Z", 10_000),
                fact(north, marraqueta, "Marraqueta", "PAN", "2026-01-10T14:00:00Z", 8_000),
                fact(south, marraqueta, "Marraqueta", "PAN", "2026-01-10T13:00:00Z", 5_000),
                fact(north, croissant, "Croissant", "PASTELERIA", "2026-01-10T13:00:00Z", 3_000)
            )
        )

        val rows = service(repo).productionSummary(tenant, null, range.first, range.second)

        assertEquals(3, rows.size)
        val northMarraqueta = rows.single { it.branchId == north.toString() && it.productId == marraqueta.toString() }
        assertEquals(2L, northMarraqueta.batchCount)
        assertEquals(18_000L, northMarraqueta.mixWeightGrams)
        assertEquals("Marraqueta", northMarraqueta.productName)
        assertEquals("PAN", northMarraqueta.productType)
        assertEquals("2026-01-10", northMarraqueta.date)
        assertEquals(5_000L, rows.single { it.branchId == south.toString() }.mixWeightGrams)
    }

    @Test
    fun `buckets a late-evening batch into the reporting-timezone day`() = runTest {
        // 02:00Z on the 11th is 23:00 on the 10th in Santiago (UTC-3 in January).
        val repo = ProductionOnlyAnalyticsRepository(
            listOf(fact(north, marraqueta, "Marraqueta", "PAN", "2026-01-11T02:00:00Z", 1_000))
        )

        val rows = service(repo).productionSummary(tenant, null, range.first, range.second)

        assertEquals("2026-01-10", rows.single().date)
    }

    @Test
    fun `passes the branch filter through`() = runTest {
        val repo = ProductionOnlyAnalyticsRepository(
            listOf(
                fact(north, marraqueta, "Marraqueta", "PAN", "2026-01-10T13:00:00Z", 10_000),
                fact(south, marraqueta, "Marraqueta", "PAN", "2026-01-10T13:00:00Z", 5_000)
            )
        )

        val rows = service(repo).productionSummary(tenant, south, range.first, range.second)

        assertEquals(south, repo.lastBranchFilter)
        assertEquals(listOf(south.toString()), rows.map { it.branchId })
    }
}
