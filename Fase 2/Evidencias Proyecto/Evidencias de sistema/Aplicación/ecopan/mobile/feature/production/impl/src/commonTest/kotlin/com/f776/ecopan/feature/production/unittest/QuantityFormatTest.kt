package com.f776.ecopan.feature.production.unittest

import com.f776.ecopan.feature.production.screens.formatDayHeading
import com.f776.ecopan.feature.production.screens.formatStockKg
import com.f776.ecopan.feature.production.screens.formatSuggestedKg
import com.f776.ecopan.feature.production.screens.formatSyncMoment
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class QuantityFormatTest {

    @Test
    fun `suggested kilograms round to one decimal, half up, with a comma`() {
        assertEquals("22,7", formatSuggestedKg(22_650))
        assertEquals("18,3", formatSuggestedKg(18_300))
        assertEquals("6,5", formatSuggestedKg(6_549))
        assertEquals("0,1", formatSuggestedKg(50))
        assertEquals("0,0", formatSuggestedKg(49))
    }

    @Test
    fun `suggested kilograms keep their single decimal and group thousands with a dot`() {
        assertEquals("12,0", formatSuggestedKg(12_000))
        assertEquals("1.234,6", formatSuggestedKg(1_234_560))
    }

    @Test
    fun `balances always show three decimals so a list never mixes precisions`() {
        assertEquals("1,000", formatStockKg(1_000))
        assertEquals("0,995", formatStockKg(995))
        assertEquals("194,048", formatStockKg(194_048))
        assertEquals("4,940", formatStockKg(4_940))
        assertEquals("20,000", formatStockKg(20_000))
        assertEquals("0,000", formatStockKg(0))
        assertEquals("1.234,567", formatStockKg(1_234_567))
    }

    @Test
    fun `a negative balance uses a true minus sign and never shrinks the deficit`() {
        assertEquals("\u22120,200", formatStockKg(-200))
        assertEquals("\u221233,279", formatStockKg(-33_279))
    }

    @Test
    fun `suggested kilograms round half up rather than truncate`() {
        assertEquals("3,9", formatSuggestedKg(3_869))
        assertEquals("17,3", formatSuggestedKg(17_250))
    }

    @Test
    fun `the day heading names the weekday, the day and the abbreviated month`() {
        assertEquals("jueves 8 oct", formatDayHeading(LocalDate(2026, 10, 8)))
        assertEquals("domingo 3 ene", formatDayHeading(LocalDate(2027, 1, 3)))
    }

    private val local = TimeZone.of("-04:00") // fixed, so the test does not depend on DST rules
    private val now = Instant.parse("2026-10-07T18:00:00Z") // 14:00 on the 7th local time (UTC-4)

    @Test
    fun `a sync from earlier today reads as hoy with the time, or the bare time when asked`() {
        val at = Instant.parse("2026-10-07T08:50:00Z") // 04:50 local
        assertEquals("hoy 04:50", formatSyncMoment(at, now, local, todayWord = true))
        assertEquals("04:50", formatSyncMoment(at, now, local, todayWord = false))
    }

    @Test
    fun `yesterday and older syncs always name their day`() {
        assertEquals("ayer 21:05", formatSyncMoment(Instant.parse("2026-10-07T01:05:00Z"), now, local, todayWord = false))
        assertEquals("3 oct 09:00", formatSyncMoment(Instant.parse("2026-10-03T13:00:00Z"), now, local, todayWord = true))
    }

    @Test
    fun `the day is the viewer's local day, not UTC's`() {
        // 02:30Z on the 8th is still the evening of the 7th in UTC-4.
        val at = Instant.parse("2026-10-08T02:30:00Z")
        val lateNow = Instant.parse("2026-10-08T03:00:00Z")
        assertEquals("hoy 22:30", formatSyncMoment(at, lateNow, local, todayWord = true))
    }
}
