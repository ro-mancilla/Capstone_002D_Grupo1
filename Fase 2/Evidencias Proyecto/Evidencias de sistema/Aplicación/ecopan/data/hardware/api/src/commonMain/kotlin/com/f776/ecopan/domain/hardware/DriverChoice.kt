package com.f776.ecopan.domain.hardware

/** [config] carries driver-specific settings (e.g. serial port name, baud) as plain strings. */
data class DriverChoice(val driverId: String, val config: Map<String, String> = emptyMap())
