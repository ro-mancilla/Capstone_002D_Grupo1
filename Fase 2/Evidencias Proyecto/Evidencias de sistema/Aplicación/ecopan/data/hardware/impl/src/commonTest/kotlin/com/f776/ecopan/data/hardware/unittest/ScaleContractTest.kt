package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.Scale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared contract (04 §7) every [Scale] driver must satisfy, regardless of implementation.
 * [triggerReading] exists because some drivers emit on their own (mock) while others need an
 * explicit external trigger (manual) — it is a no-op unless overridden.
 */
internal abstract class ScaleContractTest {

    abstract fun createScale(): Scale

    protected open suspend fun triggerReading(scale: Scale) {}

    @Test
    fun `start is idempotent`() = runTest {
        val scale = createScale()
        scale.start()
        scale.start()

        assertEquals(DeviceStatus.CONNECTED, scale.status.first { it == DeviceStatus.CONNECTED })

        scale.close()
    }

    @Test
    fun `status reaches CONNECTED after start`() = runTest {
        val scale = createScale()

        scale.start()

        assertEquals(DeviceStatus.CONNECTED, scale.status.first { it == DeviceStatus.CONNECTED })
        scale.close()
    }

    @Test
    fun `stable readings are always positive grams`() = runTest {
        val scale = createScale()
        scale.start()
        triggerReading(scale)

        val reading = scale.readings.first { it.stable }

        assertTrue(reading.grams > 0, "expected a positive stable reading, was ${reading.grams}")
        scale.close()
    }
}
