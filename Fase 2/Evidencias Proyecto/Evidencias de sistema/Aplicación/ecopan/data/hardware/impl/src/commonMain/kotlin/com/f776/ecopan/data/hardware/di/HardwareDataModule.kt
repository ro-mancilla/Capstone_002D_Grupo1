package com.f776.ecopan.data.hardware.di

import com.f776.ecopan.core.datastore.di.DatastoreModule
import com.f776.ecopan.data.hardware.DriverRegistryImpl
import com.f776.ecopan.data.hardware.DrawerDriverFactory
import com.f776.ecopan.data.hardware.ManualScaleDriverFactory
import com.f776.ecopan.data.hardware.MockDrawerDriverFactory
import com.f776.ecopan.data.hardware.MockPrinterDriverFactory
import com.f776.ecopan.data.hardware.MockScaleDriverFactory
import com.f776.ecopan.data.hardware.PrinterDriverFactory
import com.f776.ecopan.data.hardware.ScaleDriverFactory
import com.f776.ecopan.data.hardware.SimulatedTerminalDriverFactory
import com.f776.ecopan.data.hardware.TerminalDriverFactory
import com.f776.ecopan.domain.hardware.DriverRegistry
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Mock + manual factories are bound here, on every platform (04 §2). [PlatformModule]'s JVM
 * actual additionally binds Serial/EscPos factories (`SerialScale`, `EscPosPrinter`,
 * `PrinterKickDrawer` — APIs verified via context7, see `docs/hardware/scale-protocol-notes.md`);
 * they have not been exercised against physical hardware in this environment (Phase-0 NO-GO), so
 * `ManualScale`/`MockPrinter`/`MockDrawer` remain the primary supported modes even on JVM.
 * Android/iOS actuals bind nothing extra, per 01 §1 platform gating.
 */
val HardwareDataModule = module {
    includes(PlatformModule, DatastoreModule)

    // Every factory needs its OWN qualifier. Koin keys definitions by (type, qualifier), so two
    // unqualified `factory<ScaleDriverFactory>` declarations are the *same* key -- the second
    // silently overrides the first and `getAll()` returns a single element. That is not
    // theoretical: it shipped, and the desktop till crashed on launch with "no driver registered
    // for fallback id 'manual' either" because the surviving definition was whichever was declared
    // last. `DriverDescriptor.id` is deliberately reused as the qualifier so a new driver cannot be
    // added without picking a unique one.
    factory<ScaleDriverFactory>(named("scale.mock")) { MockScaleDriverFactory(get(named("defaultDispatcher")), get(named("ioDispatcher"))) }
    factory<ScaleDriverFactory>(named("scale.manual")) { ManualScaleDriverFactory() }
    factory<PrinterDriverFactory>(named("printer.mock")) { MockPrinterDriverFactory() }
    factory<DrawerDriverFactory>(named("drawer.mock")) { MockDrawerDriverFactory() }
    factory<TerminalDriverFactory>(named("terminal.mock")) { SimulatedTerminalDriverFactory() }

    single<DriverRegistry> {
        DriverRegistryImpl(
            dataStore = get(named("datastore")),
            scaleFactories = getAll(),
            printerFactories = getAll(),
            drawerFactories = getAll(),
            terminalFactories = getAll(),
            ioCoroutineScope = get(named("ioCoroutineScope")),
        )
    }
}

internal expect val PlatformModule: Module
