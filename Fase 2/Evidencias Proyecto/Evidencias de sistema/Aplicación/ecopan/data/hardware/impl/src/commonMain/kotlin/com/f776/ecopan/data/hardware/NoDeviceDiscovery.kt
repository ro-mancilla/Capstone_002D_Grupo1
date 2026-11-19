package com.f776.ecopan.data.hardware

import com.f776.ecopan.domain.hardware.DeviceDiscovery
import com.f776.ecopan.domain.hardware.SerialPortInfo

/** Android and iOS have no serial or ESC/POS drivers (01 §1), so there is nothing to discover. */
internal object NoDeviceDiscovery : DeviceDiscovery {
    override suspend fun serialPorts(): List<SerialPortInfo> = emptyList()
    override suspend fun printServices(): List<String> = emptyList()
}
