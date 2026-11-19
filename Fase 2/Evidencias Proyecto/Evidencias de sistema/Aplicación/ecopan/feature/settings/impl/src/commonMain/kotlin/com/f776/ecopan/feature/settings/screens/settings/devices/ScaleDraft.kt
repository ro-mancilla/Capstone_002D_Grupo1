package com.f776.ecopan.feature.settings.screens.settings.devices

import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.domain.hardware.ScaleDefaults
import com.f776.ecopan.domain.hardware.ScaleFrameParser
import com.f776.ecopan.domain.hardware.ScaleProtocolKind
import com.f776.ecopan.domain.hardware.SerialFraming
import com.f776.ecopan.domain.hardware.SerialScaleSettings

/** The three ways a till gets its weight. [driverId] is the registry driver behind each. */
internal enum class ScaleModeOption(val driverId: String) {
    Manual("manual"),
    Serial("serial"),
    Simulated("mock"),
    ;

    companion object {
        fun fromDriverId(id: String): ScaleModeOption = entries.firstOrNull { it.driverId == id } ?: Manual
    }
}

/** A scale maker whose settings we pre-fill. [Other] opens the basic fields instead. */
internal enum class ScaleBrand(val id: String, val baud: Int, val framing: SerialFraming, val protocol: String) {
    Cas("cas", baud = 9600, framing = SerialFraming.EIGHT_N_ONE, protocol = SerialDraft.CONTINUOUS),
    Toledo("toledo", baud = 9600, framing = SerialFraming.EIGHT_N_ONE, protocol = SerialDraft.POLL),
    Other("other", baud = 9600, framing = SerialFraming.EIGHT_N_ONE, protocol = SerialDraft.CONTINUOUS),
    ;

    companion object {
        fun fromId(id: String?): ScaleBrand = entries.firstOrNull { it.id == id } ?: Cas

        /**
         * The brand is not stored (the registry keeps only the serial settings), so it is read back
         * from them: a preset's exact line parameters name that preset, anything else is "Otra".
         */
        fun infer(baud: String, framing: SerialFraming, protocol: String): ScaleBrand =
            listOf(Cas, Toledo).firstOrNull { it.baud.toString() == baud.trim() && it.framing == framing && it.protocol == protocol } ?: Other
    }
}

/** A field of the serial scale form. [advanced] fields live behind "Avanzado: trama y estabilidad". */
internal enum class ScaleField(val advanced: Boolean) {
    Port(false),
    Baud(false),
    Delimiter(true),
    FrameRegex(true),
    UnitFactors(true),
    StableValues(true),
    PollCommand(true),
    PollInterval(true),
    StabilityCount(true),
    ToleranceGrams(true),
    WindowMs(true),
}

/** Why a field is wrong. The screen turns each into the copy deck's sentence; nothing here is user text. */
internal sealed interface ScaleFieldError {
    /** A whole number outside [min]..[max] (or not a number at all). */
    data class OutOfRange(val min: Long, val max: Long) : ScaleFieldError

    data object PortRequired : ScaleFieldError

    /** Nothing was typed in a field that needs a value. */
    data object Required : ScaleFieldError

    /** The frame cannot be compiled, or is empty: "Con esta trama no se encuentra el peso." */
    data object FrameInvalid : ScaleFieldError

    /** The frame never says where the weight or the unit is. */
    data object FrameGroupsMissing : ScaleFieldError

    /** Nested repetition that can hang the reader. */
    data object FrameTooComplex : ScaleFieldError

    data object UnitsInvalid : ScaleFieldError
    data object StableValuesEmpty : ScaleFieldError
}

/** What the "text probe" found in a pasted line. */
internal sealed interface ProbeResult {
    data class Read(val grams: Long, val stable: Boolean?) : ProbeResult
    data object NothingFound : ProbeResult

    /** The frame itself is wrong, so there is nothing to test against. */
    data object FrameBroken : ProbeResult
}

/**
 * Everything the serial form edits, as the text the person typed. Kept as strings so a half-typed
 * "96" is a draft the form can show an error for rather than a value it must reject.
 *
 * It is a view over [SerialScaleSettings]: that class owns the persisted keys, the defaults and the
 * rules (including the regex time budget), this one only adds what a form needs -- the brand
 * presets and per-field errors in the vocabulary of the screen.
 */
