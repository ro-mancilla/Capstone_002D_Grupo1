package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.DrawerDriverFactory
import com.f776.ecopan.data.hardware.PrinterDriverFactory
import com.f776.ecopan.data.hardware.ScaleDriverFactory
import com.f776.ecopan.data.hardware.TerminalDriverFactory
import com.f776.ecopan.data.hardware.di.HardwareDataModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Guards the DI wiring itself, which unit-testing the drivers in isolation cannot: Koin keys
 * definitions by (type, qualifier), so several unqualified `factory<ScaleDriverFactory>`
 * declarations are one key and silently collapse to whichever was declared last. That shipped, and
 * the desktop till died on launch with "no driver registered for fallback id 'manual' either" --
 * `DriverRegistryImpl.factoryFor` cannot fall back to a driver Koin never handed it.
 *
 * These assertions are about *counts and ids surviving registration*, not about driver behaviour.
 */
class HardwareDataModuleTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    /** Stands in for the dispatcher/scope/datastore singles the app module provides. */
    private val testDependencies = module {
        single(named("defaultDispatcher")) { Dispatchers.Default }
        single(named("ioDispatcher")) { Dispatchers.IO }
        single(named("ioCoroutineScope")) { scope }
    }

    private val koin = koinApplication {
        // HardwareDataModule includes DatastoreModule, whose real DataStore single is not needed
        // here: nothing below resolves DriverRegistry itself, only the factory lists it consumes.
        modules(HardwareDataModule, testDependencies)
    }.koin

    @AfterTest
    fun tearDown() {
        koin.close()
    }

    @Test
    fun `every scale driver survives registration, including the manual fallback`() {
        val ids = koin.getAll<ScaleDriverFactory>().map { it.descriptor.id }.toSet()

        assertTrue("manual" in ids, "manual is DriverRegistryImpl's scale fallback; found $ids")
        assertTrue("mock" in ids, "found $ids")
        assertTrue("serial" in ids, "the JVM-only serial driver must join the common set, not replace it; found $ids")
    }

    @Test
    fun `printer, drawer and terminal all keep their mock fallback`() {
        assertTrue("mock" in koin.getAll<PrinterDriverFactory>().map { it.descriptor.id })
        assertTrue("mock" in koin.getAll<DrawerDriverFactory>().map { it.descriptor.id })
        assertTrue("mock" in koin.getAll<TerminalDriverFactory>().map { it.descriptor.id })
    }

    @Test
    fun `JVM printer and drawer drivers join the common set`() {
        assertTrue("escpos" in koin.getAll<PrinterDriverFactory>().map { it.descriptor.id })
        assertTrue(koin.getAll<DrawerDriverFactory>().size >= 2, "mock + printer-kick")
    }
}
