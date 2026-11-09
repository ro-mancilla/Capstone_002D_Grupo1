package com.f776.locale

import kotlinx.datetime.format.DayOfWeekNames

val DayOfWeekNames.Companion.ENGLISH_NARROW: DayOfWeekNames
    get() =
        DayOfWeekNames(
            listOf("M", "T", "W", "T", "F", "S", "S"),
        )
