package com.f776.ecopan.data.hardware.jvm

import com.f776.ecopan.domain.hardware.ScaleDefaults
import com.f776.ecopan.domain.hardware.ScaleFrameParser
import com.f776.ecopan.domain.hardware.ScaleProtocolKind
import com.f776.ecopan.domain.hardware.SerialScaleSettings
import com.f776.ecopan.domain.hardware.WeightReading
import com.f776.ecopan.domain.hardware.unescapeControlChars
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Serial scale wire protocols as data, not code forks (04 §3). [feed] is stateful across calls --
 * frames may arrive split across multiple reads (or, worst case, one byte at a time) so
 * implementations must buffer until a complete, delimited frame is seen before parsing it. All
 * arithmetic is in integer grams; sub-gram devices round half-up at this parser boundary (01 §5).
 */
internal interface ScaleProtocol {
    /** Feed raw bytes; returns zero or more complete parsed readings (grams). */
    fun feed(bytes: ByteArray): List<WeightReading>

    /** `null` => continuous-stream protocol (device pushes frames on its own). */
    val pollCommand: ByteArray?
    val pollIntervalMs: Long?
}

/**
 * `continuous-generic` (04 §3): a regex-configurable parser for ASCII frames like
 * `ST,GS, 1.234 kg\r\n`. Nothing here is hardcoded to one brand's frame format -- [frameRegex],
 * [delimiter], and [unitFactors] are all config strings, so a new frame format is a settings row,
 * not a new class. When the frame carries no stability flag (no `stable` capture group matches),
 * falls back to the software stability heuristic: [stabilityCount] consecutive readings within
 * [stabilityToleranceGrams] of each other, all inside a [stabilityWindowMs] window (defaults
 * 3 / 2 g / 500 ms per plan).
 */
internal class ContinuousGenericProtocol(
    private val delimiter: String = DEFAULT_DELIMITER,
    private val frameRegex: Regex = DEFAULT_FRAME_REGEX,
    private val stableValues: Set<String> = DEFAULT_STABLE_VALUES,
    private val unitFactors: Map<String, Double> = DEFAULT_UNIT_FACTORS,
    private val stabilityCount: Int = DEFAULT_STABILITY_COUNT,
    private val stabilityToleranceGrams: Long = DEFAULT_STABILITY_TOLERANCE_GRAMS,
    private val stabilityWindowMs: Long = DEFAULT_STABILITY_WINDOW_MS,
) : ScaleProtocol {

    override val pollCommand: ByteArray? = null
    override val pollIntervalMs: Long? = null

    private val buffer = StringBuilder()
    private val stabilityHistory = ArrayDeque<Pair<Long, Instant>>()

    override fun feed(bytes: ByteArray): List<WeightReading> {
        buffer.append(String(bytes, Charsets.US_ASCII))
        val out = mutableListOf<WeightReading>()
        while (true) {
            val idx = buffer.indexOf(delimiter)
            if (idx < 0) break
            val frame = buffer.substring(0, idx)
            buffer.delete(0, idx + delimiter.length)
            parseFrame(frame)?.let { out += it }
        }
        // A device with the wrong baud/delimiter streams bytes that never complete a frame. Keep
        // only a tail so the buffer (and the `indexOf` above) cannot grow without bound; the frame
        // that the cut lands in is garbage anyway and the next delimiter resynchronises.
        if (buffer.length > MAX_BUFFER_CHARS) buffer.delete(0, buffer.length - MAX_BUFFER_CHARS)
        return out
    }

    /** Visible for tests: characters currently waiting for a delimiter. */
    internal val bufferedLength: Int get() = buffer.length

    private fun parseFrame(rawFrame: String): WeightReading? {
        // ScaleFrameParser is the one parser shared with the Settings text probe; it runs the user's
        // regex under a time budget, so a hostile or merely unlucky frame costs one dropped reading,
        // never a stuck serial thread.
        val parsed = ScaleFrameParser.parse(rawFrame, frameRegex, stableValues, unitFactors) ?: return null
        val at = Clock.System.now()
        return WeightReading(
            grams = parsed.grams,
            stable = parsed.stable ?: computeSoftwareStability(parsed.grams, at),
            at = at,
            rawText = rawFrame.trim().takeIf { it.isNotEmpty() },
        )
    }

    /** K/T/W software stability fallback (04 §3) for devices whose frames carry no flag of their own. */
    private fun computeSoftwareStability(grams: Long, at: Instant): Boolean {
        stabilityHistory.addLast(grams to at)
        while (stabilityHistory.size > stabilityCount) stabilityHistory.removeFirst()
        if (stabilityHistory.size < stabilityCount) return false

        val windowMs = (at - stabilityHistory.first().second).inWholeMilliseconds
        if (windowMs > stabilityWindowMs) return false

        val grouped = stabilityHistory.map { it.first }
        return (grouped.max() - grouped.min()) <= stabilityToleranceGrams
    }

    companion object {
        const val DEFAULT_DELIMITER = "\r\n"
        const val MAX_BUFFER_CHARS = 4096
        val DEFAULT_STABLE_VALUES = setOf("ST")
        val DEFAULT_UNIT_FACTORS = mapOf("kg" to 1000.0, "g" to 1.0)
        const val DEFAULT_STABILITY_COUNT = 3
        const val DEFAULT_STABILITY_TOLERANCE_GRAMS = 2L
        const val DEFAULT_STABILITY_WINDOW_MS = 500L

        /** Matches frames like `ST,GS, 1.234 kg` / `US,GS,+000.842kg` -- a plausible generic default. */
        val DEFAULT_FRAME_REGEX = Regex(ScaleDefaults.FRAME_REGEX)
    }
}

