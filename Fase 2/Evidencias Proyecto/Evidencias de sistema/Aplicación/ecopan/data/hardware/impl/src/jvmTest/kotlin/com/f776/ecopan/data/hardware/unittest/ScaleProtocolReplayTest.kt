package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.jvm.ContinuousGenericProtocol
import com.f776.ecopan.domain.hardware.WeightReading
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `SerialScale`/`ScaleProtocol` replay tests (04 §7) -- "no hardware, CI-safe". Feeds the
 * hand-crafted SYNTHETIC capture log (`resources/scale-captures/synthetic-continuous-generic.log`;
 * see its header and `docs/hardware/scale-protocol-notes.md` for the honest NO-GO context: no
 * physical scale was available to record a real one) into [ContinuousGenericProtocol.feed], both
 * chunked at the log's recorded frame boundaries and re-chunked at 1-byte granularity, to prove
 * framing is split-safe either way.
 */
internal class ScaleProtocolReplayTest {

    @Test
    fun `replaying the synthetic log at recorded frame boundaries yields the expected reading sequence`() {
        val frames = loadSyntheticFrames()
        val protocol = ContinuousGenericProtocol()

        val readings = frames.flatMap { protocol.feed(it) }

        assertExpectedSequence(readings)
    }

    @Test
    fun `replaying the synthetic log one byte at a time yields the same sequence -- framing is split-safe`() {
        val allBytes = loadSyntheticFrames().reduce { acc, bytes -> acc + bytes }
        val protocol = ContinuousGenericProtocol()

        val readings = allBytes.flatMap { byte -> protocol.feed(byteArrayOf(byte)) }

        assertExpectedSequence(readings)
    }

    @Test
    fun `garbage bytes yield zero readings and never throw`() {
        val protocol = ContinuousGenericProtocol()
        val garbage = Random(42).nextBytes(2048)

        val readings = protocol.feed(garbage)

        assertTrue(readings.isEmpty(), "expected no parseable frames out of random garbage, got $readings")
    }

    private fun assertExpectedSequence(readings: List<WeightReading>) {
        assertEquals(7, readings.size)
        assertEquals(listOf(81L, 412L, 798L, 839L, 842L, 842L, 842L), readings.map { it.grams })
        assertEquals(
            listOf(false, false, false, false, true, true, true),
            readings.map { it.stable },
        )
        assertTrue(readings.filter { it.stable }.all { it.grams > 0 })
    }

    /** Parses the `HEX=` column of each non-comment log line back into the exact frame bytes. */
    private fun loadSyntheticFrames(): List<ByteArray> {
        val stream = requireNotNull(
            javaClass.classLoader.getResourceAsStream("scale-captures/synthetic-continuous-generic.log"),
        ) { "synthetic-continuous-generic.log missing from test resources" }

        return stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line -> hexToBytes(line.split("\t").first { it.startsWith("HEX=") }.removePrefix("HEX=")) }
                .toList()
        }
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
