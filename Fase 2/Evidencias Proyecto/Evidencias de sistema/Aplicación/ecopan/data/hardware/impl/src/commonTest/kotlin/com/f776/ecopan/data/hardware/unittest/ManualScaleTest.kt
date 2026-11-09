package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.ManualScale
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.Scale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class ManualScaleContractTest : ScaleContractTest() {
    override fun createScale() = ManualScale(emptyMap())
    override suspend fun triggerReading(scale: Scale) {
        (scale as ManualScale).submitWeight(500)
    }
}

internal class ManualScaleTest {

    @Test
    fun `defaults to DISCONNECTED before start`() {
        val scale = ManualScale(emptyMap())
        assertEquals(DeviceStatus.DISCONNECTED, scale.status.value)
    }

    @Test
    fun `submitWeight rejects non-positive grams`() {
        val scale = ManualScale(emptyMap())
        scale.start()

        assertFailsWith<IllegalArgumentException> { scale.submitWeight(0) }
        assertFailsWith<IllegalArgumentException> { scale.submitWeight(-5) }
    }

    @Test
    fun `submitWeight rejects grams above the configured max`() {
        val scale = ManualScale(mapOf("maxGrams" to "1000"))
        scale.start()

        assertFailsWith<IllegalArgumentException> { scale.submitWeight(1001) }
    }

    @Test
    fun `submitWeight defaults the max to 50000 grams when unconfigured`() {
        val scale = ManualScale(emptyMap())
        scale.start()

        assertFailsWith<IllegalArgumentException> { scale.submitWeight(50_001) }
    }

    @Test
    fun `each submit emits exactly one stable reading`() = runTest {
        val scale = ManualScale(emptyMap())
        scale.start()

        scale.submitWeight(842)
        val reading = scale.readings.first()

        assertEquals(842L, reading.grams)
        assertTrue(reading.stable)
    }
}