/**
 * `poll-generic` (04 §3): identical frame parsing to [ContinuousGenericProtocol] -- the device
 * only differs in that it stays silent until sent [pollCommand] every [pollIntervalMs]. Reusing
 * the same parser keeps "poll vs. stream" a config difference, not a second parsing implementation.
 */
internal class PollGenericProtocol(
    delimiter: String = ContinuousGenericProtocol.DEFAULT_DELIMITER,
    frameRegex: Regex = ContinuousGenericProtocol.DEFAULT_FRAME_REGEX,
    stableValues: Set<String> = ContinuousGenericProtocol.DEFAULT_STABLE_VALUES,
    unitFactors: Map<String, Double> = ContinuousGenericProtocol.DEFAULT_UNIT_FACTORS,
    stabilityCount: Int = ContinuousGenericProtocol.DEFAULT_STABILITY_COUNT,
    stabilityToleranceGrams: Long = ContinuousGenericProtocol.DEFAULT_STABILITY_TOLERANCE_GRAMS,
    stabilityWindowMs: Long = ContinuousGenericProtocol.DEFAULT_STABILITY_WINDOW_MS,
    override val pollCommand: ByteArray = DEFAULT_POLL_COMMAND,
    override val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) : ScaleProtocol {

    private val delegate = ContinuousGenericProtocol(
        delimiter = delimiter,
        frameRegex = frameRegex,
        stableValues = stableValues,
        unitFactors = unitFactors,
        stabilityCount = stabilityCount,
        stabilityToleranceGrams = stabilityToleranceGrams,
        stabilityWindowMs = stabilityWindowMs,
    )

    override fun feed(bytes: ByteArray): List<WeightReading> = delegate.feed(bytes)

    companion object {
        val DEFAULT_POLL_COMMAND = "W\r".toByteArray(Charsets.US_ASCII)
        const val DEFAULT_POLL_INTERVAL_MS = 500L
    }
}

/** Builds the [ScaleProtocol] a [SerialScaleSettings] describes (04 §1.4), config-driven throughout. */
internal object ScaleProtocolFactory {

    /** @throws IllegalArgumentException if the settings do not [SerialScaleSettings.validate] -- callers surface that as a driver error. */
    fun create(settings: SerialScaleSettings): ScaleProtocol {
        val delimiter = unescapeControlChars(settings.delimiter)
        val frameRegex = Regex(settings.frameRegex)
        val stableValues = settings.stableValues.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val unitFactors = requireNotNull(SerialScaleSettings.parseUnitFactors(settings.unitFactors)) { "invalid unit factors" }
        val stabilityCount = requireNotNull(settings.stabilityCount.trim().toIntOrNull()) { "invalid stability count" }
        val stabilityToleranceGrams =
            requireNotNull(settings.stabilityToleranceGrams.trim().toLongOrNull()) { "invalid stability tolerance" }
        val stabilityWindowMs = requireNotNull(settings.stabilityWindowMs.trim().toLongOrNull()) { "invalid stability window" }

        return when (settings.protocol) {
            ScaleProtocolKind.POLL_GENERIC -> PollGenericProtocol(
                delimiter = delimiter,
                frameRegex = frameRegex,
                stableValues = stableValues,
                unitFactors = unitFactors,
                stabilityCount = stabilityCount,
                stabilityToleranceGrams = stabilityToleranceGrams,
                stabilityWindowMs = stabilityWindowMs,
                pollCommand = unescapeControlChars(settings.pollCommand).toByteArray(Charsets.ISO_8859_1),
                pollIntervalMs = requireNotNull(settings.pollIntervalMs.trim().toLongOrNull()) { "invalid poll interval" },
            )
            ScaleProtocolKind.CONTINUOUS_GENERIC -> ContinuousGenericProtocol(
                delimiter = delimiter,
                frameRegex = frameRegex,
                stableValues = stableValues,
                unitFactors = unitFactors,
                stabilityCount = stabilityCount,
                stabilityToleranceGrams = stabilityToleranceGrams,
                stabilityWindowMs = stabilityWindowMs,
            )
        }
    }
}
