package com.f776.ecopan.data.hardware.jvm

import com.f776.ecopan.data.hardware.DrawerDriverFactory
import com.f776.ecopan.data.hardware.PrinterDriverFactory
import com.f776.ecopan.data.hardware.ScaleDriverFactory
import com.f776.ecopan.domain.hardware.CashDrawer
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.DriverRegistry
import com.f776.ecopan.domain.hardware.ReceiptPrinter
import com.f776.ecopan.domain.hardware.Scale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope

/**
 * Real JVM driver factories (04 §2 matrix, work item 6) -- bound only by the JVM `PlatformModule`
 * actual, exactly as 04 §1.2 specifies ("JVM `PlatformModule` additionally binds Serial/EscPos
 * factories; Android/iOS actuals bind nothing extra"). [DriverDescriptor.platformOnly] = true so
 * the Settings → Devices picker (04 §6) hides these on mobile without any platform `if` in the UI.
 */
internal class SerialScaleDriverFactory(private val ioDispatcher: CoroutineDispatcher) : ScaleDriverFactory {
    override val descriptor =
        DriverDescriptor(id = "serial", displayName = "Balanza serial (jSerialComm)", platformOnly = true)

    override fun create(config: Map<String, String>): Scale = SerialScale(config, ioDispatcher)
}

internal class EscPosPrinterDriverFactory(private val ioDispatcher: CoroutineDispatcher) : PrinterDriverFactory {
    override val descriptor =
        DriverDescriptor(id = "escpos", displayName = "Impresora ESC/POS", platformOnly = true)

    override fun create(config: Map<String, String>): ReceiptPrinter = EscPosPrinter(config, ioDispatcher)
}

internal class PrinterKickDrawerFactory(
    private val registryProvider: () -> DriverRegistry,
    private val ioCoroutineScope: CoroutineScope,
) : DrawerDriverFactory {
    override val descriptor =
        DriverDescriptor(id = "printer-kick", displayName = "Cajón vía impresora (RJ11)", platformOnly = true, viaPrinter = true)

    override fun create(config: Map<String, String>): CashDrawer =
        PrinterKickDrawer(registryProvider, ioCoroutineScope)
}
