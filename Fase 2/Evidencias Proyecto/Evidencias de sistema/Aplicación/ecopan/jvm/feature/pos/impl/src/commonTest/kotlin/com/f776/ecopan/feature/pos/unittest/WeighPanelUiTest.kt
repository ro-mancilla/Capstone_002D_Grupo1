package com.f776.ecopan.feature.pos.unittest

import com.f776.ecopan.core.domain.money.lineTotalForWeight
import com.f776.ecopan.feature.pos.screens.pos.WeighPanelUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** The weigh session's rules, without a scale, a ViewModel or a coroutine scheduler. */
class WeighPanelUiTest {

    private fun panel(manual: Boolean = false) = WeighPanelUi(
        productId = Uuid.random(),
        productName = "Marraqueta",
        pricePerKgMinor = 2_290,
        grams = 0,
        stable = false,
        isManualEntry = manual,
    )

    @Test
    fun `an empty scale is never addable`() {
        assertFalse(panel().canAdd)
        assertFalse(panel().onReading(grams = 0, stable = true).canAdd)
        assertFalse(panel(manual = true).canAdd)
    }

    @Test
    fun `a scale reading is addable only once it settles`() {
        assertFalse(panel().onReading(grams = 845, stable = false).canAdd)
        assertTrue(panel().onReading(grams = 845, stable = true).canAdd)
    }

    @Test
    fun `a manual weight needs no stability because there is no scale to settle`() {
        val typed = panel(manual = true).onManualText("0,520").onReading(grams = 520, stable = false)

        assertTrue(typed.canAdd)
    }

    @Test
    fun `clearing the manual field disarms add rather than keeping the last weight`() {
        // The defect this closes: the text and the weight were separate state, and clearing the
        // text left the previous grams addable -- so the till would charge for a weight the
        // cashier could no longer see.
        val typed = panel(manual = true).onManualText("0,520").onReading(grams = 520, stable = false)
        assertTrue(typed.canAdd)

        val cleared = typed.onManualText("")
        assertFalse(cleared.canAdd)
        assertEquals(0, cleared.grams)

        val partial = typed.onManualText("0,")
        assertFalse(partial.canAdd)
        assertEquals(0, partial.grams)
    }

    @Test
    fun `re-typing a weight after clearing arms add again`() {
        val again = panel(manual = true)
            .onManualText("0,520")
            .onReading(grams = 520, stable = false)
            .onManualText("")
            .onManualText("0,750")
            .onReading(grams = 750, stable = false)

        assertTrue(again.canAdd)
        assertEquals(750, again.grams)
    }

    @Test
    fun `the quoted price is the price the line will be charged`() {
        for (grams in listOf(1L, 499L, 500L, 501L, 845L, 1_000L, 12_345L)) {
            val weighing = panel().onReading(grams = grams, stable = true)

            assertEquals(lineTotalForWeight(grams, 2_290), weighing.previewTotalMinor)
        }
    }

    @Test
    fun `the manual text is kept as typed so the field does not fight the cashier`() {
        assertEquals("0,", panel(manual = true).onManualText("0,").manualKgText)
        assertEquals("1,250", panel(manual = true).onManualText("1,250").manualKgText)
    }
}
