package com.f776.ecopan.feature.production.screens

/**
 * Kilograms to the gram: always three decimals (`4,940`, `20,000`, `3,869`). Weights are whole
 * grams (01 §5), so three decimals is exact -- nothing is rounded or truncated, which is what lets
 * a batch's deduction preview add up to the batch weight and match the ledger the server writes.
 * One precision for every weight a baker reads (batch totals, deductions, stock); only forecasts
 * are estimates and read at 0,1 kg ([formatSuggestedKg]).
 *
 * es-CL punctuation: comma decimal, dot thousands. `kotlin.text` has no locale-aware number
 * formatter in common code, and pulling one in for two separators would be the larger dependency.
 * A negative value keeps its sign outside the digits.
 */
internal fun formatKg(grams: Long): String {
    val negative = grams < 0
    val abs = if (negative) -grams else grams
    val sign = if (negative) "-" else ""
    return sign + groupThousands(abs / 1000) + "," + (abs % 1000).toString().padStart(3, '0')
}

/**
 * Sub-kilogram quantities read in whole grams, the rest in exact kilograms. A pinch of yeast is
 * "37 g", never "0,037 kg" -- the ingredient list under the keypad is mostly this end of the
 * scale. Both forms are exact, so a list of these sums to its batch weight.
 */
internal fun formatAmount(grams: Long): String {
    val abs = if (grams < 0) -grams else grams
    return if (abs < 1000) "$grams g" else "${formatKg(grams)} kg"
}

internal fun groupThousands(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    return buildString(digits.length + digits.length / 3) {
        digits.forEachIndexed { index, char ->
            if (index > 0 && (digits.length - index) % 3 == 0) append('.')
            append(char)
        }
    }
}
