package com.f776.ecopan.domain.hardware

import kotlin.math.round

/**
 * Defaults of the generic text protocol as plain values, for code that needs them as numbers (the
 * driver) rather than as the strings [SerialScaleSettings] edits. [SerialScaleSettings] is the
 * single source: nothing here repeats a literal.
 */
object ScaleDefaults {
    const val DELIMITER = "\r\n"
    const val STABLE_VALUES = SerialScaleSettings.DEFAULT_STABLE_VALUES
    const val UNIT_FACTORS = SerialScaleSettings.DEFAULT_UNIT_FACTORS
    val STABILITY_COUNT: Int = SerialScaleSettings.DEFAULT_STABILITY_COUNT.toInt()
    val STABILITY_TOLERANCE_GRAMS: Long = SerialScaleSettings.DEFAULT_STABILITY_TOLERANCE_GRAMS.toLong()
    val STABILITY_WINDOW_MS: Long = SerialScaleSettings.DEFAULT_STABILITY_WINDOW_MS.toLong()

    /** Matches frames like `ST,GS, 1.234 kg` / `US,GS,+000.842kg`. */
    const val FRAME_REGEX = SerialScaleSettings.DEFAULT_FRAME_REGEX

    /** `kg=1000, g=1` -> {kg: 1000.0, g: 1.0}; entries that are not `unit=factor` are dropped. */
    fun parseUnitFactors(raw: String): Map<String, Double> =
        raw.split(",").mapNotNull { entry ->
            val parts = entry.split("=")
            if (parts.size != 2) return@mapNotNull null
            val unit = parts[0].trim()
            val factor = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
            if (unit.isEmpty() || factor <= 0.0) null else unit to factor
        }.toMap()

    fun parseStableValues(raw: String): Set<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

/** One frame read: integer [grams], and [stable] when the frame carries its own flag (`null` = it does not). */
data class ParsedFrame(val grams: Long, val stable: Boolean?)

/**
 * Reads one text frame with a configured regex. It is the single parser behind both the serial
 * driver and the settings screen's text probe, so "paste a line and see what is read" is the truth
 * and not an approximation. Required named groups: `value` and `unit`; optional: `stable`.
 *
 * The regex is user-typed, so it runs under [findWithinBudget]: a pathological pattern costs one
 * unread frame, never a stuck reader thread.
 */
object ScaleFrameParser {

    fun parse(
        frame: String,
        regex: Regex,
        stableValues: Set<String>,
        unitFactors: Map<String, Double>,
    ): ParsedFrame? {
        val text = frame.trim()
        if (text.isEmpty()) return null
        val match = try {
            regex.findWithinBudget(text)
        } catch (_: RegexBudgetExceeded) {
            null
        } ?: return null
        val value = match.group("value")?.toDoubleOrNull() ?: return null
        val unit = match.group("unit") ?: return null
        val factor = unitFactors[unit] ?: return null
        val stableGroup = match.group("stable")
        return ParsedFrame(
            grams = round(value * factor).toLong(),
            stable = stableGroup?.let { it in stableValues },
        )
    }

    /** `groups[name]` throws (rather than returning null) when the regex declares no such group; the stability group is optional. */
    private fun MatchResult.group(name: String): String? = runCatching { groups[name]?.value }.getOrNull()
}
