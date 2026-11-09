package com.f776.ecopan.data.hardware

import co.touchlab.kermit.Logger
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/**
 * The weights `MockScale` plays when the cashier picks a by-weight product, so a stage demo shows
 * the same ramp-then-settle for the same bread every time. SIMULATED: this scripts a fake scale, it
 * says nothing about any real one.
 *
 * Format, one product per line (see `docs/hardware/demo-weights.reference.txt`):
 * ```
 * # comment
 * Marraqueta: 310, 640, 842
 * ```
 * Grams in order; every value but the last is an unstable reading, the last is the stable one. The
 * weights follow the LAST colon, so a product name may itself contain colons. Product names are
 * matched ignoring case and surrounding spaces; if a name appears twice the later line wins. A
 * leading byte order mark and a trailing comma are tolerated. A line that does not parse is skipped
 * and logged with its line number, never fatal.
 */
internal class DemoWeightScript private constructor(private val sequences: Map<String, List<Long>>) {

    fun sequenceFor(productName: String): List<Long>? = sequences[key(productName)]

    companion object {
        /** Config key on the mock scale: path to a script file. */
        const val CONFIG_KEY = "script"

        /** Looked for in the working directory when no [CONFIG_KEY] is set. */
        const val DEFAULT_FILE = "demo-weights.txt"

        private const val BYTE_ORDER_MARK = '﻿'

        val EMPTY = DemoWeightScript(emptyMap())

        fun parse(text: String, onSkipped: (String) -> Unit = { Logger.w { "DemoWeightScript: $it" } }): DemoWeightScript {
            val sequences = LinkedHashMap<String, List<Long>>()
            text.removePrefix(BYTE_ORDER_MARK.toString()).lines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
                val number = index + 1
                val colon = line.lastIndexOf(':')
                val name = if (colon > 0) line.substring(0, colon).trim() else ""
                val grams = line.substring(colon + 1).split(",").map { it.trim() }.dropLastWhile { it.isEmpty() }
                    .map { it.toLongOrNull() }
                if (colon <= 0 || name.isEmpty() || grams.isEmpty() || grams.any { it == null || it <= 0 }) {
                    onSkipped("skipped line $number, expected `name: grams, grams, ...`: $line")
                    return@forEachIndexed
                }
                if (sequences.put(key(name), grams.filterNotNull()) != null) {
                    onSkipped("line $number repeats '$name'; the later line wins")
                }
            }
            return DemoWeightScript(sequences)
        }

        /**
         * Reads the script named by [CONFIG_KEY], else [DEFAULT_FILE] in the working directory. This
         * does BLOCKING file I/O: callers run it on an IO dispatcher. A missing default file is simply
         * no script; a named file that cannot be read is logged and also gives no script.
         */
        fun fromConfig(config: Map<String, String>): DemoWeightScript {
            val named = config[CONFIG_KEY]?.takeIf { it.isNotBlank() }
            val path = Path(named ?: DEFAULT_FILE)
            if (named == null && !SystemFileSystem.exists(path)) return EMPTY
            return runCatching {
                SystemFileSystem.source(path).buffered().use { parse(it.readString()) }
            }.getOrElse {
                Logger.w(it) { "DemoWeightScript: could not read '$path'; the mock scale keeps its random weights" }
                EMPTY
            }
        }

        private fun key(name: String) = name.trim().lowercase()
    }
}
