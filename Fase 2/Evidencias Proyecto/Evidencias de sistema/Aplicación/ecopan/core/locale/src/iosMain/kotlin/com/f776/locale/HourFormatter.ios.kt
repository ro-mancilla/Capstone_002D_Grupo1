package com.f776.locale

import kotlinx.datetime.LocalTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter

/**
 * Asks Foundation for the locale's own hour skeleton: `j` resolves to whichever hour field the
 * locale prefers, so a resulting pattern carrying an AM/PM marker means a 12-hour locale. On iOS
 * this also picks up the user's "24-Hour Time" setting, because that setting is applied to the
 * current locale itself.
 */
actual fun localeUses24HourClock(locale: PlatformLocale): Boolean {
    val pattern = NSDateFormatter.dateFormatFromTemplate("j", 0u, locale)
    return pattern?.contains("a") != true
}

internal actual fun formatTimeOfDay(
    time: LocalTime,
    locale: PlatformLocale,
    use24Hour: Boolean
): String = time.format(locale, if (use24Hour) "HH:mm" else "h:mm a")

internal actual fun formatHourOfDay(
    time: LocalTime,
    locale: PlatformLocale,
    use24Hour: Boolean
): String = time.format(locale, if (use24Hour) "HH" else "h a")

private fun LocalTime.format(locale: PlatformLocale, pattern: String): String {
    val date = toNSDate() ?: return fallback(pattern)
    val formatter = NSDateFormatter().apply {
        setLocale(locale)
        setDateFormat(pattern)
    }
    return formatter.stringFromDate(date)
}

/**
 * The date part is arbitrary and never rendered — only the time fields are in the pattern — but
 * Foundation formats an [NSDate], so one has to exist.
 */
private fun LocalTime.toNSDate(): NSDate? {
    val components = NSDateComponents().apply {
        year = REFERENCE_YEAR
        month = 1
        day = 1
        hour = this@toNSDate.hour.toLong()
        minute = this@toNSDate.minute.toLong()
        second = 0
    }
    return NSCalendar.currentCalendar.dateFromComponents(components)
}

/** Only reachable if Foundation cannot build a date from a fixed, valid set of components. */
private fun LocalTime.fallback(pattern: String): String {
    val paddedMinute = minute.toString().padStart(2, '0')
    return if (pattern.startsWith("HH")) {
        "${hour.toString().padStart(2, '0')}:$paddedMinute"
    } else {
        val half = if (hour % 12 == 0) 12 else hour % 12
        "$half:$paddedMinute ${if (hour < 12) "AM" else "PM"}"
    }
}

private const val REFERENCE_YEAR = 2000L
