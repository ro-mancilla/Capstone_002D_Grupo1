package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.jvm.ContinuousGenericProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ScaleProtocolHardeningTest {

    @Test
    fun `the receive buffer is capped when no delimiter ever arrives`() {
        val protocol = ContinuousGenericProtocol()

        repeat(100) { protocol.feed(ByteArray(1024) { 'x'.code.toByte() }) }

        assertTrue(protocol.bufferedLength <= ContinuousGenericProtocol.MAX_BUFFER_CHARS)
    }

    @Test
    fun `a good frame after capped garbage still parses`() {
        val protocol = ContinuousGenericProtocol()
        repeat(100) { protocol.feed(ByteArray(1024) { 'x'.code.toByte() }) }

        val readings = protocol.feed("\r\nST,GS, 0.842 kg\r\n".encodeToByteArray())

        assertEquals(listOf(842L), readings.map { it.grams })
    }

    @Test
    fun `a catastrophic regex drops the frame instead of hanging`() {
        val protocol = ContinuousGenericProtocol(frameRegex = Regex("^(?<value>(\\2x|x)+y)(?<unit>z)"))
        val started = System.currentTimeMillis()

        val readings = protocol.feed(("x".repeat(64) + "\r\n").encodeToByteArray())

        assertTrue(readings.isEmpty())
        assertTrue(System.currentTimeMillis() - started < 3_000)
    }
}
