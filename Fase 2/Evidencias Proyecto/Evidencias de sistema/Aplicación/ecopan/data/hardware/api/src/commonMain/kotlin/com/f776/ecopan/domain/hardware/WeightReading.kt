package com.f776.ecopan.domain.hardware

import kotlin.time.Instant

/**
 * Grams only — no floats in the ledger (01 §5); scale drivers report integer grams.
 *
 * [rawText] is the exact frame this reading was parsed from (`ST,GS, 0.842 kg`), `null` for drivers
 * with no wire text. It travels with the reading so the live test can show the value and the text
 * as one unit instead of two flows that drift apart.
 */
data class WeightReading(val grams: Long, val stable: Boolean, val at: Instant, val rawText: String? = null)