internal data class SerialDraft(
    val brand: ScaleBrand = ScaleBrand.Cas,
    val port: String = "",
    val baud: String = SerialScaleSettings.DEFAULT_BAUD.toString(),
    val framing: SerialFraming = SerialFraming.EIGHT_N_ONE,
    val protocol: String = CONTINUOUS,
    val delimiter: String = SerialScaleSettings.DEFAULT_DELIMITER,
    val frameRegex: String = ScaleDefaults.FRAME_REGEX,
    val unitFactors: String = ScaleDefaults.UNIT_FACTORS,
    val stableValues: String = ScaleDefaults.STABLE_VALUES,
    val pollCommand: String = SerialScaleSettings.DEFAULT_POLL_COMMAND,
    val pollIntervalMs: String = SerialScaleSettings.DEFAULT_POLL_INTERVAL_MS,
    val stabilityCount: String = SerialScaleSettings.DEFAULT_STABILITY_COUNT,
    val toleranceGrams: String = SerialScaleSettings.DEFAULT_STABILITY_TOLERANCE_GRAMS,
    val windowMs: String = SerialScaleSettings.DEFAULT_STABILITY_WINDOW_MS,
) {
    /** Picking a brand fills baud, framing and protocol; it leaves the port and the advanced frame alone. */
    fun withBrand(brand: ScaleBrand): SerialDraft =
        copy(brand = brand, baud = brand.baud.toString(), framing = brand.framing, protocol = brand.protocol)

    /** "Restablecer valores": the frame, units and stability go back to the generic defaults. */
    fun withAdvancedDefaults(): SerialDraft {
        val defaults = SerialDraft()
        return copy(
            delimiter = defaults.delimiter,
            frameRegex = defaults.frameRegex,
            unitFactors = defaults.unitFactors,
            stableValues = defaults.stableValues,
            pollCommand = defaults.pollCommand,
            pollIntervalMs = defaults.pollIntervalMs,
            stabilityCount = defaults.stabilityCount,
            toleranceGrams = defaults.toleranceGrams,
            windowMs = defaults.windowMs,
        )
    }

    fun with(field: ScaleField, value: String): SerialDraft = when (field) {
        ScaleField.Port -> copy(port = value)
        ScaleField.Baud -> copy(baud = value)
        ScaleField.Delimiter -> copy(delimiter = value)
        ScaleField.FrameRegex -> copy(frameRegex = value)
        ScaleField.UnitFactors -> copy(unitFactors = value)
        ScaleField.StableValues -> copy(stableValues = value)
        ScaleField.PollCommand -> copy(pollCommand = value)
        ScaleField.PollInterval -> copy(pollIntervalMs = value)
        ScaleField.StabilityCount -> copy(stabilityCount = value)
        ScaleField.ToleranceGrams -> copy(toleranceGrams = value)
        ScaleField.WindowMs -> copy(windowMs = value)
    }

    private fun toSettings() = SerialScaleSettings(
        port = port,
        // 0 is outside the legal range, so a half-typed baud surfaces as that field's error.
        baud = baud.trim().toIntOrNull() ?: 0,
        framing = framing,
        protocol = ScaleProtocolKind.fromId(protocol) ?: ScaleProtocolKind.CONTINUOUS_GENERIC,
        delimiter = delimiter,
        frameRegex = frameRegex,
        stableValues = stableValues,
        unitFactors = unitFactors,
        pollCommand = pollCommand,
        pollIntervalMs = pollIntervalMs,
        stabilityCount = stabilityCount,
        stabilityToleranceGrams = toleranceGrams,
        stabilityWindowMs = windowMs,
    )

    fun validate(): Map<ScaleField, ScaleFieldError> = buildMap {
        for ((field, problem) in toSettings().validate()) {
            put(field.toScaleField(), problem.toError(field))
        }
        // The static check is cheaper than the time budget and catches the same hazard before any
        // regex runs, so it names the frame first; the blank frame is "no weight found", not "no groups".
        frameError(frameRegex)?.let { put(ScaleField.FrameRegex, it) }
    }

    /** What reading [text] with the current frame, units and stability values gives -- the real parser, not a guess. */
    fun probe(text: String): ProbeResult {
        if (frameError(frameRegex) != null || SerialScaleSettings.Field.FRAME_REGEX in toSettings().validate()) return ProbeResult.FrameBroken
        val parsed = ScaleFrameParser.parse(
            frame = text,
            regex = Regex(frameRegex),
            stableValues = ScaleDefaults.parseStableValues(stableValues),
            unitFactors = ScaleDefaults.parseUnitFactors(unitFactors),
        ) ?: return ProbeResult.NothingFound
        return ProbeResult.Read(parsed.grams, parsed.stable)
    }

    fun toConfig(): Map<String, String> = toSettings().toConfig()

    companion object {
        const val CONTINUOUS = "continuous-generic"
        const val POLL = "poll-generic"

        fun fromConfig(config: Map<String, String>): SerialDraft {
            val s = SerialScaleSettings.fromConfig(config)
            val protocol = s.protocol.id
            return SerialDraft(
                brand = ScaleBrand.infer(s.baud.toString(), s.framing, protocol),
                port = s.port,
                baud = s.baud.toString(),
                framing = s.framing,
                protocol = protocol,
                delimiter = s.delimiter,
                frameRegex = s.frameRegex,
                unitFactors = s.unitFactors,
                stableValues = s.stableValues,
                pollCommand = s.pollCommand,
                pollIntervalMs = s.pollIntervalMs,
                stabilityCount = s.stabilityCount,
                toleranceGrams = s.stabilityToleranceGrams,
                windowMs = s.stabilityWindowMs,
            )
        }

        private fun SerialScaleSettings.Field.toScaleField(): ScaleField = when (this) {
            SerialScaleSettings.Field.PORT -> ScaleField.Port
            SerialScaleSettings.Field.BAUD -> ScaleField.Baud
            SerialScaleSettings.Field.DELIMITER -> ScaleField.Delimiter
            SerialScaleSettings.Field.FRAME_REGEX -> ScaleField.FrameRegex
            SerialScaleSettings.Field.STABLE_VALUES -> ScaleField.StableValues
            SerialScaleSettings.Field.UNIT_FACTORS -> ScaleField.UnitFactors
            SerialScaleSettings.Field.POLL_COMMAND -> ScaleField.PollCommand
            SerialScaleSettings.Field.POLL_INTERVAL -> ScaleField.PollInterval
            SerialScaleSettings.Field.STABILITY_COUNT -> ScaleField.StabilityCount
            SerialScaleSettings.Field.STABILITY_TOLERANCE -> ScaleField.ToleranceGrams
            SerialScaleSettings.Field.STABILITY_WINDOW -> ScaleField.WindowMs
        }

        private fun SerialScaleSettings.Problem.toError(field: SerialScaleSettings.Field): ScaleFieldError = when (this) {
            SerialScaleSettings.Problem.REQUIRED -> when (field) {
                SerialScaleSettings.Field.PORT -> ScaleFieldError.PortRequired
                SerialScaleSettings.Field.STABLE_VALUES -> ScaleFieldError.StableValuesEmpty
                else -> ScaleFieldError.Required
            }
            SerialScaleSettings.Problem.INVALID_REGEX -> ScaleFieldError.FrameInvalid
            SerialScaleSettings.Problem.MISSING_GROUPS -> ScaleFieldError.FrameGroupsMissing
            SerialScaleSettings.Problem.REGEX_TOO_SLOW -> ScaleFieldError.FrameTooComplex
            SerialScaleSettings.Problem.INVALID_UNIT_FACTORS -> ScaleFieldError.UnitsInvalid
            SerialScaleSettings.Problem.INVALID_NUMBER -> rangeOf(field)
        }

        private fun rangeOf(field: SerialScaleSettings.Field): ScaleFieldError {
            val range = when (field) {
                SerialScaleSettings.Field.BAUD -> SerialScaleSettings.BAUD_RANGE
                SerialScaleSettings.Field.POLL_INTERVAL -> SerialScaleSettings.POLL_INTERVAL_RANGE
                SerialScaleSettings.Field.STABILITY_COUNT -> SerialScaleSettings.STABILITY_COUNT_RANGE
                SerialScaleSettings.Field.STABILITY_TOLERANCE -> SerialScaleSettings.STABILITY_TOLERANCE_RANGE
                else -> SerialScaleSettings.STABILITY_WINDOW_RANGE
            }
            return ScaleFieldError.OutOfRange(range.first.toLong(), range.last.toLong())
        }

        /**
         * The weight must be findable (a compilable, non-empty frame naming `value` and `unit`) and
         * cheap to run. A frame with a repeated group that is itself repeated -- `(a+)+` -- can take
         * exponential time on a line that nearly matches, which would freeze the reader thread.
         */
        private fun frameError(source: String): ScaleFieldError? {
            if (source.isBlank()) return ScaleFieldError.FrameInvalid
            if (source.length > MAX_FRAME_LENGTH || NESTED_REPETITION.containsMatchIn(source)) return ScaleFieldError.FrameTooComplex
            return null
        }

        private const val MAX_FRAME_LENGTH = 300

        /** A quantified group whose body already contains a quantifier: `(a+)+`, `(\d*)*`, `(x{2,})+`. */
        private val NESTED_REPETITION = Regex("""\((?:[^()\\]|\\.)*[+*}](?:[^()\\]|\\.)*\)[+*{]""")
    }
}

/** What the scale card edits as a whole: which mode, and the serial form that matters only in [ScaleModeOption.Serial]. */
internal data class ScaleDraft(
    val mode: ScaleModeOption = ScaleModeOption.Manual,
    val serial: SerialDraft = SerialDraft(),
) {
    /** Only serial has fields to get wrong. */
    fun validate(): Map<ScaleField, ScaleFieldError> =
        if (mode == ScaleModeOption.Serial) serial.validate() else emptyMap()

    fun toChoice(): DriverChoice = DriverChoice(
        driverId = mode.driverId,
        config = if (mode == ScaleModeOption.Serial) serial.toConfig() else emptyMap(),
    )

    companion object {
        fun fromChoice(choice: DriverChoice): ScaleDraft = ScaleDraft(
            mode = ScaleModeOption.fromDriverId(choice.driverId),
            serial = SerialDraft.fromConfig(choice.config),
        )
    }
}
