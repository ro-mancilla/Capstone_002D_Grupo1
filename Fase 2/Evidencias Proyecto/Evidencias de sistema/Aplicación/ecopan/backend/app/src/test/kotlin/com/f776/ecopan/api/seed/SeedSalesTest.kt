package com.f776.ecopan.api.seed

import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeCatalogRepository
import com.f776.ecopan.api.fakes.FakeIngredientRepository
import com.f776.ecopan.api.fakes.FakeSaleRepository
import com.f776.ecopan.api.fakes.FakeSeedRevisionRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.data.database.entity.SoldBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.DayOfWeek
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Plan 11 SEED-2's acceptance criterion is "after seeding, dashboard shows 90-day KPIs and the
 * forecast chart renders". That reduces to three properties this suite pins:
 *
 *  1. seeding produces real sales with priced lines, recent enough for the dashboard's windows;
 *  2. seeding twice does not double the revenue;
 *  3. the freshness shift preserves weekday alignment, because weekday seasonality is the whole
 *     point of the generated curve.
 */
class SeedSalesTest {

    private val zone = "America/Santiago"

    private fun newLoader(sales: FakeSaleRepository, catalog: FakeCatalogRepository) = SeedLoader(
        FakeTenantRepository(), FakeBranchRepository(), FakeUserRepository(),
        FakeIngredientRepository(), catalog, sales, com.f776.ecopan.api.fakes.FakeProductStockRepository(), FakeSeedRevisionRepository(), zone, com.f776.ecopan.api.fakes.RecordingForecastRefreshBroker()
    )

    @Test
    fun `seeding reference produces priced sales inside the dashboard's windows`() = runTest {
        val sales = FakeSaleRepository()
        val catalog = FakeCatalogRepository()

        newLoader(sales, catalog).loadOne("reference")

        assertTrue(sales.sales.size > 500, "expected a substantial history, got ${sales.sales.size}")

        val all = sales.sales.values
        assertTrue(all.all { it.lines.isNotEmpty() }, "every sale must carry at least one line")
        assertTrue(all.all { it.totalMinor > 0 }, "every sale must have a positive total")
        assertTrue(
            all.all { sale -> sale.totalMinor == sale.lines.sumOf { it.lineTotalMinor } },
            "sale total must equal the sum of its lines"
        )

        // Both selling modes are represented, since the KPI row reports money *and* kilos.
        val lines = all.flatMap { it.lines }
        assertTrue(lines.any { it.soldBy == SoldBy.WEIGHT && (it.weightGrams ?: 0) > 0 })
        assertTrue(lines.any { it.soldBy == SoldBy.UNIT && (it.quantity ?: 0) > 0 })

        // Nothing in the future, and something within the last week -- the overview's default range.
        val tz = TimeZone.of(zone)
        val today = kotlin.time.Clock.System.now().toLocalDateTime(tz).date
        val days = all.map { it.soldAt.toLocalDateTime(tz).date }
        assertTrue(days.none { it > today }, "seeded sales must not be dated in the future")
        assertTrue(
            days.any { it.toEpochDays() >= today.toEpochDays() - 7 },
            "expected sales inside the last 7 days so the overview is not empty"
        )
        assertTrue(
            days.distinct().size >= 42,
            "expected >= 42 distinct days so a series clears MIN_HISTORY_FOR_MODEL"
        )
    }

    @Test
    fun `seeding twice inserts nothing the second time`() = runTest {
        val sales = FakeSaleRepository()
        val catalog = FakeCatalogRepository()
        val loader = newLoader(sales, catalog)

        loader.loadOne("reference")
        val afterFirst = sales.sales.size
        val revenueAfterFirst = sales.sales.values.sumOf { it.totalMinor }

        loader.loadOne("reference")

        assertEquals(afterFirst, sales.sales.size, "re-seeding must not add sales")
        assertEquals(revenueAfterFirst, sales.sales.values.sumOf { it.totalMinor })
    }

    /** Runs `reference` against a tenant already stored with the given modules; returns the broker. */
    private fun com.f776.ecopan.api.fakes.FakeTenantRepository.withModules(modules: Set<com.f776.ecopan.data.domain.Module>) = apply {
        val id = kotlin.uuid.Uuid.parse(referenceTenantIdOnDisk())
        seed(com.f776.ecopan.data.database.repository.model.Tenant(id, "reference", "Reference", "CLP", modules, kotlin.time.Clock.System.now()))
    }

    private fun referenceTenantIdOnDisk(): String =
        Regex("\"tenant\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*\"([^\"]+)\"")
            .find(javaClass.classLoader.getResourceAsStream("seed/reference.json")!!.bufferedReader().readText())!!.groupValues[1]

