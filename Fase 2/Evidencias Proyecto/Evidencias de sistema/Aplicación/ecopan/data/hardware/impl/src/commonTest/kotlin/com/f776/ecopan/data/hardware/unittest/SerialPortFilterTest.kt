package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.domain.hardware.SerialPortInfo
import com.f776.ecopan.domain.hardware.defaultScalePort
import com.f776.ecopan.domain.hardware.userPorts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SerialPortFilterTest {

    private fun ports(vararg names: String) = names.map { SerialPortInfo(it, "") }

    @Test
    fun `macOS system and debug ports are dropped`() {
        val all = ports("cu.Bluetooth-Incoming-Port", "cu.debug-console", "cu.wlan-debug", "cu.usbserial-1410")
        assertEquals(listOf("cu.usbserial-1410"), all.userPorts().map { it.name })
    }

    @Test
    fun `macOS tty duplicate of a cu port is dropped`() {
        val all = ports("cu.usbserial-1410", "tty.usbserial-1410")
        assertEquals(listOf("cu.usbserial-1410"), all.userPorts().map { it.name })
    }

    @Test
    fun `a tty port with no cu twin is kept`() {
        assertEquals(listOf("tty.usbserial-9"), ports("tty.usbserial-9").userPorts().map { it.name })
    }

    @Test
    fun `windows and linux adapters pass through`() {
        val all = ports("COM3", "COM4", "ttyUSB0", "ttyACM0")
        assertEquals(all, all.userPorts())
    }

    @Test
    fun `default never lands on a system port`() {
        assertNull(ports("cu.debug-console", "cu.Bluetooth-Incoming-Port").defaultScalePort())
        assertEquals("cu.usbserial-1410", ports("cu.debug-console", "cu.usbserial-1410").defaultScalePort()?.name)
    }

    @Test
    fun `default prefers an adapter over an onboard uart`() {
        assertEquals("ttyUSB0", ports("ttyS0", "ttyS1", "ttyUSB0").defaultScalePort()?.name)
        assertEquals("ttyS0", ports("ttyS0").defaultScalePort()?.name)
    }

    @Test
    fun `default on windows is the first com port`() {
        assertEquals("COM3", ports("COM3", "COM5").defaultScalePort()?.name)
    }

    @Test
    fun `empty scan has no default`() {
        assertNull(emptyList<SerialPortInfo>().defaultScalePort())
    }
}
