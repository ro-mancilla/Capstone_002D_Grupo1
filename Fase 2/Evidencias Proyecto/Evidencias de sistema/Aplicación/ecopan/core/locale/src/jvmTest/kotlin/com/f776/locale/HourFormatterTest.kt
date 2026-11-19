package com.f776.locale

import kotlinx.datetime.LocalTime
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HourFormatterTest {

    private val afternoon = LocalTime(16, 10)
    private val midnight = LocalTime(0, 5)
    private val noon = LocalTime(12, 0)

    @Test
    fun `detects the clock convention from the locale`() {
        assertTrue(localeUses24HourClock(Locale.forLanguageTag("es-ES")))
        assertTrue(localeUses24HourClock(Locale.forLanguageTag("ja-JP")))
        assertFalse(localeUses24HourClock(Locale.forLanguageTag("en-US")))
    }

    @Test
    fun `a twelve-hour locale gets an afternoon time as PM, not as 16`() {
        // The whole point of the change: this used to render "16:10" for every user on earth.
        val formatted = HourFormatter.format(afternoon, Locale.forLanguageTag("en-US"))

        assertTrue(formatted.startsWith("4:10"), "expected a half-day hour, got $formatted")
        assertTrue(formatted.contains("PM", ignoreCase = true), "expected a marker, got $formatted")
    }

    @Test
    fun `a twenty-four hour locale keeps the zero-padded hour`() {
        assertEquals("16:10", HourFormatter.format(afternoon, Locale.forLanguageTag("es-ES")))
        assertEquals("00:05", HourFormatter.format(midnight, Locale.forLanguageTag("es-ES")))
    }

    @Test
    fun `midnight and noon do not become hour zero on a twelve-hour clock`() {
        val locale = Locale.forLanguageTag("en-US")

        assertTrue(HourFormatter.format(midnight, locale).startsWith("12:05"))
        assertTrue(HourFormatter.format(noon, locale).startsWith("12:00"))
    }

    @Test
    fun `the period marker is localized, not a hardcoded AM or PM`() {
        // kotlinx-datetime's format builders can only emit literal markers, which is why the
        // rendering is delegated to the platform: Japanese writes the period before the hour and
        // does not use the letters "PM" at all.
        val japanese = HourFormatter.format(afternoon, Locale.JAPAN, use24Hour = false)

        assertTrue(japanese.contains("午後"), "expected a localized marker, got $japanese")
    }

    @Test
    fun `a range renders both ends in the same convention`() {
        val formatted = HourFormatter.formatRange(
            LocalTime(9, 0),
            afternoon,
            Locale.forLanguageTag("es-ES")
        )

        assertEquals("09:00-16:10", formatted)
    }

    @Test
    fun `an explicit override beats the locale convention`() {
        // Android lets the user force a 24-hour clock regardless of locale; that override arrives
        // as this parameter, so it has to win.
        assertEquals(
            "16:10",
            HourFormatter.format(afternoon, Locale.forLanguageTag("en-US"), use24Hour = true)
        )
    }

    @Test
    fun `the hour-only form drops the minutes`() {
        assertEquals("16", HourFormatter.formatHour(afternoon, Locale.forLanguageTag("es-ES")))

        val english = HourFormatter.formatHour(afternoon, Locale.forLanguageTag("en-US"))
        assertTrue(english.startsWith("4"), "expected a half-day hour, got $english")
        assertFalse(english.contains(":"), "expected no minutes, got $english")
    }
}
