package com.f776.ecopan.api

import com.f776.ecopan.api.feature.analytics.domain.estimateWaste
import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.model.ProductionFact
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** HU-ANA-0005: produced-minus-sold per day and product type, flagged as an estimate. */
class WasteEstimateTest {

    private val zone = ZoneId.of("America/Santiago")
    private val branch = Uuid.random()
    private val product = Uuid.random()

    // 2026-03-10 15:00 UTC is mid-afternoon in Santiago, well inside the local day.
    private val day1 = Instant.parse("2026-03-10T15:00:00Z")
    private val day2 = Instant.parse("2026-03-11T15:00:00Z")

    private fun batch(at: Instant, type: String, grams: Long) = ProductionFact(branch, product, "Producto", at, type, grams)

    private fun line(at: Instant, type: String, grams: Long? = null, quantity: Int? = null) = SaleLineFact(
        saleId = Uuid.random(), branchId = branch, soldAt = at, paymentMethod = PaymentMethod.CASH,
        productId = Uuid.random(), productName = "P", origin = ProductOrigin.PANADERIA, productType = type,
        soldBy = SoldBy.WEIGHT, weightGrams = grams, quantity = quantity, lineTotalMinor = 1000,
    )

    @Test
    fun `waste is what was produced minus what was sold that day`() {
        val result = estimateWaste(
            production = listOf(batch(day1, "BREAD", 20_000), batch(day1, "BREAD", 10_000)),
            sales = listOf(line(day1, "BREAD", grams = 22_000)),
            zone = zone,
        ).single()

        assertEquals(30_000, result.producedGrams)
        assertEquals(22_000, result.soldGrams)
        assertEquals(8_000, result.estimatedWasteGrams)
    }

    @Test
    fun `every row is marked as an estimate`() {
        val rows = estimateWaste(listOf(batch(day1, "BREAD", 5_000)), listOf(line(day1, "BREAD", grams = 1_000)), zone)

        assertTrue(rows.all { it.estimated })
    }

    @Test
    fun `selling more than was produced is never negative waste`() {
        val result = estimateWaste(listOf(batch(day1, "BREAD", 5_000)), listOf(line(day1, "BREAD", grams = 9_000)), zone).single()

        assertEquals(0, result.estimatedWasteGrams)
    }

    @Test
    fun `a day with sales and no batch reports zero produced`() {
        val result = estimateWaste(emptyList(), listOf(line(day2, "BREAD", grams = 3_000)), zone).single()

        assertEquals(0, result.producedGrams)
        assertEquals(0, result.estimatedWasteGrams)
    }

    @Test
    fun `a product type sold by unit has no gram waste to report`() {
        val result = estimateWaste(listOf(batch(day1, "PASTRY", 8_000)), listOf(line(day1, "PASTRY", quantity = 12)), zone).single()

        assertEquals(12, result.soldUnits)
        assertNull(result.estimatedWasteGrams)
    }

    @Test
    fun `days and product types are reported separately and in date order`() {
        val rows = estimateWaste(
            production = listOf(batch(day2, "BREAD", 4_000), batch(day1, "BREAD", 6_000), batch(day1, "ROLLS", 2_000)),
            sales = emptyList(),
            zone = zone,
        )

        assertEquals(listOf("2026-03-10" to "BREAD", "2026-03-10" to "ROLLS", "2026-03-11" to "BREAD"), rows.map { it.date to it.productType })
    }

    @Test
    fun `the local day, not the UTC day, decides the bucket`() {
        // 02:00 UTC on the 11th is 23:00 on the 10th in Santiago (UTC-3 in March).
        val lateEvening = Instant.parse("2026-03-11T02:00:00Z")

        val result = estimateWaste(listOf(batch(lateEvening, "BREAD", 1_000)), emptyList(), zone).single()

        assertEquals("2026-03-10", result.date)
    }
}

class WasteEstimateTypeVariantsTest {
    private val zone = ZoneId.of("America/Santiago")
    private val branch = Uuid.random()
    private val product = Uuid.random()
    private val at = Instant.parse("2026-03-10T15:00:00Z")

    @Test
    fun `Pan and PAN are one series`() {
        val result = estimateWaste(
            production = listOf(ProductionFact(branch, product, "Producto", at, "Pan", 20_000), ProductionFact(branch, product, "Producto", at, "PAN", 10_000)),
            sales = listOf(
                SaleLineFact(
                    saleId = Uuid.random(), branchId = branch, soldAt = at, paymentMethod = PaymentMethod.CASH,
                    productId = Uuid.random(), productName = "P", origin = ProductOrigin.PANADERIA, productType = "pan",
                    soldBy = SoldBy.WEIGHT, weightGrams = 25_000, quantity = null, lineTotalMinor = 1000
                )
            ),
            zone = zone,
        ).single()
        assertEquals(30_000L, result.producedGrams)
        assertEquals(5_000L, result.estimatedWasteGrams)
    }
}
