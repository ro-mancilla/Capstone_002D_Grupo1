package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.jvm.SerialConnection
import com.f776.ecopan.data.hardware.jvm.SerialLineParams
import com.f776.ecopan.data.hardware.jvm.SerialPortListener
import com.f776.ecopan.data.hardware.jvm.SerialPortProvider
import com.f776.ecopan.data.hardware.jvm.SerialScale
import com.f776.ecopan.domain.hardware.DeviceStatus
import com.f776.ecopan.domain.hardware.ScaleProtocolKind
import com.f776.ecopan.domain.hardware.SerialFraming
import com.f776.ecopan.domain.hardware.SerialPortInfo
import com.f776.ecopan.domain.hardware.SerialScaleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class SerialScaleTest {

    @Test
    fun `opens the configured port with the configured baud and framing`() {
        val ports = FakeSerialPortProvider()
        val settings = SerialScaleSettings(port = "ttyUSB0", baud = 4800, framing = SerialFraming.SEVEN_E_ONE)
        val scale = SerialScale(settings.toConfig(), Dispatchers.Unconfined, ports)

        scale.start()

        assertEquals("ttyUSB0", ports.openedPort)
        assertEquals(SerialLineParams(4800, SerialFraming.SEVEN_E_ONE), ports.openedParams)
        assertEquals(DeviceStatus.CONNECTED, scale.status.value)
        scale.close()
    }

    @Test
    fun `an unknown port leaves the scale in error instead of opening anything`() {
        val ports = FakeSerialPortProvider(available = false)
        val scale = SerialScale(SerialScaleSettings(port = "COM9").toConfig(), Dispatchers.Unconfined, ports)

        scale.start()

        assertEquals(DeviceStatus.ERROR, scale.status.value)
        scale.close()
    }

    @Test
    fun `bytes from the port are parsed with the configured frame regex and unit factors`() = runTest {
        val ports = FakeSerialPortProvider()
        val settings = SerialScaleSettings(
            port = "ttyUSB0",
            delimiter = "\\r",
            frameRegex = "^(?<value>[0-9]+)(?<unit>dag)$",
            unitFactors = "dag=10",
        )
        val scale = SerialScale(settings.toConfig(), Dispatchers.Unconfined, ports)
        scale.start()

        ports.listener.onData("084dag\r".encodeToByteArray())

        assertEquals(840L, scale.readings.first().grams)
        scale.close()
    }

    @Test
    fun `the poll command is written to the port using its unescaped bytes`() {
        val ports = FakeSerialPortProvider()
        val settings = SerialScaleSettings(
            port = "ttyUSB0",
            protocol = ScaleProtocolKind.POLL_GENERIC,
            pollCommand = "P\\x05",
        )
        val scale = SerialScale(settings.toConfig(), Dispatchers.Unconfined, ports)

        scale.start()

        assertEquals(listOf("P\u0005"), ports.written)
        scale.close()
    }

    @Test
    fun `an invalid regex that slipped past the UI errors the driver instead of throwing`() {
        val ports = FakeSerialPortProvider()
        val scale = SerialScale(SerialScaleSettings(port = "ttyUSB0", frameRegex = "([").toConfig(), Dispatchers.Unconfined, ports)

        scale.start()

        assertEquals(DeviceStatus.ERROR, scale.status.value)
        assertEquals(null, ports.openedPort)
        scale.close()
    }

    @Test
    fun `close closes the connection`() {
        val ports = FakeSerialPortProvider()
        val scale = SerialScale(SerialScaleSettings(port = "ttyUSB0").toConfig(), Dispatchers.Unconfined, ports)
        scale.start()

        scale.close()

        assertTrue(ports.closed)
        assertEquals(DeviceStatus.DISCONNECTED, scale.status.value)
    }

    @Test
    fun `a blank port goes straight to error without trying to open anything`() {
        val ports = FakeSerialPortProvider()
        val scale = SerialScale(SerialScaleSettings().toConfig(), Dispatchers.Unconfined, ports)

        scale.start()

        assertEquals(DeviceStatus.ERROR, scale.status.value)
        assertEquals(0, ports.openCount)
        scale.close()
    }

    @Test
    fun `an unplugged port is closed and reopened`() {
        val ports = FakeSerialPortProvider()
        val scale = SerialScale(SerialScaleSettings(port = "ttyUSB0").toConfig(), Dispatchers.Unconfined, ports)
        scale.start()

        ports.listener.onDisconnected()

        assertEquals(1, ports.closeCount, "the dead handle is released")
        assertEquals(2, ports.openCount, "recovery reopens the port")
        assertEquals(DeviceStatus.CONNECTED, scale.status.value)
        scale.close()
    }

    @Test
    fun `closing during a slow open does not leak the port`() {
        val enteredOpen = CountDownLatch(1)
        val releaseOpen = CountDownLatch(1)
        val ports = FakeSerialPortProvider(onOpen = {
            enteredOpen.countDown()
            releaseOpen.await(5, TimeUnit.SECONDS)
        })
        val scale = SerialScale(SerialScaleSettings(port = "ttyUSB0").toConfig(), Dispatchers.Default, ports)
        scale.start()
        assertTrue(enteredOpen.await(5, TimeUnit.SECONDS))

        scale.close()
        releaseOpen.countDown()

        val deadline = System.currentTimeMillis() + 5_000
        while (ports.closeCount == 0 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(1, ports.closeCount, "the connection that finished opening after close() is closed")
        assertEquals(DeviceStatus.DISCONNECTED, scale.status.value)
    }

    @Test
    fun `a flood of bytes with no delimiter does not wedge the parser`() = runTest {
        val ports = FakeSerialPortProvider()
        val scale = SerialScale(SerialScaleSettings(port = "ttyUSB0").toConfig(), Dispatchers.Unconfined, ports)
        scale.start()

        repeat(50) { ports.listener.onData(ByteArray(1024) { 'x'.code.toByte() }) }
        ports.listener.onData("\r\nST,GS, 1.234 kg\r\n".encodeToByteArray())

        assertEquals(1234L, scale.readings.first().grams)
        scale.close()
    }

    @Test
    fun `a catastrophic user regex cannot hang the data thread`() {
        val ports = FakeSerialPortProvider()
        val settings = SerialScaleSettings(port = "ttyUSB0", frameRegex = "^(?<value>(\\2x|x)+y)(?<unit>z)")
        val scale = SerialScale(settings.toConfig(), Dispatchers.Unconfined, ports)
        scale.start()

        val started = System.currentTimeMillis()
        ports.listener.onData(("x".repeat(64) + "\r\n").encodeToByteArray())

        assertTrue(System.currentTimeMillis() - started < 3_000)
        scale.close()
    }

    private class FakeSerialPortProvider(
        private val available: Boolean = true,
        private val onOpen: () -> Unit = {},
    ) : SerialPortProvider {
        var openedPort: String? = null
        var openedParams: SerialLineParams? = null
        lateinit var listener: SerialPortListener
        val written = mutableListOf<String>()
        @Volatile var closeCount = 0
        @Volatile var openCount = 0
        val closed get() = closeCount > 0

        override fun listPorts(): List<SerialPortInfo> = emptyList()

        override fun open(portName: String, params: SerialLineParams, listener: SerialPortListener): SerialConnection? {
            if (!available) return null
            onOpen()
            openCount++
            openedPort = portName
            openedParams = params
            this.listener = listener
            return object : SerialConnection {
                override fun write(bytes: ByteArray) {
                    written += bytes.decodeToString()
                }

                override fun close() {
                    closeCount++
                }
            }
        }
    }
}
