package com.f776.ecopan.domain.hardware

/**
 * Optional capability a [Scale] driver may additionally implement (04 §5 — `ManualScale`'s
 * "internal extension surface consumed by the POS weight-entry field"). POS discovers it with
 * `scale as? ManualWeightEntry` rather than depending on the concrete (internal) driver class,
 * keeping POS on `:data:hardware:api` only. Submitting a weight is expected to also emit a
 * [WeightReading] on the same driver's [Scale.readings], so callers observe the result through the
 * one existing channel instead of a second return path.
 */
interface ManualWeightEntry {
    fun submitWeight(grams: Long)
}
