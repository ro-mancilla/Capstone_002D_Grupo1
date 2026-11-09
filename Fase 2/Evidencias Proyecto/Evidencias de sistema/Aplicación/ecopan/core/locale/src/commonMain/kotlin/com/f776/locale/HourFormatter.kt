package com.f776.locale

import kotlinx.datetime.LocalTime

/**
 * Formats times of day for display.
 *
 * Whether a time reads "16:10" or "4:10 PM" is a property of the user's locale, not of the app,
 * and the AM/PM marker itself is localized ("p. m." in Spanish, "午後" in Japanese). Neither can
 * be expressed with kotlinx-datetime's literal-string format builders, so the actual rendering is
 * delegated to the platform formatter via [formatTimeOfDay].
 *
 * [use24Hour] defaults to the locale's own convention. Anything with access to the DI graph
 * should pass [ClockFormat.uses24HourClock] instead: on Android the user can force a 24-hour
 * clock system-wide, and that override is only visible through a `Context`.
 */
object HourFormatter {

    /** e.g. "16:10" or "4:10 PM". */
    fun format(
        time: LocalTime,
        locale: PlatformLocale = getDefaultLocale(),
        use24Hour: Boolean = localeUses24HourClock(locale)
    ): String = formatTimeOfDay(time, locale, use24Hour)

    /** e.g. "16:10-17:40" or "4:10 PM-5:40 PM". */
    fun formatRange(
        start: LocalTime,
        end: LocalTime,
        locale: PlatformLocale = getDefaultLocale(),
        use24Hour: Boolean = localeUses24HourClock(locale)
    ): String = "${format(start, locale, use24Hour)}-${format(end, locale, use24Hour)}"

    /**
     * Hour only, e.g. "16" or "4 PM" — for the schedule graph's time axis, where minutes are
     * always zero and the label has to stay narrow.
     */
    fun formatHour(
        time: LocalTime,
        locale: PlatformLocale = getDefaultLocale(),
        use24Hour: Boolean = localeUses24HourClock(locale)
    ): String = formatHourOfDay(time, locale, use24Hour)
}

/** True when [locale] conventionally writes times on a 24-hour clock. */
expect fun localeUses24HourClock(locale: PlatformLocale = getDefaultLocale()): Boolean

internal expect fun formatTimeOfDay(
    time: LocalTime,
    locale: PlatformLocale,
    use24Hour: Boolean
): String

internal expect fun formatHourOfDay(
    time: LocalTime,
    locale: PlatformLocale,
    use24Hour: Boolean
): String
