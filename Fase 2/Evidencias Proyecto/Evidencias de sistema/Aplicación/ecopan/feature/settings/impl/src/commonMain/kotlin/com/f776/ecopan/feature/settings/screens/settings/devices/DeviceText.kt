package com.f776.ecopan.feature.settings.screens.settings.devices

import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.WeightReading

/** Which sentence a device card shows under its picker. */
internal enum class DeviceText {
    /** A demo driver: nothing physical happens. */
    Simulated,

    /** A real driver: what it is for. */
    Real,

    /** Drawer opened through the printer, and the printer is real. */
    DrawerViaPrinter,

    /** Drawer opened through the printer, but there is none (or a demo one): nothing will open. */
    DrawerViaPrinterUnavailable,

    /** A demo drawer next to a real printer: says how to make the drawer follow the printer. */
    DrawerSimulatedWithRealPrinter,
}

/**
 * The drawer's sentence depends on the printer, because "Cajón vía impresora" is only as real as
 * the printer behind it. Keeps the printer card, the drawer card and the boleta note telling one story.
 */
internal fun deriveDrawerText(drawer: DriverDescriptor?, printer: DriverDescriptor?): DeviceText {
    val printerIsReal = printer != null && !printer.none && !printer.simulated
    return when {
        drawer == null -> DeviceText.Simulated
        drawer.viaPrinter -> if (printerIsReal) DeviceText.DrawerViaPrinter else DeviceText.DrawerViaPrinterUnavailable
        drawer.simulated -> if (printerIsReal) DeviceText.DrawerSimulatedWithRealPrinter else DeviceText.Simulated
        else -> DeviceText.Real
    }
}

/**
 * The live test as one unit: the value and the text under it come from the same frame. A reading
 * carries the text it was parsed from; only before any reading has arrived does the latest raw text
 * stand alone, which is when a person most needs to see what a scale that cannot be read is sending.
 */
internal fun deriveLiveScale(reading: WeightReading?, lastRawText: String?): LiveScaleUi =
    if (reading == null) {
        LiveScaleUi(grams = null, stable = false, rawText = lastRawText)
    } else {
        LiveScaleUi(grams = reading.grams, stable = reading.stable, rawText = reading.rawText ?: lastRawText)
    }
