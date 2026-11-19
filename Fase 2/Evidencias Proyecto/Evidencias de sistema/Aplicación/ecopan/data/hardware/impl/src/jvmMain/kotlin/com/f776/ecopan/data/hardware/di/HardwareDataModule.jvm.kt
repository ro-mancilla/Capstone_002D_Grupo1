package com.f776.ecopan.data.hardware.di

import com.f776.ecopan.data.hardware.DrawerDriverFactory
import com.f776.ecopan.data.hardware.PrinterDriverFactory
import com.f776.ecopan.data.hardware.ScaleDriverFactory
import com.f776.ecopan.data.hardware.jvm.EscPosPrinterDriverFactory
import com.f776.ecopan.data.hardware.jvm.PrinterKickDrawerFactory
import com.f776.ecopan.data.hardware.jvm.SerialScaleDriverFactory
import com.f776.ecopan.data.hardware.jvm.JvmDeviceDiscovery
import com.f776.ecopan.domain.hardware.DeviceDiscovery
import com.f776.ecopan.domain.hardware.DriverRegistry
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Real JVM drivers (`SerialScale` + `ScaleProtocol`, `EscPosPrinter`, `PrinterKickDrawer` -- 04
 * work item 6). APIs verified via context7 against `/fazecast/jserialcomm` and
 * `/anastaciocintra/escpos-coffee` before writing (see `docs/hardware/scale-protocol-notes.md`).
 *
 * HONESTY NOTE: none of these have been exercised against physical hardware in this development
 * environment -- the Phase-0 scale spike (`tools/scale-spike/`) came back NO-GO (no scale or
 * substitute device available). `SerialScale`/`ScaleProtocol` are tested only against a synthetic
 * capture-log replay (`jvmTest`); `ManualScale` remains the primary supported scale mode, and
 * `MockPrinter`/`MockDrawer` remain fully supported fallbacks for the printer/drawer side too.
 *
 * [PrinterKickDrawerFactory] takes a deferred `() -> DriverRegistry` lookup rather than an
 * eagerly-injected `DriverRegistry` to avoid a circular Koin resolution: `DriverRegistryImpl`'s
 * constructor pulls `drawerFactories = getAll<DrawerDriverFactory>()`, which would otherwise try to
 * resolve `DriverRegistry` again mid-construction.
 */
internal actual val PlatformModule = module {
    // Qualified for the same reason as the common factories -- see HardwareDataModule.kt. Without
    // these, the JVM drivers would have *replaced* the mock/manual ones rather than joining them.
    factory<ScaleDriverFactory>(named("scale.serial")) { SerialScaleDriverFactory(get(named("ioDispatcher"))) }
    single<DeviceDiscovery> { JvmDeviceDiscovery(get(named("ioDispatcher"))) }
    factory<PrinterDriverFactory>(named("printer.escpos")) { EscPosPrinterDriverFactory(get(named("ioDispatcher"))) }
    factory<DrawerDriverFactory>(named("drawer.printerkick")) {
        PrinterKickDrawerFactory(
            registryProvider = { get<DriverRegistry>() },
            ioCoroutineScope = get(named("ioCoroutineScope")),
        )
    }
}
