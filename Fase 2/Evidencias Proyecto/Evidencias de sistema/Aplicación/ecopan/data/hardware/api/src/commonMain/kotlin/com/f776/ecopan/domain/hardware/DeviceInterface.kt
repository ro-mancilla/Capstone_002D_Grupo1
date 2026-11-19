package com.f776.ecopan.domain.hardware

/**
 * The four driver-backed peripherals (04 §1.2). Deliberately no `SCANNER` entry — keyboard-wedge
 * HID barcode scanners type characters + Enter into whatever has focus; there is no driver to
 * select, so the scanner is not a [DeviceInterface] at all. The home for scanner input is the POS
 * screen's own focus-capturing text field (05), not this module.
 */
enum class DeviceInterface { SCALE, PRINTER, DRAWER, TERMINAL }
