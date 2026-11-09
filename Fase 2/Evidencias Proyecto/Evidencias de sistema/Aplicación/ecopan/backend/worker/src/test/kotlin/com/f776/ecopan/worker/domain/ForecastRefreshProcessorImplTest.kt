package com.f776.ecopan.worker.domain

import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested
import com.f776.ecopan.worker.fakes.FakeAnalyticsRepository
import com.f776.ecopan.worker.fakes.FakeForecastRepository
import com.f776.ecopan.worker.fakes.FakeMlForecastClient
import com.f776.ecopan.worker.ml.MlForecastRequest
import com.f776.ecopan.worker.ml.MlForecastResponse
import com.f776.ecopan.worker.ml.MlHistoryPoint
import com.f776.ecopan.worker.ml.MlSeriesHistory
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ForecastRefreshProcessorImplTest {

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val tz = "America/Santiago"
    private val zone = TimeZone.of(tz)

    // 2026-07-29T19:00Z = 15:00 in Santiago (UTC-4, southern winter): mid-afternoon "today".
    private val now = Instant.parse("2026-07-29T19:00:00Z")
    private val today = LocalDate(2026, 7, 29)

    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }

    /** Noon local time [daysAgo] days before [today] -- safely inside that local date. */
    private fun at(daysAgo: Int): Instant =
        today.plus(-daysAgo, DateTimeUnit.DAY).atStartOfDayIn(zone) + 12.hours

    private val breadId = Uuid.random()
    private val cakeId = Uuid.random()

    private fun fact(
        soldAt: Instant,
        productType: String = "BREAD",
        soldBy: SoldBy = SoldBy.WEIGHT,
        grams: Long? = 500,
        qty: Int? = null,
        productId: Uuid = if (soldBy == SoldBy.WEIGHT) breadId else cakeId,
        origin: ProductOrigin = ProductOrigin.PANADERIA,
    ) = SaleLineFact(
        saleId = Uuid.random(), branchId = branchId, soldAt = soldAt, paymentMethod = PaymentMethod.CASH,
        productId = productId, productName = "P-$productId", productType = productType, origin = origin,
        soldBy = soldBy, weightGrams = grams, quantity = qty, lineTotalMinor = 1000
    )

    private fun message(productType: String? = null) =
        ForecastRefreshRequested(tenantId.toString(), branchId.toString(), productType)

    private class Harness(
        val analytics: FakeAnalyticsRepository = FakeAnalyticsRepository(),
        val forecasts: FakeForecastRepository = FakeForecastRepository(),
        val ml: FakeMlForecastClient = FakeMlForecastClient(),
    )

    private fun Harness.processor() = ForecastRefreshProcessorImpl(
        analyticsRepository = analytics,
        forecastRepository = forecasts,
        mlForecastClient = ml,
        reportingTz = tz,
        clock = fixedClock,
    )

    @Test
    fun `buckets sales into reporting-timezone days and zero-fills the gaps up to yesterday`() = runTest {
        val h = Harness()
        h.analytics.facts += fact(at(daysAgo = 5), grams = 300)
        h.analytics.facts += fact(at(daysAgo = 5), grams = 200)
        h.analytics.facts += fact(at(daysAgo = 2), grams = 700)

        h.processor().processMessage(message())

        val series = h.ml.lastRequest!!.series.single()
        // First sale 5 days ago through yesterday inclusive: 5 dense points, gaps as zero demand.
        assertEquals(5, series.history.size)
        assertEquals(today.plus(-5, DateTimeUnit.DAY).toString(), series.history.first().day)
        assertEquals(listOf(500.0, 0.0, 0.0, 700.0, 0.0), series.history.map { it.demand })
    }

    @Test
    fun `a sale late in UTC lands on its local reporting day`() = runTest {
        val h = Harness()
        // 02:00Z on the 29th is 22:00 on the 28th in Santiago -- must bucket as the 28th.
        h.analytics.facts += fact(Instant.parse("2026-07-29T02:00:00Z"), grams = 400)

        h.processor().processMessage(message())

        val series = h.ml.lastRequest!!.series.single()
        assertEquals(listOf(MlHistoryPoint("2026-07-28", 400.0)), series.history)
    }

    @Test
    fun `today's partial sales are excluded and start_day is today`() = runTest {
        val h = Harness()
        h.analytics.facts += fact(at(daysAgo = 1), grams = 600)
        h.analytics.facts += fact(now - 1.hours, grams = 999) // today, must not enter history

        h.processor().processMessage(message())

        val request = h.ml.lastRequest!!
        assertEquals(today.toString(), request.startDay)
        assertEquals(7, request.horizonDays)
        assertEquals(listOf(MlHistoryPoint(today.plus(-1, DateTimeUnit.DAY).toString(), 600.0)), request.series.single().history)
    }

    @Test
    fun `predictions map to grams or units by the product's soldBy`() = runTest {
        val h = Harness()
        h.ml.predictedValue = 12.6
        h.analytics.facts += fact(at(1), productType = "BREAD", soldBy = SoldBy.WEIGHT, grams = 500)
        h.analytics.facts += fact(at(1), productType = "CAKE", soldBy = SoldBy.UNIT, grams = null, qty = 3)

        h.processor().processMessage(message())

        val bread = h.forecasts.upserts.filter { it.productId == breadId }
        val cake = h.forecasts.upserts.filter { it.productId == cakeId }
        assertEquals(7, bread.size)
        assertEquals(7, cake.size)
        bread.forEach {
            assertEquals(13L, it.predictedGrams)
            assertNull(it.predictedUnits)
            assertEquals("lgbm-test", it.modelVersion)
        }
        cake.forEach {
            assertNull(it.predictedGrams)
            assertEquals(13, it.predictedUnits)
        }
        // The UNIT series' demand came from quantity, not weight.
        assertEquals(3.0, h.ml.lastRequest!!.series.single { it.productType == cakeId.toString() }.history.single().demand)
        // 7 consecutive dates starting today.
        assertEquals(
            (0 until 7).map { today.plus(it, DateTimeUnit.DAY) },
            bread.map { it.forecastDate }.sorted()
        )
    }

    @Test
    fun `a refresh never rewrites a past day's forecast, so accuracy can score it later`() = runTest {
        val h = Harness()
        h.analytics.facts += fact(at(daysAgo = 3), grams = 500)

        h.processor().processMessage(message())

        assertTrue(h.forecasts.upserts.isNotEmpty())
        assertTrue(h.forecasts.upserts.all { it.forecastDate >= today })
    }

    @Test
    fun `two products of one type produce two series, keyed by product id`() = runTest {
        val h = Harness()
        val marraqueta = Uuid.random()
        val hallulla = Uuid.random()
        h.analytics.facts += fact(at(1), productType = "Pan", productId = marraqueta, grams = 800)
        h.analytics.facts += fact(at(1), productType = "Pan", productId = hallulla, soldBy = SoldBy.UNIT, grams = null, qty = 12)

        h.processor().processMessage(message())

        val series = h.ml.lastRequest!!.series
        assertEquals(setOf(marraqueta.toString(), hallulla.toString()), series.map { it.productType }.toSet())
        assertEquals(800.0, series.single { it.productType == marraqueta.toString() }.history.single().demand)
        assertEquals(12.0, series.single { it.productType == hallulla.toString() }.history.single().demand)
        assertEquals(setOf(marraqueta, hallulla), h.forecasts.upserts.map { it.productId }.toSet())
    }

    @Test
    fun `Almacen sales are ignored`() = runTest {
        val h = Harness()
        val cola = Uuid.random()
        h.analytics.facts += fact(at(1))
        h.analytics.facts += fact(
            at(1), productType = "Almacén", productId = cola, origin = ProductOrigin.ALMACEN,
            soldBy = SoldBy.UNIT, grams = null, qty = 9
        )

        h.processor().processMessage(message())

        assertEquals(listOf(breadId.toString()), h.ml.lastRequest!!.series.map { it.productType })
        assertTrue(h.forecasts.upserts.none { it.productId == cola })
    }

    @Test
    fun `only Almacen sales means no ML call`() = runTest {
        val h = Harness()
        h.analytics.facts += fact(at(1), productType = "Almacén", origin = ProductOrigin.ALMACEN, soldBy = SoldBy.UNIT, grams = null, qty = 3)

        h.processor().processMessage(message())

        assertNull(h.ml.lastRequest)
    }

    @Test
    fun `message productType narrows to that type's products, ignoring case and accents`() = runTest {
        val h = Harness()
        h.analytics.facts += fact(at(1), productType = "Pan")
        h.analytics.facts += fact(at(1), productType = "Pastelería", soldBy = SoldBy.UNIT, grams = null, qty = 2)

        h.processor().processMessage(message(productType = "PASTELERIA"))

        assertEquals(listOf(cakeId.toString()), h.ml.lastRequest!!.series.map { it.productType })
        assertTrue(h.forecasts.upserts.all { it.productId == cakeId })
    }

    @Test
    fun `no history means no ML call and no upserts`() = runTest {
        val h = Harness()

        h.processor().processMessage(message())

        assertNull(h.ml.lastRequest)
        assertTrue(h.forecasts.upserts.isEmpty())
    }

    @Test
    fun `a product with mixed-unit lines sums only its majority unit`() = runTest {
        val h = Harness()
        h.analytics.facts += fact(at(1), soldBy = SoldBy.WEIGHT, grams = 500)
        h.analytics.facts += fact(at(1), soldBy = SoldBy.WEIGHT, grams = 250)
        h.analytics.facts += fact(at(1), soldBy = SoldBy.UNIT, grams = null, qty = 4, productId = breadId)

        h.processor().processMessage(message())

        // Majority is WEIGHT (2 lines vs 1): grams summed, the unit line ignored.
        assertEquals(750.0, h.ml.lastRequest!!.series.single().history.single().demand)
        assertTrue(h.forecasts.upserts.all { it.predictedGrams != null && it.predictedUnits == null })
    }

    @Test
    fun `unparseable ids drop the message without side effects`() = runTest {
        val h = Harness()
        h.analytics.facts += fact(at(1))

        h.processor().processMessage(ForecastRefreshRequested("not-a-uuid", branchId.toString(), null))

        assertNull(h.analytics.lastQuery)
        assertNull(h.ml.lastRequest)
        assertTrue(h.forecasts.upserts.isEmpty())
    }

    @Test
    fun `a branch with many products is sent in bounded chunks, one transaction each`() = runTest {
        val h = Harness()
        repeat(120) { h.analytics.facts += fact(at(1), productId = Uuid.random()) }

        h.processor().processMessage(message())

        assertEquals(listOf(50, 50, 20), h.ml.requests.map { it.series.size })
        assertEquals(3, h.forecasts.transactions)
        assertEquals(120 * 7, h.forecasts.upserts.size)
    }

    @Test
    fun `a failing chunk does not stop the others, and the failure still surfaces`() = runTest {
        val h = Harness()
        repeat(60) { h.analytics.facts += fact(at(1), productId = Uuid.random()) }
        h.ml.failNext = IllegalStateException("ml down")

        val failure = runCatching { h.processor().processMessage(message()) }.exceptionOrNull()

        assertEquals("ml down", failure?.message)
        assertEquals(10 * 7, h.forecasts.upserts.size, "the second chunk (10 series) was still stored")
    }

    @Test
    fun `a product deleted while the ML call ran is skipped, the rest of the branch is stored`() = runTest {
        val h = Harness()
        val gone = Uuid.random()
        h.forecasts.unknownProducts += gone
        h.analytics.facts += fact(at(1), productId = gone)
        h.analytics.facts += fact(at(1))

        h.processor().processMessage(message())

        assertEquals(setOf(breadId), h.forecasts.upserts.map { it.productId }.toSet())
    }

    @Test
    fun `the repository is told today as the earliest day it may write`() = runTest {
        val h = Harness()
        h.analytics.facts += fact(at(1))

        h.processor().processMessage(message())

        // The fake mimics the refusal: nothing before today is stored.
        assertTrue(h.forecasts.upserts.all { it.forecastDate >= today })
    }

    @Test
    fun `request and response JSON match the ML service's snake_case contract`() {
        val request = MlForecastRequest(
            series = listOf(
                MlSeriesHistory(branchId = "b1", productType = "11111111-1111-1111-1111-111111111111", history = listOf(MlHistoryPoint("2026-07-28", 500.0)))
            ),
            horizonDays = 7,
            startDay = "2026-07-29",
        )
        val encoded = Json.encodeToString(MlForecastRequest.serializer(), request)
        listOf("\"horizon_days\"", "\"start_day\"", "\"branch_id\"", "\"product_type\"", "\"history\"", "\"day\"", "\"demand\"")
            .forEach { field -> assertTrue(field in encoded, "expected $field in $encoded") }

        val response = """
            {
              "forecasts": [{
                "branch_id": "b1", "product_type": "BREAD", "model_version": "lgbm-abc",
                "observed_days": 42,
                "predictions": [{"day": "2026-07-29", "predicted": 12.5}]
              }],
              "generated_at": "2026-07-29T15:00:00+00:00",
              "service_model_version": "lgbm-abc"
            }
        """.trimIndent()
        val decoded = Json.decodeFromString(MlForecastResponse.serializer(), response)
        assertEquals("lgbm-abc", decoded.forecasts.single().modelVersion)
        assertEquals(12.5, decoded.forecasts.single().predictions.single().predicted)
    }
}
