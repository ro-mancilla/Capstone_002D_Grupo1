package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.MockDrawer
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.SimulatedDrawer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class MockDrawerTest {

    @Test
    fun `is always CONNECTED`() {
        assertEquals(DeviceStatus.CONNECTED, MockDrawer().status.value)
    }

    @Test
    fun `says it is simulated so the till can show it`() {
        assertTrue(MockDrawer() is SimulatedDrawer)
    }

    @Test
    fun `open emits an event`() = runTest {
        val drawer = MockDrawer()

        drawer.open()

        drawer.openEvents.first()
    }
}
