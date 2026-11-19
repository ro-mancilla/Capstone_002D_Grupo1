package com.f776.ecopan.domain.hardware

/** What a person is told about one device, everywhere it is shown (top bar, settings cards, boleta). */
enum class DeviceDisplay {
    /** A demo driver, or a driver whose output lands on one: nothing physical happens. */
    Simulated,

    /** Deliberately no device ("Sin impresora"). Not simulated: nothing is pretended. */
    None,

    /** A real driver that is working (or whose status is not tracked). */
    Ready,

    /** A real driver that is not working. */
    Error,
}

/**
 * Pure mapping from the configured driver and its live [status] to the state shown.
 *
 * @param driver the descriptor of the configured driver; `null` (unknown id) reads as [DeviceDisplay.None].
 * @param status the live status of the running instance, `null` when it is not tracked.
 * @param printer the configured printer, for a [DriverDescriptor.viaPrinter] drawer: a drawer kicked
 * through a simulated or absent printer opens nothing, whatever the drawer's own descriptor says.
 */
fun deriveDeviceDisplay(
    driver: DriverDescriptor?,
    status: DeviceStatus? = null,
    printer: DriverDescriptor? = null,
): DeviceDisplay = when {
    driver == null || driver.none -> DeviceDisplay.None
    driver.simulated -> DeviceDisplay.Simulated
    driver.viaPrinter && (printer == null || printer.none || printer.simulated) -> DeviceDisplay.Simulated
    status == DeviceStatus.ERROR || status == DeviceStatus.DISCONNECTED -> DeviceDisplay.Error
    else -> DeviceDisplay.Ready
}
