package com.f776.ecopan.feature.settings.unittest.fake

import com.f776.ecopan.domain.hardware.DeviceDiscovery
import com.f776.ecopan.domain.hardware.SerialPortInfo

class FakeDeviceDiscovery(
    var ports: List<SerialPortInfo> = emptyList(),
    var services: List<String> = emptyList(),
    var portsError: Throwable? = null,
    var servicesError: Throwable? = null,
) : DeviceDiscovery {
    /** How many times the ports were looked up. */
    var scans = 0
        private set

    override suspend fun serialPorts(): List<SerialPortInfo> {
        scans++
        return portsError?.let { throw it } ?: ports
    }
    override suspend fun printServices(): List<String> = servicesError?.let { throw it } ?: services
}
