package com.f776.ecopan.feature.pos.screens.pos

/** CLP-style formatting: no decimals, dot-grouped thousands, e.g. `$11.824`. */
internal fun formatMinor(amountMinor: Long): String {
    val digits = amountMinor.toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return "$$grouped"
}

/** `845` -> `"0,845 kg"` (comma decimal, matching the till's locale). */
internal fun formatGramsAsKg(grams: Long): String {
    val kg = grams / 1000
    val rem = (grams % 1000).toString().padStart(3, '0')
    return "$kg,$rem kg"
}

/**
 * Parses a cashier-typed kg string (comma or dot decimal, up to 3 decimals) to integer grams,
 * matching the same "no floats stored" rule as the hardware layer (01 §5). Returns null for
 * blank/malformed input rather than throwing — callers treat that as "not ready to add yet".
 */
internal fun parseKgTextToGrams(kgText: String): Long? {
    val normalized = kgText.trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    if (value <= 0) return null
    return (value * 1000).let { if (it - it.toLong() >= 0.5) it.toLong() + 1 else it.toLong() }
}

/** `845` -> `"0,845"`: the number alone, for a readout that prints the unit separately. */
internal fun formatGramsNumber(grams: Long): String = "${grams / 1000},${(grams % 1000).toString().padStart(3, '0')}"
