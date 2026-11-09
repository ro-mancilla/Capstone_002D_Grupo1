package com.f776.locale

/**
 * Whether times should be written on a 24-hour clock for the current user.
 *
 * This is the locale's convention on every platform, except that Android lets the user force a
 * 24-hour clock system-wide regardless of locale -- and that setting is only reachable through a
 * `Context`. Injecting the lookup keeps the `Context` on the Android side of an `expect`/`actual`
 * boundary instead of leaking it into shared code, and lets anything that formats a time ask for
 * the answer, composable or not.
 */
interface ClockFormat {
    fun uses24HourClock(): Boolean
}

/**
 * The answer the locale alone gives, for platforms with no system-wide override and as the
 * fallback for anything constructed outside the DI graph.
 */
class LocaleClockFormat : ClockFormat {
    override fun uses24HourClock(): Boolean = localeUses24HourClock()
}
