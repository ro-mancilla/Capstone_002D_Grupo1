package com.f776.ecopan.feature.production.screens

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/*
 * The baker's number formats (Panadero móvil spec, 2d). [formatKg] is the exact-gram weight (three
 * decimals) used for batches, deductions and stock; the suggested-production card reads a
 * forecast, so it alone rounds, to 0,1 kg.
 *
 * es-CL punctuation and day names live here as locale data for the same reason as in KgFormat.kt:
 * `kotlin.text` has no locale-aware formatter in common code.
 */

/**
 * A forecast in kilograms, rounded half up to 0,1 (spec: "kg a 0,1... no '12,50 kg'") and always
 * showing the tenth, so a column of quantities keeps the same width: `22,7`, `12,0`.
 */
internal fun formatSuggestedKg(grams: Long): String {
    val tenths = (grams.coerceAtLeast(0) + 50) / 100
    return groupThousands(tenths / 10) + "," + (tenths % 10)
}

/**
 * A stock balance in kilograms to the gram: always three decimals, same as [formatKg], so a list
 * never mixes `4,94` with `194,048` and `20`. A negative balance gets a true minus (U+2212), as
 * the design draws it.
 */
internal fun formatStockKg(grams: Long): String {
    val digits = formatKg(if (grams < 0) -grams else grams)
    return if (grams < 0) "−$digits" else digits
}

private val WeekdayNames = mapOf(
    DayOfWeek.MONDAY to "lunes",
    DayOfWeek.TUESDAY to "martes",
    DayOfWeek.WEDNESDAY to "miércoles",
    DayOfWeek.THURSDAY to "jueves",
    DayOfWeek.FRIDAY to "viernes",
    DayOfWeek.SATURDAY to "sábado",
    DayOfWeek.SUNDAY to "domingo",
)

private val MonthAbbreviations = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** `jueves 8 oct`: the heading names the day because "mañana" alone reads as "today" past midnight. */
internal fun formatDayHeading(date: LocalDate): String =
    "${WeekdayNames.getValue(date.dayOfWeek)} ${date.day} ${MonthAbbreviations[date.month.ordinal]}"

/**
 * When the device last heard from the server, as the offline lines print it. Today's time stands
 * alone (or as `hoy 04:50` when [todayWord]); anything older names its day, because `04:50` read
 * the next morning would look current.
 */
internal fun formatSyncMoment(at: Instant, now: Instant, zone: TimeZone, todayWord: Boolean): String {
    val local = at.toLocalDateTime(zone)
    val today = now.toLocalDateTime(zone).date
    val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
    return when (local.date) {
        today -> if (todayWord) "hoy $time" else time
        today.minus(1, DateTimeUnit.DAY) -> "ayer $time"
        else -> "${local.day} ${MonthAbbreviations[local.month.ordinal]} $time"
    }
}
