package com.f776.ecopan.data

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A settable clock for repositories that stamp server receipt time. */
class TestClock(var current: Instant = Clock.System.now()) : Clock {
    override fun now(): Instant = current

    fun advance(by: Duration) {
        current += by
    }
}
