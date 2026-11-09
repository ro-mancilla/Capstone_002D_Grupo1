package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.domain.hardware.DeviceDisplay
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.deriveDeviceDisplay
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceDisplayTest {
    private val demo = DriverDescriptor("mock", "Demo", false, simulated = true)
    private val noPrinter = DriverDescriptor("mock", "Sin impresora", false, none = true)
    private val real = DriverDescriptor("real", "Real", true)
    private val kick = DriverDescriptor("printer-kick", "Cajón vía impresora", true, viaPrinter = true)

    @Test
    fun `scale - demo is simulated even while it reports connected`() {
        assertEquals(DeviceDisplay.Simulated, deriveDeviceDisplay(demo, DeviceStatus.CONNECTED))
    }

    @Test
    fun `scale - serial maps its status`() {
        assertEquals(DeviceDisplay.Ready, deriveDeviceDisplay(real, DeviceStatus.CONNECTED))
        assertEquals(DeviceDisplay.Error, deriveDeviceDisplay(real, DeviceStatus.DISCONNECTED))
        assertEquals(DeviceDisplay.Error, deriveDeviceDisplay(real, DeviceStatus.ERROR))
    }

    @Test
    fun `printer - sin impresora is none, not simulated`() {
        assertEquals(DeviceDisplay.None, deriveDeviceDisplay(noPrinter))
        assertEquals(DeviceDisplay.Ready, deriveDeviceDisplay(real))
        assertEquals(DeviceDisplay.Error, deriveDeviceDisplay(real, DeviceStatus.ERROR))
    }

    @Test
    fun `drawer - demo drawer is simulated`() {
        assertEquals(DeviceDisplay.Simulated, deriveDeviceDisplay(demo, printer = real))
    }

    @Test
    fun `drawer - via printer follows the printer`() {
        assertEquals(DeviceDisplay.Ready, deriveDeviceDisplay(kick, DeviceStatus.CONNECTED, printer = real))
        assertEquals(DeviceDisplay.Simulated, deriveDeviceDisplay(kick, DeviceStatus.CONNECTED, printer = noPrinter))
        assertEquals(DeviceDisplay.Simulated, deriveDeviceDisplay(kick, DeviceStatus.CONNECTED, printer = demo))
        assertEquals(DeviceDisplay.Error, deriveDeviceDisplay(kick, DeviceStatus.DISCONNECTED, printer = real))
    }

    @Test
    fun `terminal - demo is simulated, real is ready`() {
        assertEquals(DeviceDisplay.Simulated, deriveDeviceDisplay(demo))
        assertEquals(DeviceDisplay.Ready, deriveDeviceDisplay(real))
    }

    @Test
    fun `unknown driver reads as none`() {
        assertEquals(DeviceDisplay.None, deriveDeviceDisplay(null))
    }
}
