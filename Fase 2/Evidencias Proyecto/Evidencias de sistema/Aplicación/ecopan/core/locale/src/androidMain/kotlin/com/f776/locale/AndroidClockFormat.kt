package com.f776.locale

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat

/**
 * Reads Android's system-wide "Use 24-hour format" setting, which overrides the locale.
 *
 * The value is cached and invalidated by a [ContentObserver] rather than re-read on every call:
 * [DateFormat.is24HourFormat] goes through Settings, and this is consulted once per rendered time
 * -- several times per frame on a screen full of cards.
 */
internal class AndroidClockFormat(private val context: Context) : ClockFormat {

    @Volatile
    private var cached: Boolean = DateFormat.is24HourFormat(context)

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            cached = DateFormat.is24HourFormat(context)
        }
    }

    init {
        // Registered for the life of the process: this is a singleton in the DI graph, so there
        // is no narrower scope to unregister against.
        context.contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.TIME_12_24),
            /* notifyForDescendants = */ false,
            observer
        )
    }

    override fun uses24HourClock(): Boolean = cached
}
