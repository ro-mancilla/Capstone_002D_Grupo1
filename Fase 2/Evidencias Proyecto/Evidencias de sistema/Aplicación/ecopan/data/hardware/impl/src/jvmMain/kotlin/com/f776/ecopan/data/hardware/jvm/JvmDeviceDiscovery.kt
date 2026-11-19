package com.f776.ecopan.data.hardware.jvm

import com.f776.ecopan.domain.hardware.DeviceDiscovery
import com.f776.ecopan.domain.hardware.SerialPortInfo
import com.github.anastaciocintra.output.PrinterOutputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Enumerates real ports (jSerialComm) and OS print services (escpos-coffee) for the Settings pickers. */
internal class JvmDeviceDiscovery(
    private val ioDispatcher: CoroutineDispatcher,
    private val ports: SerialPortProvider = JSerialCommPortProvider,
) : DeviceDiscovery {

    override suspend fun serialPorts(): List<SerialPortInfo> = withContext(ioDispatcher) { ports.listPorts() }

    override suspend fun printServices(): List<String> = withContext(ioDispatcher) {
        PrinterOutputStream.getListPrintServicesNames().toList()
    }
}
