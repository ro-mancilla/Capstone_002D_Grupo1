package com.f776.ecopan.core.domain.formula

/**
 * Finished units a mix of [mixGrams] is expected to yield, from the product's
 * [mixToLoafRatioMilli] (finished units per kg of mix, ×1000; null = no estimate configured).
 *
 * `mixGrams * ratio / 1_000_000`, **rounded to nearest** per plan 06 §2. The rounding is the
 * whole point of putting this in one place: the keypad preview rounded while the logged row and
 * the pull applier truncated, so a 12.5 kg mix at ratio 2200 showed the baker 28 and stored 27.
 *
 * Informational only — displayed and snapshotted on the batch row for history, never ledgered
 * (plan 06 §2: finished goods are approximate and unreconciled). Sits beside
 * [deriveIngredientGrams], the other rule every client must agree on exactly.
 */
fun estimateLoaves(mixGrams: Long, mixToLoafRatioMilli: Int?): Int? {
    require(mixGrams >= 0) { "mixGrams must be non-negative, was $mixGrams" }
    val ratio = mixToLoafRatioMilli ?: return null
    require(ratio > 0) { "mixToLoafRatioMilli must be positive, was $ratio" }

    return ((mixGrams * ratio + 500_000L) / 1_000_000L).toInt()
}
