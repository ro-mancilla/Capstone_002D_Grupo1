package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.domain.hardware.ParsedFrame
import com.f776.ecopan.domain.hardware.ScaleDefaults
import com.f776.ecopan.domain.hardware.ScaleFrameParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class ScaleFrameParserTest {

    private val regex = Regex(ScaleDefaults.FRAME_REGEX)
    private val stable = ScaleDefaults.parseStableValues(ScaleDefaults.STABLE_VALUES)
    private val units = ScaleDefaults.parseUnitFactors(ScaleDefaults.UNIT_FACTORS)

    private fun parse(text: String) = ScaleFrameParser.parse(text, regex, stable, units)

    @Test
    fun `reads the documented example frame`() {
        assertEquals(ParsedFrame(grams = 1234, stable = true), parse("ST,GS, 1.234 kg"))
    }

    @Test
    fun `an unstable flag reads as not stable`() {
        assertEquals(ParsedFrame(grams = 842, stable = false), parse("US,GS,+000.842kg"))
    }

    @Test
    fun `grams are read without scaling`() {
        assertEquals(ParsedFrame(grams = 250, stable = true), parse("ST,NT, 250 g"))
    }

    @Test
    fun `text that does not match reads nothing`() {
        assertNull(parse("hola"))
        assertNull(parse(""))
    }

    @Test
    fun `an unknown unit reads nothing`() {
        assertNull(parse("ST,GS, 1.234 lb"))
    }

    @Test
    fun `a frame without a stability group leaves stable undecided`() {
        val bare = Regex("""(?<value>[0-9.]+)\s*(?<unit>kg)""")
        assertEquals(ParsedFrame(grams = 500, stable = null), ScaleFrameParser.parse("0.5 kg", bare, stable, units))
    }

    @Test
    fun `unit factors drop malformed entries`() {
        assertEquals(mapOf("kg" to 1000.0, "g" to 1.0), ScaleDefaults.parseUnitFactors("kg=1000, g=1, oops, lb=abc, x=0"))
    }
}
