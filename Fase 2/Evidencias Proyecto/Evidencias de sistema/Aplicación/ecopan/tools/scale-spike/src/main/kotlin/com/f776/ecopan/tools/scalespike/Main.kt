package com.f776.ecopan.tools.scalespike

import com.fazecast.jSerialComm.SerialPort
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase-0 scale spike harness (plan `04-hardware.md` §4, work item 1).
 *
 * Standalone, throwaway-friendly plain-JVM tool -- deliberately outside the KMP module graph
 * (`:tools:scale-spike`, plain `org.jetbrains.kotlin.jvm`, jSerialComm only, no Koin/Compose/app).
 * Its job is to answer one question before any POS screen exists: can a real serial scale be read
 * at all? The learnings (a working `(port, baud, framing, protocol-id, regex, unit factor)` tuple
 * and a raw capture log) migrate into `:data:hardware:impl` -- this tool itself is not shipped.
 *
 * HONESTY NOTE: this harness is real, runnable code, but it has never been run against physical
 * hardware in this development environment (no serial scale or substitute device was available).
 * See `docs/hardware/scale-protocol-notes.md` for the resulting NO-GO verdict and its consequences
 * (`ManualScale` as the primary POS scale mode). Anyone with hardware in hand should be able to run
 * this exactly as written and get real enumerate/dump/poll output.
 *
 * Usage:
 *   ./gradlew :tools:scale-spike:run --args="list"
 *   ./gradlew :tools:scale-spike:run --args="dump <systemPortName> [baud] [framing]"
 *
 * `list` enumerates all comm ports (step 1 of the harness). `dump` opens the named port at the
 * given baud (default 9600), prints raw hex+ASCII as bytes arrive, writes a timestamped capture
 * log file, and -- if nothing arrives within a few seconds -- sends the common poll bytes the plan
 * calls out (`W\r`, `P\r`, ENQ 0x05) and keeps listening (step 2). `framing` is data bits, parity
 * (N/E/O) and stop bits -- `8N1` (default), `7E1`, `8E1`, `7O1`, `8N2`. Run it once per candidate baud
 * (9600, 4800, 19200, 38400, 115200) and framing (8N1, 7E1) per the plan's iteration order.
 */
fun main(args: Array<String>) {
    when (args.getOrNull(0)) {
        "list" -> listPorts()
        "dump" -> {
            val portName = args.getOrNull(1)
            if (portName == null) {
                println("Usage: dump <systemPortName> [baud] [framing]")
                return
            }
            val baud = args.getOrNull(2)?.toIntOrNull() ?: 9600
            val framingArg = args.getOrNull(3) ?: "8N1"
            val framing = Framing.parse(framingArg)
            if (framing == null) {
                println("Unknown framing '$framingArg'. Use <5-8 data bits><N|E|O><1|2>, e.g. 8N1 or 7E1.")
                return
            }
            dumpPort(portName, baud, framing)
        }
        else -> {
            println("Usage:")
            println("  list                          -- enumerate comm ports")
            println("  dump <systemPortName> [baud] [framing]  -- open a port and dump raw hex/ASCII (framing default 8N1)")
            println()
            listPorts()
        }
    }
}

/** Harness step 1: enumerate ports; plug/unplug the scale to identify its port. */
private fun listPorts() {
    val ports = SerialPort.getCommPorts()
    if (ports.isEmpty()) {
        println("No comm ports found.")
        return
    }
    println("Found ${ports.size} comm port(s):")
    ports.forEach { port ->
        println("  systemPortName=${port.systemPortName}  descriptivePortName=${port.descriptivePortName}")
    }
}

/** Harness step 2: dump raw bytes at a candidate baud; send poll bytes if the line is silent. */
private fun dumpPort(portName: String, baud: Int, framing: Framing) {
    val port = SerialPort.getCommPorts().find { it.systemPortName == portName }
    if (port == null) {
        println("Port '$portName' not found. Run 'list' first.")
        return
    }

    port.setComPortParameters(baud, framing.dataBits, framing.stopBits, framing.parity)
    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0)

    if (!port.openPort()) {
        println("Failed to open port '$portName' at $baud baud ${framing.label}.")
        return
    }
    println("Opened '$portName' at $baud baud, ${framing.label}. Reading for up to $SESSION_SECONDS s...")

    val logFile = File("scale-spike-capture-${timestampForFilename()}.log")
    logFile.appendText("# scale-spike capture -- port=$portName baud=$baud framing=${framing.label}\n")

    val started = System.currentTimeMillis()
    var sentPoll = false
    var sawAnyData = false

    try {
        while (System.currentTimeMillis() - started < SESSION_SECONDS * 1000L) {
            val available = port.bytesAvailable()
            if (available > 0) {
                sawAnyData = true
                val buffer = ByteArray(available)
                val numRead = port.readBytes(buffer, buffer.size)
                if (numRead > 0) {
                    logAndPrint(logFile, buffer.copyOf(numRead))
                }
            } else if (!sentPoll && System.currentTimeMillis() - started > SILENCE_BEFORE_POLL_MS) {
                println("No data yet -- sending common poll bytes (W\\r, P\\r, ENQ)...")
                sendPollBytes(port)
                sentPoll = true
            }
        }
    } finally {
        port.closePort()
    }

    if (!sawAnyData) {
        println("No readable bytes observed in $SESSION_SECONDS s. See log: ${logFile.path}")
    } else {
        println("Capture complete. Log written to: ${logFile.path}")
    }
}

private fun sendPollBytes(port: SerialPort) {
    listOf(
        "W\r".toByteArray(Charsets.US_ASCII),
        "P\r".toByteArray(Charsets.US_ASCII),
        byteArrayOf(0x05), // ENQ
    ).forEach { cmd -> port.writeBytes(cmd, cmd.size) }
}

private fun logAndPrint(logFile: File, bytes: ByteArray) {
    val hex = bytes.joinToString(" ") { "%02X".format(it) }
    val ascii = bytes.joinToString("") { b -> if (b in 0x20..0x7e) b.toInt().toChar().toString() else "." }
    val line = "${nowIso()}\tHEX=$hex\tASCII=$ascii"
    println(line)
    logFile.appendText("$line\n")
}

private fun nowIso(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

private fun timestampForFilename(): String =
    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

private const val READ_TIMEOUT_MS = 1000
private const val SESSION_SECONDS = 30
private const val SILENCE_BEFORE_POLL_MS = 3000L

/** Serial line framing parsed from the conventional shorthand (`8N1`, `7E1`, ...). */
private class Framing(val label: String, val dataBits: Int, val parity: Int, val stopBits: Int) {
    companion object {
        fun parse(raw: String): Framing? {
            val match = Regex("^([5-8])([NEOneo])([12])$").matchEntire(raw.trim()) ?: return null
            val parity = when (match.groupValues[2].uppercase()) {
                "E" -> SerialPort.EVEN_PARITY
                "O" -> SerialPort.ODD_PARITY
                else -> SerialPort.NO_PARITY
            }
            val stopBits = if (match.groupValues[3] == "2") SerialPort.TWO_STOP_BITS else SerialPort.ONE_STOP_BIT
            return Framing(raw.trim().uppercase(), match.groupValues[1].toInt(), parity, stopBits)
        }
    }
}
