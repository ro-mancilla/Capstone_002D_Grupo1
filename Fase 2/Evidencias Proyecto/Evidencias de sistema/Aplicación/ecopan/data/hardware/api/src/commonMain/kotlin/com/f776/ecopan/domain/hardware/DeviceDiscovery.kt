package com.f776.ecopan.domain.hardware

/** A serial port the OS reports. [name] is what the driver opens; [description] is the friendly label. */
data class SerialPortInfo(val name: String, val description: String) {
    /** `COM3 · Prolific USB-to-Serial`, or just the name when the OS gives no description. */
    val label: String get() = if (description.isBlank()) name else "$name · $description"
}

/**
 * What hardware is physically attached right now, for the Settings -> Dispositivos pickers. Both
 * lookups can block on OS enumeration, so they are `suspend` and run off the main thread. Platforms
 * with no real drivers (Android, iOS) bind an implementation that finds nothing.
 */
interface DeviceDiscovery {
    suspend fun serialPorts(): List<SerialPortInfo>

    /** Names of the OS print services; the chosen one is passed to the `escpos` driver as `printerName`. */
    suspend fun printServices(): List<String>
}

/** [DriverChoice.config] keys the `escpos` printer driver reads. */
object PrinterConfig {
    /** OS print-service name; absent or blank means the OS default printer. */
    const val KEY_NAME = "printerName"
}

/** Ids of the JVM-only drivers that have a Settings configuration panel. */
object DriverIds {
    const val SERIAL_SCALE = "serial"
    const val ESCPOS_PRINTER = "escpos"
}
