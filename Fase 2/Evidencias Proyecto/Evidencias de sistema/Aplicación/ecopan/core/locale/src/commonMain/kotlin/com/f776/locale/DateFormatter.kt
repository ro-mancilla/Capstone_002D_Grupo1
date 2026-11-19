package com.f776.locale

import kotlinx.datetime.LocalDate

/**
 * Formats calendar dates for display using locale-aware month names.
 *
 * The project has no plural resources, so callers that need relative wording ("today",
 * "tomorrow") should special-case those and fall back to these absolute formats — that keeps
 * every language readable without introducing plural rules.
 */
object DateFormatter {

    /** e.g. "30 Jun" — for dates within the current year. */
    fun formatDayMonth(
        date: LocalDate,
        locale: PlatformLocale = getDefaultLocale()
    ): String = "${date.day} ${date.month.getDisplayName(TextStyle.SHORT, locale)}"

    /** e.g. "30 Jun 2027" — includes the year, for dates outside [referenceYear]. */
    fun formatDayMonthYear(
        date: LocalDate,
        locale: PlatformLocale = getDefaultLocale()
    ): String = "${formatDayMonth(date, locale)} ${date.year}"

    /** Picks the compact form when the date falls in [referenceYear], the full one otherwise. */
    fun formatRelativeToYear(
        date: LocalDate,
        referenceYear: Int,
        locale: PlatformLocale = getDefaultLocale()
    ): String = if (date.year == referenceYear) {
        formatDayMonth(date, locale)
    } else {
        formatDayMonthYear(date, locale)
    }

    /** e.g. "Tuesday, 30 Jun" — used where the weekday helps the user orient. */
    fun formatWithWeekday(
        date: LocalDate,
        locale: PlatformLocale = getDefaultLocale()
    ): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        return "$weekday, ${formatDayMonth(date, locale)}"
    }
}
