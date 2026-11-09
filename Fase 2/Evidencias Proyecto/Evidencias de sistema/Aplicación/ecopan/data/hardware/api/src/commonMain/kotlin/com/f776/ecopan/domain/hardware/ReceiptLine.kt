package com.f776.ecopan.domain.hardware

/**
 * One printed line. [quantityLabel] is already formatted by the caller (e.g. "0,842 kg" or "3")
 * — the hardware layer never re-derives display formatting from raw grams/quantities. [perKg]
 * says the unit price is per kilogram, so the printed detail reads `@ $3.200/kg` rather than
 * `@ $1.800`.
 */
data class ReceiptLine(
    val name: String,
    val quantityLabel: String,
    val unitPriceMinor: Long,
    val lineTotalMinor: Long,
    val perKg: Boolean = false,
)
