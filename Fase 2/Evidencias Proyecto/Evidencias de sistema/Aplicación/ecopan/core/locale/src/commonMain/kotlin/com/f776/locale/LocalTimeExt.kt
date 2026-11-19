package com.f776.locale

import kotlinx.datetime.LocalTime

val LocalTime.Companion.MIN: LocalTime
    get() = LocalTime(0, 0)

val LocalTime.Companion.MAX: LocalTime
    get() = LocalTime(23, 59)


val LocalTime.MIDNIGHT: LocalTime
    get() = LocalTime(0, 0)