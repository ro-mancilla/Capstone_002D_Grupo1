package com.f776.ecopan.domain.hardware

/** Wire framing of the serial line: data bits, parity, stop bits. [label] is the conventional "8N1" shorthand. */
enum class SerialFraming(val label: String, val dataBits: Int, val parity: Parity, val stopBits: Int) {
    EIGHT_N_ONE("8N1", 8, Parity.NONE, 1),
    SEVEN_E_ONE("7E1", 7, Parity.EVEN, 1),
    EIGHT_E_ONE("8E1", 8, Parity.EVEN, 1),
    SEVEN_O_ONE("7O1", 7, Parity.ODD, 1),
    EIGHT_N_TWO("8N2", 8, Parity.NONE, 2),
    ;

    enum class Parity { NONE, ODD, EVEN }

    companion object {
        fun fromLabel(label: String?): SerialFraming? = entries.firstOrNull { it.label == label }
    }
}

/** The frame parsers `SerialScale` can run (04 §3). [id] is what is persisted in `hw.scale.serial.protocol`. */
enum class ScaleProtocolKind(val id: String) {
    CONTINUOUS_GENERIC("continuous-generic"),
    POLL_GENERIC("poll-generic"),
    ;

    companion object {
        fun fromId(id: String?): ScaleProtocolKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Everything the "serial" scale driver needs, as the user edits it in Settings -> Dispositivos.
 *
 * Text-valued on purpose: the Avanzado fields are free text the cashier is typing, and a typed
 * "abc" in a numeric box has to be representable so [validate] can reject it inline instead of the
 * UI silently coercing it. [delimiter] and [pollCommand] hold the *escaped* form (`\r\n`, `\x05`),
 * since a raw CR/LF cannot be typed into a text field; the driver runs them through
 * [unescapeControlChars].
 *
 * Persisted via [toConfig] (the [DriverChoice.config] map), which `DriverRegistry` stores under
 * `hw.scale.serial.<key>`. Defaults mirror the plan-04 worked example (`ST,GS, 1.234 kg\r\n`).
 */
data class SerialScaleSettings(
    val port: String = "",
    val baud: Int = DEFAULT_BAUD,
    val framing: SerialFraming = SerialFraming.EIGHT_N_ONE,
    val protocol: ScaleProtocolKind = ScaleProtocolKind.CONTINUOUS_GENERIC,
    val delimiter: String = DEFAULT_DELIMITER,
    val frameRegex: String = DEFAULT_FRAME_REGEX,
    val stableValues: String = DEFAULT_STABLE_VALUES,
    val unitFactors: String = DEFAULT_UNIT_FACTORS,
    val pollCommand: String = DEFAULT_POLL_COMMAND,
    val pollIntervalMs: String = DEFAULT_POLL_INTERVAL_MS,
    val stabilityCount: String = DEFAULT_STABILITY_COUNT,
    val stabilityToleranceGrams: String = DEFAULT_STABILITY_TOLERANCE_GRAMS,
    val stabilityWindowMs: String = DEFAULT_STABILITY_WINDOW_MS,
) {
    enum class Field {
        PORT, BAUD, DELIMITER, FRAME_REGEX, STABLE_VALUES, UNIT_FACTORS, POLL_COMMAND, POLL_INTERVAL,
        STABILITY_COUNT, STABILITY_TOLERANCE, STABILITY_WINDOW,
    }

    enum class Problem { REQUIRED, INVALID_REGEX, MISSING_GROUPS, REGEX_TOO_SLOW, INVALID_NUMBER, INVALID_UNIT_FACTORS }

    /** Empty map = safe to apply. Checked by the UI on every edit so a bad value is shown beside its field. */
    fun validate(): Map<Field, Problem> = buildMap {
        if (port.isBlank()) put(Field.PORT, Problem.REQUIRED)
        if (baud !in BAUD_RANGE) put(Field.BAUD, Problem.INVALID_NUMBER)
        if (delimiter.isEmpty()) put(Field.DELIMITER, Problem.REQUIRED)
        val regex = validateRegex()
        regex.problem?.let { put(Field.FRAME_REGEX, it) }
        if (regex.capturesStable && stableValues.isBlank()) put(Field.STABLE_VALUES, Problem.REQUIRED)
        if (parseUnitFactors(unitFactors) == null) put(Field.UNIT_FACTORS, Problem.INVALID_UNIT_FACTORS)
        if (!stabilityCount.isIntIn(STABILITY_COUNT_RANGE)) put(Field.STABILITY_COUNT, Problem.INVALID_NUMBER)
        if (!stabilityToleranceGrams.isIntIn(STABILITY_TOLERANCE_RANGE)) put(Field.STABILITY_TOLERANCE, Problem.INVALID_NUMBER)
        if (!stabilityWindowMs.isIntIn(STABILITY_WINDOW_RANGE)) put(Field.STABILITY_WINDOW, Problem.INVALID_NUMBER)
        if (protocol == ScaleProtocolKind.POLL_GENERIC) {
            if (pollCommand.isEmpty()) put(Field.POLL_COMMAND, Problem.REQUIRED)
            if (!pollIntervalMs.isIntIn(POLL_INTERVAL_RANGE)) put(Field.POLL_INTERVAL, Problem.INVALID_NUMBER)
        }
    }

    private class RegexCheck(val problem: Problem?, val capturesStable: Boolean)

    private fun validateRegex(): RegexCheck {
        val compiled = runCatching { Regex(frameRegex) }.getOrNull() ?: return RegexCheck(Problem.INVALID_REGEX, false)
        // `(?:pattern)|` always matches the empty string, so the match result exists even for a
        // pattern that would not match "", and asking it for a group the pattern never declared
        // fails -- which tells declared groups apart from the same text inside an escape or class.
        val probe = Regex("(?:$frameRegex)|").find("")
        fun declares(name: String) = runCatching { probe?.groups?.get(name) }.isSuccess
        if (!declares("value") || !declares("unit")) return RegexCheck(Problem.MISSING_GROUPS, false)
        // A pathological pattern is rejected here, with a hostile-looking sample, rather than
        // discovered on the serial thread at the first real frame.
        val slow = SAMPLE_FRAMES.any { sample ->
            try {
                compiled.findWithinBudget(sample)
                false
            } catch (_: RegexBudgetExceeded) {
                true
            }
        }
        return RegexCheck(if (slow) Problem.REGEX_TOO_SLOW else null, declares("stable"))
    }

    fun toConfig(): Map<String, String> = mapOf(
        KEY_PORT to port,
        KEY_BAUD to baud.toString(),
        KEY_FRAMING to framing.label,
        KEY_PROTOCOL to protocol.id,
        KEY_DELIMITER to delimiter,
        KEY_FRAME_REGEX to frameRegex,
        KEY_STABLE_VALUES to stableValues,
        KEY_UNIT_FACTORS to unitFactors,
        KEY_POLL_COMMAND to pollCommand,
        KEY_POLL_INTERVAL_MS to pollIntervalMs,
        KEY_STABILITY_COUNT to stabilityCount,
        KEY_STABILITY_TOLERANCE_GRAMS to stabilityToleranceGrams,
        KEY_STABILITY_WINDOW_MS to stabilityWindowMs,
    )

    companion object {
        const val KEY_PORT = "port"
        const val KEY_BAUD = "baud"
        const val KEY_FRAMING = "framing"
        const val KEY_PROTOCOL = "protocol"
        const val KEY_DELIMITER = "delimiter"
        const val KEY_FRAME_REGEX = "frameRegex"
        const val KEY_STABLE_VALUES = "stableValues"
        const val KEY_UNIT_FACTORS = "unitFactors"
        const val KEY_POLL_COMMAND = "pollCommand"
        const val KEY_POLL_INTERVAL_MS = "pollIntervalMs"
        const val KEY_STABILITY_COUNT = "stabilityCount"
        const val KEY_STABILITY_TOLERANCE_GRAMS = "stabilityToleranceGrams"
        const val KEY_STABILITY_WINDOW_MS = "stabilityWindowMs"

        /** Every key [toConfig] emits; `DriverRegistry` persists exactly these under `hw.scale.serial.*`. */
        val CONFIG_KEYS: List<String> = listOf(
            KEY_PORT, KEY_BAUD, KEY_FRAMING, KEY_PROTOCOL, KEY_DELIMITER, KEY_FRAME_REGEX, KEY_STABLE_VALUES,
            KEY_UNIT_FACTORS, KEY_POLL_COMMAND, KEY_POLL_INTERVAL_MS, KEY_STABILITY_COUNT,
            KEY_STABILITY_TOLERANCE_GRAMS, KEY_STABILITY_WINDOW_MS,
        )

        const val DEFAULT_BAUD = 9600
        val BAUD_RANGE = 50..4_000_000
        val STABILITY_COUNT_RANGE = 1..50
        val STABILITY_TOLERANCE_RANGE = 0..1_000
        val STABILITY_WINDOW_RANGE = 10..10_000
        val POLL_INTERVAL_RANGE = 50..60_000

        /** Realistic and adversarial frames the regex must survive within its time budget. */
        private val SAMPLE_FRAMES = listOf("ST,GS, 1.234 kg", "a".repeat(48) + "!", "x".repeat(64), " ".repeat(48) + "!", "9".repeat(48) + "!")
        val COMMON_BAUDS: List<Int> = listOf(1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200)
        const val DEFAULT_DELIMITER = "\\r\\n"
        const val DEFAULT_FRAME_REGEX =
            """^(?<stable>ST|US),[A-Z]{2},\s*(?<value>[+-]?[0-9]+(?:\.[0-9]+)?)\s*(?<unit>kg|g)$"""
        const val DEFAULT_STABLE_VALUES = "ST"
        const val DEFAULT_UNIT_FACTORS = "kg=1000,g=1"
        const val DEFAULT_POLL_COMMAND = "W\\r"
        const val DEFAULT_POLL_INTERVAL_MS = "500"
        const val DEFAULT_STABILITY_COUNT = "3"
        const val DEFAULT_STABILITY_TOLERANCE_GRAMS = "2"
        const val DEFAULT_STABILITY_WINDOW_MS = "500"

        /**
         * Tolerant on purpose: this reads what is already on disk, and a half-written or hand-edited
         * value must degrade to the default rather than stop the till booting. Strictness lives in
         * [validate], at the point the user applies a change.
         */
        fun fromConfig(config: Map<String, String>): SerialScaleSettings {
            val defaults = SerialScaleSettings()
            return SerialScaleSettings(
                port = config[KEY_PORT] ?: defaults.port,
                baud = config[KEY_BAUD]?.toIntOrNull() ?: defaults.baud,
                framing = SerialFraming.fromLabel(config[KEY_FRAMING]) ?: defaults.framing,
                protocol = ScaleProtocolKind.fromId(config[KEY_PROTOCOL]) ?: defaults.protocol,
                delimiter = config[KEY_DELIMITER] ?: defaults.delimiter,
                frameRegex = config[KEY_FRAME_REGEX] ?: defaults.frameRegex,
                stableValues = config[KEY_STABLE_VALUES] ?: defaults.stableValues,
                unitFactors = config[KEY_UNIT_FACTORS] ?: defaults.unitFactors,
                pollCommand = config[KEY_POLL_COMMAND] ?: defaults.pollCommand,
                pollIntervalMs = config[KEY_POLL_INTERVAL_MS] ?: defaults.pollIntervalMs,
                stabilityCount = config[KEY_STABILITY_COUNT] ?: defaults.stabilityCount,
                stabilityToleranceGrams = config[KEY_STABILITY_TOLERANCE_GRAMS] ?: defaults.stabilityToleranceGrams,
                stabilityWindowMs = config[KEY_STABILITY_WINDOW_MS] ?: defaults.stabilityWindowMs,
            )
        }

        /** `kg=1000,g=1` -> unit to grams factor. `null` when empty, malformed or any factor is not positive. */
        fun parseUnitFactors(raw: String): Map<String, Double>? {
            val entries = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (entries.isEmpty()) return null
            return buildMap {
                for (entry in entries) {
                    val parts = entry.split("=")
                    if (parts.size != 2) return null
                    val unit = parts[0].trim()
                    val factor = parts[1].trim().toDoubleOrNull()
                    if (unit.isEmpty() || factor == null || factor <= 0.0) return null
                    put(unit, factor)
                }
            }
        }
    }
}

private fun String.isIntIn(range: IntRange): Boolean = trim().toIntOrNull()?.let { it in range } ?: false

/**
 * Turns the typeable escapes `\r`, `\n`, `\t`, `\\` and `\xHH` into the real characters. Text with
 * no backslash passes through unchanged, so a value that already holds a raw CR/LF still works.
 */
fun unescapeControlChars(raw: String): String = buildString {
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c != '\\' || i + 1 >= raw.length) {
            append(c)
            i++
            continue
        }
        when (val next = raw[i + 1]) {
            'r' -> { append('\r'); i += 2 }
            'n' -> { append('\n'); i += 2 }
            't' -> { append('\t'); i += 2 }
            '\\' -> { append('\\'); i += 2 }
            'x' -> {
                // Exactly two hex digits: `toIntOrNull(16)` alone would also accept "+1" or "-1".
                val digits = if (i + 4 <= raw.length) raw.substring(i + 2, i + 4) else ""
                if (digits.length == 2 && digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                    append(digits.toInt(16).toChar())
                    i += 4
                } else {
                    append(c).append(next)
                    i += 2
                }
            }
            else -> { append(c).append(next); i += 2 }
        }
    }
}