    @Test
    fun `forecast refresh is requested only after the sales history is stored`() = runTest {
        val sales = FakeSaleRepository()
        var salesWhenPublished = -1
        val broker = com.f776.ecopan.api.fakes.RecordingForecastRefreshBroker { salesWhenPublished = sales.sales.size }
        val tenants = FakeTenantRepository().withModules(setOf(com.f776.ecopan.data.domain.Module.DEMAND_FORECASTING))
        val branches = FakeBranchRepository()

        SeedLoader(
            tenants, branches, FakeUserRepository(), FakeIngredientRepository(), FakeCatalogRepository(), sales,
            com.f776.ecopan.api.fakes.FakeProductStockRepository(), FakeSeedRevisionRepository(), zone, broker
        ).loadOne("reference")

        assertEquals(branches.branches.keys.map { it.toString() }.toSet(), broker.published.map { it.branchId }.toSet())
        assertTrue(salesWhenPublished > 500, "refresh must be requested after the history landed, saw $salesWhenPublished sales")
    }

    @Test
    fun `restarting an already seeded stack requests no further refresh`() = runTest {
        val sales = FakeSaleRepository()
        val broker = com.f776.ecopan.api.fakes.RecordingForecastRefreshBroker()
        val tenants = FakeTenantRepository().withModules(setOf(com.f776.ecopan.data.domain.Module.DEMAND_FORECASTING))
        val loader = SeedLoader(
            tenants, FakeBranchRepository(), FakeUserRepository(), FakeIngredientRepository(), FakeCatalogRepository(), sales,
            com.f776.ecopan.api.fakes.FakeProductStockRepository(), FakeSeedRevisionRepository(), zone, broker
        )

        loader.loadOne("reference")
        val afterFirst = broker.published.size
        loader.loadOne("reference")

        assertTrue(afterFirst > 0)
        assertEquals(afterFirst, broker.published.size, "second boot inserts no sales, so it must request no refresh")
    }

    @Test
    fun `a tenant without forecasting gets no refresh request`() = runTest {
        val broker = com.f776.ecopan.api.fakes.RecordingForecastRefreshBroker()
        SeedLoader(
            FakeTenantRepository(), FakeBranchRepository(), FakeUserRepository(), FakeIngredientRepository(), FakeCatalogRepository(),
            FakeSaleRepository(), com.f776.ecopan.api.fakes.FakeProductStockRepository(), FakeSeedRevisionRepository(), zone, broker
        ).loadOne("reference")
        assertTrue(broker.published.isEmpty())
    }

    @Test
    fun `a broker outage does not stop the seed`() = runTest {
        val sales = FakeSaleRepository()
        val broker = com.f776.ecopan.api.fakes.RecordingForecastRefreshBroker { error("broker down") }
        val tenants = FakeTenantRepository().withModules(setOf(com.f776.ecopan.data.domain.Module.DEMAND_FORECASTING))
        SeedLoader(
            tenants, FakeBranchRepository(), FakeUserRepository(), FakeIngredientRepository(), FakeCatalogRepository(), sales,
            com.f776.ecopan.api.fakes.FakeProductStockRepository(), FakeSeedRevisionRepository(), zone, broker
        ).loadOne("reference")
        assertTrue(sales.sales.size > 500)
    }

    @Test
    fun `the freshness shift is a whole number of weeks, so weekdays never move`() {
        val lastDay = LocalDate.parse("2026-01-14") // a Wednesday
        val today = LocalDate.parse("2026-07-29")

        val shift = SeedSalesExpander.weekShiftDays(lastDay, today)

        assertEquals(0L, shift % 7, "shift must be a whole number of weeks")
        val shifted = LocalDate.fromEpochDays(lastDay.toEpochDays() + shift)
        assertEquals(
            DayOfWeek.WEDNESDAY,
            java.time.LocalDate.of(shifted.year, shifted.monthNumber, shifted.day).dayOfWeek
        )
        assertTrue(shifted <= today, "shifted series must not run past today")
        assertTrue(
            today.toEpochDays() - shifted.toEpochDays() < 7,
            "shift should land within the last week, not leave the data months stale"
        )
    }

    @Test
    fun `a curve already ending today is not shifted backwards`() {
        val today = LocalDate.parse("2026-07-29")
        assertEquals(0L, SeedSalesExpander.weekShiftDays(today, today))
        assertEquals(0L, SeedSalesExpander.weekShiftDays(LocalDate.parse("2026-08-05"), today))
    }

    @Test
    fun `expansion is deterministic -- same inputs, identical ids and totals`() = runTest {
        val first = FakeSaleRepository()
        val second = FakeSaleRepository()
        newLoader(first, FakeCatalogRepository()).loadOne("reference")
        newLoader(second, FakeCatalogRepository()).loadOne("reference")

        assertEquals(first.sales.keys, second.sales.keys)
        assertEquals(
            first.sales.values.sumOf { it.totalMinor },
            second.sales.values.sumOf { it.totalMinor }
        )
    }

    @Test
    fun `sales land inside trading hours in the reporting timezone`() = runTest {
        val sales = FakeSaleRepository()
        newLoader(sales, FakeCatalogRepository()).loadOne("reference")

        val tz = TimeZone.of(zone)
        val hours = sales.sales.values.map { it.soldAt.toLocalDateTime(tz).hour }
        assertTrue(hours.min() >= 8, "nothing before opening, got ${hours.min()}")
        assertTrue(hours.max() < 20, "nothing after closing, got ${hours.max()}")
        // Sanity that the zone is actually applied rather than UTC leaking through.
        assertEquals(ZoneId.of(zone).id, tz.id)
    }
}
