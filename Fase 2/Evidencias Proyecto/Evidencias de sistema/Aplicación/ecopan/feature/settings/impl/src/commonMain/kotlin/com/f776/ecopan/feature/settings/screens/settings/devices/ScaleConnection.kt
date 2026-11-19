package com.f776.ecopan.feature.settings.screens.settings.devices

import com.f776.ecopan.domain.hardware.DeviceStatus

/** The state chip of the scale card (copy deck 4c). One value per thing a person can be told. */
internal enum class ScaleConnection {
    /** Manual entry: there is no device to be connected to, so no chip. */
    Manual,

    /** Just applied: waiting for the first reading (up to 10 s). */
    Connecting,

    /** Readings are arriving. */
    Responding,

    /** The port opened but the scale sent nothing (or went quiet). */
    NoData,

    /** The port could not be opened, usually because another program holds it. */
    PortBusy,

    /** The computer sees no serial ports at all. */
    NoPorts,

    /** Demonstration readings; never hardware. */
    Simulated,
}

/**
 * Pure derivation of the chip, so every state is testable without a scale.
 *
 * @param waitingForFirstReading true during the window after a driver was (re)started.
 * @param hasReading a reading has arrived from the current driver instance.
 * @param noPorts the last scan found nothing.
 */
internal fun deriveScaleConnection(
    mode: ScaleModeOption,
    status: DeviceStatus,
    hasReading: Boolean,
    waitingForFirstReading: Boolean,
    noPorts: Boolean,
): ScaleConnection = when (mode) {
    ScaleModeOption.Manual -> ScaleConnection.Manual
    ScaleModeOption.Simulated -> ScaleConnection.Simulated
    ScaleModeOption.Serial -> when {
        noPorts && status != DeviceStatus.CONNECTED -> ScaleConnection.NoPorts
        status == DeviceStatus.ERROR -> ScaleConnection.PortBusy
        hasReading && status == DeviceStatus.CONNECTED -> ScaleConnection.Responding
        waitingForFirstReading -> ScaleConnection.Connecting
        else -> ScaleConnection.NoData
    }
}
