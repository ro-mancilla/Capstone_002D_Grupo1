package com.f776.ecopan.data.hardware.jvm

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortDataListener
import com.fazecast.jSerialComm.SerialPortEvent
import com.f776.ecopan.domain.hardware.SerialFraming
import com.f776.ecopan.domain.hardware.SerialPortInfo

/** Line parameters a [SerialScale] asks the port to be opened with. */
internal data class SerialLineParams(val baud: Int, val framing: SerialFraming)

internal interface SerialPortListener {
    fun onData(bytes: ByteArray)
    fun onDisconnected()
}

internal interface SerialConnection : AutoCloseable {
    fun write(bytes: ByteArray)
}

/**
 * The seam between [SerialScale] and jSerialComm: the scale decides *which* port and *how* it is
 * configured, this opens it. It exists so the one thing that can be proven without a physical scale
 * -- that the settings reach the port -- can be proven with a fake (`SerialScaleTest`).
 */
internal interface SerialPortProvider {
    fun listPorts(): List<SerialPortInfo>

    /** `null` when [portName] is not present or the OS refuses to open it. */
    fun open(portName: String, params: SerialLineParams, listener: SerialPortListener): SerialConnection?
}

/** API names verified via context7 against `/fazecast/jserialcomm`. */
internal object JSerialCommPortProvider : SerialPortProvider {

    private const val READ_TIMEOUT_MS = 1000

    override fun listPorts(): List<SerialPortInfo> =
        dedupeMacPorts(SerialPort.getCommPorts().map { SerialPortInfo(name = it.systemPortName, description = it.descriptivePortName) })

    override fun open(portName: String, params: SerialLineParams, listener: SerialPortListener): SerialConnection? {
        val port = SerialPort.getCommPorts().find { it.systemPortName == portName } ?: return null
        var handedOver = false
        try {
            val framing = params.framing
            val configured = port.setComPortParameters(
                params.baud,
                framing.dataBits,
                if (framing.stopBits == 2) SerialPort.TWO_STOP_BITS else SerialPort.ONE_STOP_BIT,
                when (framing.parity) {
                    SerialFraming.Parity.NONE -> SerialPort.NO_PARITY
                    SerialFraming.Parity.ODD -> SerialPort.ODD_PARITY
                    SerialFraming.Parity.EVEN -> SerialPort.EVEN_PARITY
                },
            )
            // A driver that rejects the line parameters would otherwise "connect" and then read
            // garbage at the wrong settings; report it as a failed open instead.
            if (!configured) return null
            if (!port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0)) return null
            if (!port.openPort()) return null

            val registered = port.addDataListener(object : SerialPortDataListener {
                override fun getListeningEvents(): Int =
                    SerialPort.LISTENING_EVENT_DATA_AVAILABLE or SerialPort.LISTENING_EVENT_PORT_DISCONNECTED

                override fun serialEvent(event: SerialPortEvent) {
                    if (event.eventType and SerialPort.LISTENING_EVENT_PORT_DISCONNECTED != 0) {
                        listener.onDisconnected()
                        return
                    }
                    val available = port.bytesAvailable()
                    if (available <= 0) return
                    val buffer = ByteArray(available)
                    val numRead = port.readBytes(buffer, buffer.size)
                    if (numRead > 0) listener.onData(if (numRead == buffer.size) buffer else buffer.copyOf(numRead))
                }
            })
            if (!registered) return null

            handedOver = true
            return object : SerialConnection {
                override fun write(bytes: ByteArray) {
                    port.writeBytes(bytes, bytes.size)
                }

                override fun close() {
                    port.removeDataListener()
                    port.closePort()
                }
            }
        } finally {
            // Any early return or exception above must not leave the OS handle open, or the next
            // attempt (and the next driver instance) could never claim the port.
            if (!handedOver) runCatching { port.closePort() }
        }
    }
}

/**
 * macOS lists every USB serial device twice: `cu.usbserial-X` (the call-out node, what an app
 * should open) and `tty.usbserial-X` (blocks until carrier detect). Showing both doubles the picker
 * and invites choosing the wrong one, so a `tty.` entry is dropped when its `cu.` twin exists.
 */
internal fun dedupeMacPorts(ports: List<SerialPortInfo>): List<SerialPortInfo> {
    val names = ports.map { it.name }.toSet()
    return ports.filterNot { it.name.startsWith("tty.") && "cu.${it.name.removePrefix("tty.")}" in names }
}
