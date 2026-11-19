package com.f776.ecopan.feature.pos.unittest

import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.core.domain.money.lineTotalForWeight
import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.feature.pos.screens.pos.Cart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * The cart's rules, without a ViewModel, a dispatcher or eight fakes. Asserting that two
 * empanadas cost 5.600 used to cost the whole POS rig plus three `advanceUntilIdle()` round
 * trips, seven of whose fakes had nothing to do with the arithmetic.
 */
class CartTest {

    private val empanada = product(name = "Empanada", priceMinor = 2_800)
    private val marraqueta = product(name = "Marraqueta", pricePerKgMinor = 2_000)

    @Test
    fun `the total is the lines always`() {
        val cart = Cart().addUnit(empanada).addUnit(empanada)

        assertEquals(5_600, cart.totalMinor)
        assertEquals(cart.lines.sumOf { it.totalMinor }, cart.totalMinor)
    }

    @Test
    fun `tapping the same unit product again increments its line instead of adding another`() {
        val cart = Cart().addUnit(empanada).addUnit(empanada).addUnit(empanada)

        assertEquals(1, cart.lines.size)
        assertEquals(3, cart.lines.single().quantity)
        assertEquals(8_400, cart.totalMinor)
    }

    @Test
    fun `the caption follows the quantity`() {
        val one = Cart().addUnit(empanada)
        val two = one.addUnit(empanada)

        assertEquals("1 u. × $2.800", one.lines.single().metaLabel)
        assertEquals("2 u. × $2.800", two.lines.single().metaLabel)
    }

    @Test
    fun `two weighings of one product stay two lines`() {
        val cart = Cart()
            .addWeighed(marraqueta, grams = 500, pricePerKgMinor = 2_000)
            .addWeighed(marraqueta, grams = 750, pricePerKgMinor = 2_000)

        assertEquals(2, cart.lines.size)
        assertEquals(listOf(500L, 750L), cart.lines.map { it.weightGrams })
    }

    @Test
    fun `a weighed line is priced by the shared money rule`() {
        val cart = Cart().addWeighed(marraqueta, grams = 845, pricePerKgMinor = 2_000)

        assertEquals(lineTotalForWeight(845, 2_000), cart.lines.single().totalMinor)
        assertEquals(SoldBy.WEIGHT, cart.lines.single().soldBy)
    }

    @Test
    fun `stepping down to zero removes the line`() {
        val cart = Cart().addUnit(empanada).addUnit(empanada)
        val lineId = cart.lines.single().lineId

        val stepped = cart.step(lineId, -1)
        assertEquals(1, stepped.lines.single().quantity)
        assertEquals(2_800, stepped.totalMinor)

        val emptied = stepped.step(lineId, -1)
        assertTrue(emptied.isEmpty)
        assertEquals(0, emptied.totalMinor)
    }

    @Test
    fun `stepping a weighed line does nothing since it has no quantity`() {
        val cart = Cart().addWeighed(marraqueta, grams = 500, pricePerKgMinor = 2_000)
        val before = cart.lines.single()

        assertEquals(cart, cart.step(before.lineId, 1))
    }

    @Test
    fun `voiding a line drops it and the total follows`() {
        val cart = Cart().addUnit(empanada).addWeighed(marraqueta, grams = 500, pricePerKgMinor = 2_000)
        val unitLine = cart.lines.first { it.soldBy == SoldBy.UNIT }

        val voided = cart.remove(unitLine.lineId)
        assertEquals(1, voided.lines.size)
        assertEquals(voided.lines.sumOf { it.totalMinor }, voided.totalMinor)
    }

    @Test
    fun `a product with no unit price cannot be added by unit`() {
        val cart = Cart().addUnit(marraqueta)

        assertTrue(cart.isEmpty)
        assertEquals(0, cart.totalMinor)
    }

    @Test
    fun `an unknown line id changes nothing`() {
        val cart = Cart().addUnit(empanada)

        assertEquals(cart, cart.step(Uuid.random(), 1))
        assertEquals(cart, cart.remove(Uuid.random()))
    }

    private fun product(name: String, priceMinor: Long? = null, pricePerKgMinor: Long? = null) = Product(
        id = Uuid.random(),
        tenantId = Uuid.random(),
        name = name,
        productType = "Pan",
        soldBy = if (pricePerKgMinor != null) SoldBy.WEIGHT else SoldBy.UNIT,
        pricePerKgMinor = pricePerKgMinor,
        priceMinor = priceMinor,
        barcode = null,
        imageUrl = null,
        active = true,
        mixToLoafRatioMilli = null,
        minMixGrams = null,
        maxMixGrams = null,
    )
}
