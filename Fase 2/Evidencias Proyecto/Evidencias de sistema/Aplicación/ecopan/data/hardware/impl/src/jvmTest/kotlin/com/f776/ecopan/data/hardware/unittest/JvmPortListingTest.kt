package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.jvm.dedupeMacPorts
import com.f776.ecopan.domain.hardware.SerialPortInfo
import kotlin.test.Test
import kotlin.test.assertEquals

internal class JvmPortListingTest {

    @Test
    fun `a macOS tty node is dropped when its cu twin exists`() {
        val ports = listOf(
            SerialPortInfo("tty.usbserial-1410", "USB"),
            SerialPortInfo("cu.usbserial-1410", "USB"),
            SerialPortInfo("tty.Bluetooth-Incoming-Port", "BT"),
            SerialPortInfo("ttyUSB0", "Linux"),
        )

        assertEquals(
            listOf("cu.usbserial-1410", "tty.Bluetooth-Incoming-Port", "ttyUSB0"),
            dedupeMacPorts(ports).map { it.name },
        )
    }
}
