package com.f776.ecopan.domain.hardware

data class DeviceProfile(
    val scale: DriverChoice,
    val printer: DriverChoice,
    val drawer: DriverChoice,
    val terminal: DriverChoice,
)
