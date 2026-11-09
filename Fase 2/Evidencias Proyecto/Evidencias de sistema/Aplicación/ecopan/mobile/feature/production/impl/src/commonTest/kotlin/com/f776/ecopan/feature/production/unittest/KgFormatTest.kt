package com.f776.ecopan.feature.production.unittest

import com.f776.ecopan.feature.production.screens.formatAmount
import com.f776.ecopan.feature.production.screens.formatKg
import kotlin.test.Test
import kotlin.test.assertEquals

class KgFormatTest {

    @Test
    fun `kilograms always carry exactly three decimals`() {
        assertEquals("24,000", formatKg(24_000))
        assertEquals("12,500", formatKg(12_500))
        assertEquals("0,007", formatKg(7))
        assertEquals("0,000", formatKg(0))
        assertEquals("4,940", formatKg(4_940))
    }

    /** 3 869 g was shown as "3,86 kg" when the third decimal was truncated (issue #84). */
    @Test
    fun `a weight is shown to the gram, never truncated`() {
        assertEquals("3,869", formatKg(3_869))
        assertEquals("1,239", formatKg(1_239))
        assertEquals("55,879", formatKg(55_879))
        assertEquals("194,048", formatKg(194_048))
    }

    @Test
    fun `thousands are grouped with a dot`() {
        assertEquals("999,000", formatKg(999_000))
        assertEquals("1.000,000", formatKg(1_000_000))
        assertEquals("1.234.567,890", formatKg(1_234_567_890))
    }

    @Test
    fun `negative balances keep the sign outside the digits`() {
        assertEquals("-33,279", formatKg(-33_279))
        assertEquals("-0,664", formatKg(-664))
    }

    @Test
    fun `sub-kilogram amounts read in grams`() {
        assertEquals("37 g", formatAmount(37))
        assertEquals("999 g", formatAmount(999))
        assertEquals("1,000 kg", formatAmount(1_000))
        assertEquals("3,869 kg", formatAmount(3_869))
        assertEquals("7,438 kg", formatAmount(7_438))
    }

    /** The server decrements the same grams (largest-remainder split), so they must sum to the batch. */
    @Test
    fun `a 10 kg batch preview adds up to the batch weight at the shown precision`() {
        val grams = listOf(5_952L, 3_869L, 119L, 60L)
        assertEquals(10_000L, grams.sum())
        val shown = grams.map { formatAmount(it) }
        assertEquals(listOf("5,952 kg", "3,869 kg", "119 g", "60 g"), shown)
        assertEquals("10,000", formatKg(grams.sum()))
    }
}
