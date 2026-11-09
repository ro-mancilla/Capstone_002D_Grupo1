package com.f776.locale

import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle

/**
 * Derives the convention from the locale's own short time pattern: a pattern containing a
 * half-day hour field (`h`/`K`) or a period marker (`a`/`B`) is a 12-hour one. Reading the
 * pattern is more reliable than maintaining a locale list, and it stays correct as the JDK's
 * CLDR data is updated.
 */
actual fun localeUses24HourClock(locale: PlatformLocale): Boolean {
    val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        /* dateStyle = */ null,
        /* timeStyle = */ FormatStyle.SHORT,
        IsoChronology.INSTANCE,
        locale
    )
    return !pattern.containsHalfDayField()
}

internal actual fun formatTimeOfDay(
    time: LocalTime,
    locale: PlatformLocale,
    use24Hour: Boolean
): String = time.toJavaLocalTime().format(
    DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
)

internal actual fun formatHourOfDay(
    time: LocalTime,
    locale: PlatformLocale,
    use24Hour: Boolean
): String = time.toJavaLocalTime().format(
    DateTimeFormatter.ofPattern(if (use24Hour) "HH" else "h a", locale)
)

/**
 * Quoted literals in a pattern can contain any character, so they are skipped before looking for
 * the pattern letters — a locale whose separator is the quoted text `'h'` must not be misread as
 * a 12-hour locale.
 */
private fun String.containsHalfDayField(): Boolean {
    var insideLiteral = false
    for (character in this) {
        when {
            character == '\'' -> insideLiteral = !insideLiteral
            insideLiteral -> Unit
            character in HALF_DAY_PATTERN_LETTERS -> return true
        }
    }
    return false
}

/** `h`/`K` are the half-day hour fields; `a`/`B` are the AM/PM and day-period markers. */
private const val HALF_DAY_PATTERN_LETTERS = "hKaB"
