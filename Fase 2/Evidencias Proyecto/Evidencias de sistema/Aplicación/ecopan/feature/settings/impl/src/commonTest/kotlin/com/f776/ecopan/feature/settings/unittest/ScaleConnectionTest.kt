package com.f776.ecopan.feature.settings.unittest

import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleConnection
import com.f776.ecopan.feature.settings.screens.settings.devices.ScaleModeOption
import com.f776.ecopan.feature.settings.screens.settings.devices.deriveScaleConnection
import kotlin.test.Test
import kotlin.test.assertEquals

class ScaleConnectionTest {

    private fun serial(
        status: DeviceStatus,
        hasReading: Boolean = false,
        waiting: Boolean = false,
        noPorts: Boolean = false,
    ) = deriveScaleConnection(ScaleModeOption.Serial, status, hasReading, waiting, noPorts)

    @Test
    fun `manual entry has no connection to report`() {
        assertEquals(ScaleConnection.Manual, deriveScaleConnection(ScaleModeOption.Manual, DeviceStatus.DISCONNECTED, false, false, false))
    }

    @Test
    fun `the simulated scale is always labelled simulated, whatever else is true`() {
        assertEquals(ScaleConnection.Simulated, deriveScaleConnection(ScaleModeOption.Simulated, DeviceStatus.CONNECTED, true, false, false))
    }

    @Test
    fun `just applied, waiting for the first reading, is connecting`() {
        assertEquals(ScaleConnection.Connecting, serial(DeviceStatus.DISCONNECTED, waiting = true))
        assertEquals(ScaleConnection.Connecting, serial(DeviceStatus.CONNECTED, waiting = true))
    }

    @Test
    fun `a connected port with readings responds`() {
        assertEquals(ScaleConnection.Responding, serial(DeviceStatus.CONNECTED, hasReading = true, waiting = true))
    }

    @Test
    fun `an open port that sends nothing for the whole window is no data`() {
        assertEquals(ScaleConnection.NoData, serial(DeviceStatus.CONNECTED, waiting = false))
    }

    @Test
    fun `a scale that answered and then went quiet is no data, not responding`() {
        assertEquals(ScaleConnection.NoData, serial(DeviceStatus.DISCONNECTED, hasReading = true, waiting = false))
    }

    @Test
    fun `an error status is a port that could not be opened`() {
        assertEquals(ScaleConnection.PortBusy, serial(DeviceStatus.ERROR, waiting = true))
    }

    @Test
    fun `no ports at all beats the generic failures`() {
        assertEquals(ScaleConnection.NoPorts, serial(DeviceStatus.ERROR, noPorts = true))
        assertEquals(ScaleConnection.NoPorts, serial(DeviceStatus.DISCONNECTED, waiting = true, noPorts = true))
    }
}
