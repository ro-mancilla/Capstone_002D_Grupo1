package com.f776.ecopan.core.domain.money

/** Money as integer minor units — never a float (01 §5). [currencyCode] is an ISO 4217 code. */
data class Money(
    val amountMinor: Long,
    val currencyCode: String,
) {
    operator fun plus(other: Money): Money {
        require(currencyCode == other.currencyCode) {
            "Cannot add $currencyCode to ${other.currencyCode}"
        }
        return Money(amountMinor + other.amountMinor, currencyCode)
    }
}

/**
 * Weight-based sale line total: `weightGrams * pricePerKgMinor / 1000`, half-up rounded and
 * computed once at sale time (01 §5 — the result is stored and authoritative; never recomputed).
 */
fun lineTotalForWeight(weightGrams: Long, pricePerKgMinor: Long): Long =
    roundHalfUp(weightGrams * pricePerKgMinor, 1000L)

/** Unit-based sale line total: exact, no rounding needed. */
fun lineTotalForUnits(quantity: Long, priceMinor: Long): Long =
    quantity * priceMinor

/**
 * Half-up rounding of `numerator / denominator` using only integer arithmetic (both non-negative).
 * `denominator / 2` must be exact — only ever called here with 1000.
 */
internal fun roundHalfUp(numerator: Long, denominator: Long): Long {
    require(numerator >= 0) { "numerator must be non-negative, was $numerator" }
    require(denominator > 0 && denominator % 2 == 0L) { "denominator must be positive and even, was $denominator" }
    return (numerator + denominator / 2) / denominator
}
