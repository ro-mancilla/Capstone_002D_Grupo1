package com.f776.ecopan.core.domain.formula

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class UnitEstimateTest {

    @Test
    fun `no configured ratio means no estimate`() {
        assertNull(estimateLoaves(mixGrams = 12_500, mixToLoafRatioMilli = null))
    }

    @Test
    fun `rounds to nearest rather than truncating`() {
        // 12.5 kg at 2200 milli = 27.5 loaves. Plan 06 section 2 specifies "rounded", and the
        // baker approves the rounded number on the keypad -- truncating here is what let the
        // screen read 28 while the row stored 27.
        assertEquals(28, estimateLoaves(mixGrams = 12_500, mixToLoafRatioMilli = 2200))
    }

    @Test
    fun `rounds a half up and anything below a half down`() {
        assertEquals(1, estimateLoaves(mixGrams = 500, mixToLoafRatioMilli = 1000))
        assertEquals(0, estimateLoaves(mixGrams = 499, mixToLoafRatioMilli = 1000))
        assertEquals(3, estimateLoaves(mixGrams = 2_500, mixToLoafRatioMilli = 1000))
    }

    @Test
    fun `an exact multiple is unchanged by the rounding`() {
        assertEquals(10, estimateLoaves(mixGrams = 10_000, mixToLoafRatioMilli = 1000))
        assertEquals(22, estimateLoaves(mixGrams = 10_000, mixToLoafRatioMilli = 2200))
    }

    @Test
    fun `an empty mix estimates nothing rather than failing`() {
        assertEquals(0, estimateLoaves(mixGrams = 0, mixToLoafRatioMilli = 2200))
    }

    @Test
    fun `a negative mix is a programming error`() {
        assertFailsWith<IllegalArgumentException> { estimateLoaves(mixGrams = -1, mixToLoafRatioMilli = 2200) }
    }

    @Test
    fun `a non-positive ratio is a programming error`() {
        assertFailsWith<IllegalArgumentException> { estimateLoaves(mixGrams = 10_000, mixToLoafRatioMilli = 0) }
    }
}
