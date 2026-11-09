package com.f776.ecopan.domain.hardware

/**
 * Feeds the Settings → Devices picker (04 §6) — [platformOnly] hides JVM-only drivers on mobile.
 * [simulated] marks a driver that pretends to be hardware (the demo scale, the pretend drawer and
 * terminal), so the UI can say "Simulado" instead of implying a device.
 *
 * [none] marks a deliberate "no device" choice ("Sin impresora"): nothing is pretended, so it is
 * never labelled simulated. [viaPrinter] marks a drawer that is opened through the receipt printer,
 * so it is only as real as the printer behind it.
 */
data class DriverDescriptor(
    val id: String,
    val displayName: String,
    val platformOnly: Boolean,
    val simulated: Boolean = false,
    val none: Boolean = false,
    val viaPrinter: Boolean = false,
)
