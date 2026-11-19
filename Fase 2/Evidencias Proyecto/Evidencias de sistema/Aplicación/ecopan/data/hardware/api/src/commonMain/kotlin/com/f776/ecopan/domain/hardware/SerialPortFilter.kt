package com.f776.ecopan.domain.hardware

/**
 * Ports that are never a scale. macOS lists `cu.debug-console`, `cu.Bluetooth-Incoming-Port`,
 * `cu.wlan-debug` and similar system endpoints next to real adapters; none has a scale on it.
 */
private val SYSTEM_PORT_MARKERS = listOf("debug-console", "bluetooth-incoming-port", "wlan-debug", "blth")

/** Linux/BSD onboard UARTs (`ttyS0`..): usually phantom on current hardware, so never the default. */
private val ONBOARD_UART = Regex("^(tty)?S\\d+$", RegexOption.IGNORE_CASE)

private fun SerialPortInfo.isSystemEndpoint(): Boolean {
    val lower = name.lowercase()
    return SYSTEM_PORT_MARKERS.any { it in lower }
}

/**
 * The ports worth showing a person, in the order given:
 * - macOS system/debug endpoints are dropped;
 * - macOS lists every adapter twice, `tty.usbserial-X` (waits for carrier detect) and `cu.usbserial-X`
 *   (opens straight away); only the `cu.` one is kept, which is the one to open for a scale.
 *
 * Windows `COMn` and Linux `ttyUSBn`/`ttyACMn` pass through untouched.
 */
fun List<SerialPortInfo>.userPorts(): List<SerialPortInfo> {
    val cuSuffixes = filter { it.name.startsWith("cu.") }.map { it.name.removePrefix("cu.") }.toSet()
    return filterNot { it.isSystemEndpoint() }
        .filterNot { it.name.startsWith("tty.") && it.name.removePrefix("tty.") in cuSuffixes }
}

/**
 * The port to preselect when the person picks "Balanza serial": the first [userPorts] entry that is
 * not an onboard UART, else the first remaining one, else `null` (leave the field empty rather than
 * point the scale at a system port).
 */
fun List<SerialPortInfo>.defaultScalePort(): SerialPortInfo? {
    val candidates = userPorts()
    return candidates.firstOrNull { !ONBOARD_UART.matches(it.name) } ?: candidates.firstOrNull()
}
