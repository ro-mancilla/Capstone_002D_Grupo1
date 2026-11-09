package com.f776.ecopan.data.hardware

import com.f776.ecopan.domain.hardware.CashDrawer
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.PaymentTerminal
import com.f776.ecopan.domain.hardware.ReceiptPrinter
import com.f776.ecopan.domain.hardware.Scale

/** Common shape so [DriverRegistryImpl] can look factories up by id generically. */
internal interface DriverFactory {
    val descriptor: DriverDescriptor
}

internal interface ScaleDriverFactory : DriverFactory {
    fun create(config: Map<String, String>): Scale
}

internal interface PrinterDriverFactory : DriverFactory {
    fun create(config: Map<String, String>): ReceiptPrinter
}

internal interface DrawerDriverFactory : DriverFactory {
    fun create(config: Map<String, String>): CashDrawer
}

internal interface TerminalDriverFactory : DriverFactory {
    fun create(config: Map<String, String>): PaymentTerminal
}
