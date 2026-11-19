package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.domain.hardware.RegexBudgetExceeded
import com.f776.ecopan.domain.hardware.ScaleProtocolKind
import com.f776.ecopan.domain.hardware.findWithinBudget
import com.f776.ecopan.domain.hardware.SerialFraming
import com.f776.ecopan.domain.hardware.SerialScaleSettings
import com.f776.ecopan.domain.hardware.SerialScaleSettings.Field
import com.f776.ecopan.domain.hardware.SerialScaleSettings.Problem
import com.f776.ecopan.domain.hardware.unescapeControlChars
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

internal class SerialScaleSettingsTest {

    private val valid = SerialScaleSettings(port = "ttyUSB0")

    @Test
    fun `defaults are valid once a port is chosen`() {
        assertEquals(emptyMap(), valid.validate())
    }

    @Test
    fun `a missing port is flagged`() {
        assertEquals(mapOf(Field.PORT to Problem.REQUIRED), SerialScaleSettings().validate())
    }

    @Test
    fun `config round-trips every field`() {
        val settings = SerialScaleSettings(
            port = "COM3",
            baud = 4800,
            framing = SerialFraming.SEVEN_E_ONE,
            protocol = ScaleProtocolKind.POLL_GENERIC,
            delimiter = "\\r",
            frameRegex = "^(?<value>[0-9.]+)(?<unit>g)$",
            stableValues = "S,ST",
            unitFactors = "g=1",
            pollCommand = "\\x05",
            pollIntervalMs = "250",
            stabilityCount = "5",
            stabilityToleranceGrams = "1",
            stabilityWindowMs = "800",
        )

        assertEquals(settings, SerialScaleSettings.fromConfig(settings.toConfig()))
    }

    @Test
    fun `fromConfig falls back to defaults for a missing or unknown value`() {
        val settings = SerialScaleSettings.fromConfig(mapOf("baud" to "fast", "framing" to "9Z9", "protocol" to "weird"))

        assertEquals(SerialScaleSettings(), settings)
    }

    @Test
    fun `an unparseable regex is rejected`() {
        assertEquals(mapOf(Field.FRAME_REGEX to Problem.INVALID_REGEX), valid.copy(frameRegex = "([").validate())
    }

    @Test
    fun `a regex without value and unit groups is rejected`() {
        assertEquals(mapOf(Field.FRAME_REGEX to Problem.MISSING_GROUPS), valid.copy(frameRegex = "^[0-9]+$").validate())
    }

    @Test
    fun `unit factors must be unit=positive-number pairs`() {
        assertEquals(mapOf(Field.UNIT_FACTORS to Problem.INVALID_UNIT_FACTORS), valid.copy(unitFactors = "kg:1000").validate())
        assertEquals(mapOf(Field.UNIT_FACTORS to Problem.INVALID_UNIT_FACTORS), valid.copy(unitFactors = "kg=0").validate())
        assertEquals(mapOf("kg" to 1000.0, "g" to 1.0), SerialScaleSettings.parseUnitFactors("kg=1000, g=1"))
        assertNull(SerialScaleSettings.parseUnitFactors(""))
    }

    @Test
    fun `numeric advanced fields must be positive integers`() {
        val problems = valid.copy(stabilityCount = "x", stabilityToleranceGrams = "-1", stabilityWindowMs = "").validate()

        assertEquals(
            mapOf(
                Field.STABILITY_COUNT to Problem.INVALID_NUMBER,
                Field.STABILITY_TOLERANCE to Problem.INVALID_NUMBER,
                Field.STABILITY_WINDOW to Problem.INVALID_NUMBER,
            ),
            problems,
        )
    }

    @Test
    fun `poll fields are only checked for the poll protocol`() {
        val broken = valid.copy(pollCommand = "", pollIntervalMs = "x")

        assertEquals(emptyMap(), broken.validate())
        assertEquals(
            mapOf(Field.POLL_COMMAND to Problem.REQUIRED, Field.POLL_INTERVAL to Problem.INVALID_NUMBER),
            broken.copy(protocol = ScaleProtocolKind.POLL_GENERIC).validate(),
        )
    }

    @Test
    fun `a blank delimiter is rejected`() {
        assertEquals(mapOf(Field.DELIMITER to Problem.REQUIRED), valid.copy(delimiter = "").validate())
    }

    @Test
    fun `a slow catastrophic regex is rejected up front`() {
        val problems = valid.copy(frameRegex = "^(?<value>(\\2x|x)+y)(?<unit>z)").validate()

        assertEquals(mapOf(Field.FRAME_REGEX to Problem.REGEX_TOO_SLOW), problems)
    }

    @Test
    fun `value and unit must be real named groups, not just text in the pattern`() {
        val escaped = valid.copy(frameRegex = """^\(\?<value>x\)\(\?<unit>y\)$""")

        assertEquals(mapOf(Field.FRAME_REGEX to Problem.MISSING_GROUPS), escaped.validate())
    }

    @Test
    fun `stable values are required when the regex captures a stable flag`() {
        assertEquals(mapOf(Field.STABLE_VALUES to Problem.REQUIRED), valid.copy(stableValues = " ").validate())
        val noFlag = valid.copy(frameRegex = "^(?<value>[0-9.]+)(?<unit>g)$", stableValues = "")

        assertEquals(emptyMap(), noFlag.validate())
    }

    @Test
    fun `baud, poll interval and stability fields are range-checked`() {
        val poll = valid.copy(protocol = ScaleProtocolKind.POLL_GENERIC)

        assertEquals(mapOf(Field.BAUD to Problem.INVALID_NUMBER), valid.copy(baud = 0).validate())
        assertEquals(mapOf(Field.POLL_INTERVAL to Problem.INVALID_NUMBER), poll.copy(pollIntervalMs = "1").validate())
        assertEquals(mapOf(Field.POLL_INTERVAL to Problem.INVALID_NUMBER), poll.copy(pollIntervalMs = "9999999").validate())
        assertEquals(mapOf(Field.STABILITY_COUNT to Problem.INVALID_NUMBER), valid.copy(stabilityCount = "1000000").validate())
        assertEquals(mapOf(Field.STABILITY_WINDOW to Problem.INVALID_NUMBER), valid.copy(stabilityWindowMs = "999999999").validate())
    }

    @Test
    fun `hex escapes need exactly two hex digits`() {
        assertEquals("""\x+1""", unescapeControlChars("""\x+1"""))
        assertEquals("""\x5""", unescapeControlChars("""\x5"""))
        assertEquals("""\xZZ""", unescapeControlChars("""\xZZ"""))
        assertEquals("ÿ", unescapeControlChars("""\xFF"""))
    }

    @Test
    fun `the regex budget guard times out a catastrophic pattern`() {
        val result = runCatching { Regex("^(\\1x|x)+y").findWithinBudget("x".repeat(64), budgetMs = 50) }

        assertEquals(true, result.exceptionOrNull() is RegexBudgetExceeded)
    }

    @Test
    fun `control characters are unescaped`() {
        assertEquals("\r\n", unescapeControlChars("\\r\\n"))
        assertEquals("\r\n", unescapeControlChars("\r\n"))
        assertEquals("W\r", unescapeControlChars("W\\r"))
        assertEquals("\u0005", unescapeControlChars("\\x05"))
        assertEquals("a\\b", unescapeControlChars("a\\\\b"))
    }
}
