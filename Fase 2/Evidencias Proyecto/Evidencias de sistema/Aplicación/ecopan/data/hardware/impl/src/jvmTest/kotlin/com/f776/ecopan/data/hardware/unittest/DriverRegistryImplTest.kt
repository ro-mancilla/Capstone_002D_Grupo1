@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.hardware.unittest

import com.f776.ecopan.data.hardware.DriverRegistryImpl
import com.f776.ecopan.data.hardware.ManualScaleDriverFactory
import com.f776.ecopan.data.hardware.MockDrawerDriverFactory
import com.f776.ecopan.data.hardware.MockPrinterDriverFactory
import com.f776.ecopan.data.hardware.MockScaleDriverFactory
import com.f776.ecopan.data.hardware.SimulatedTerminalDriverFactory
import com.f776.ecopan.data.hardware.unittest.fake.FakePreferencesDataStore
import com.f776.ecopan.data.hardware.unittest.fake.FakeScale
import com.f776.ecopan.data.hardware.unittest.fake.FakeScaleDriverFactory
import com.f776.ecopan.domain.hardware.DeviceInterface
import com.f776.ecopan.data.hardware.PrinterDriverFactory
import com.f776.ecopan.domain.hardware.DriverChoice
import com.f776.ecopan.domain.hardware.DriverDescriptor
import com.f776.ecopan.domain.hardware.ReceiptPrinter
import com.f776.ecopan.domain.hardware.SerialFraming
import com.f776.ecopan.domain.hardware.SerialScaleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class DriverRegistryImplTest {

    @Test
    fun `defaults to manual scale and mock printer, drawer, terminal`() = runTest {
        val registry = DriverRegistryImpl(
            dataStore = FakePreferencesDataStore(),
            scaleFactories = listOf(MockScaleDriverFactory(Dispatchers.Default, Dispatchers.Default), ManualScaleDriverFactory()),
            printerFactories = listOf(MockPrinterDriverFactory()),
            drawerFactories = listOf(MockDrawerDriverFactory()),
            terminalFactories = listOf(SimulatedTerminalDriverFactory()),
            ioCoroutineScope = backgroundScope,
        )

        val profile = registry.profile.first()

        assertEquals("manual", profile.scale.driverId)
        assertEquals("mock", profile.printer.driverId)
        assertEquals("mock", profile.drawer.driverId)
        assertEquals("mock", profile.terminal.driverId)
    }

    @Test
    fun `selecting a new scale driver closes the previous instance and emits a fresh one`() = runTest {
        // Dispatchers.Unconfined runs everything inline/synchronously, so the collector below is
        // guaranteed to have received the first (default) instance before `select` is called, and
        // `select`'s resulting swap is guaranteed to have propagated before it returns -- avoiding
        // any ambiguity about how the shared virtual-time test scheduler orders a `shareIn`'d hot
        // flow's background collection relative to `advanceUntilIdle()`.
        val ioScope = CoroutineScope(Dispatchers.Unconfined + Job())
        val registry = DriverRegistryImpl(
            dataStore = FakePreferencesDataStore(),
            scaleFactories = listOf(FakeScaleDriverFactory("manual"), FakeScaleDriverFactory("mock")),
            printerFactories = emptyList(),
            drawerFactories = emptyList(),
            terminalFactories = emptyList(),
            ioCoroutineScope = ioScope,
        )
        val instances = mutableListOf<FakeScale>()
        val job = ioScope.launch { registry.scale.collect { instances += it as FakeScale } }

        registry.select(DeviceInterface.SCALE, DriverChoice("mock"))

        assertEquals(2, instances.size)
        assertTrue(instances[0].closed, "the previous instance should have been closed on swap")
        assertFalse(instances[1].closed)
        assertTrue(instances[1].started)
        job.cancel()
        ioScope.cancel()
    }

    @Test
    fun `an unknown scale driverId falls back to the default instead of crashing`() = runTest {
        val registry = DriverRegistryImpl(
            dataStore = FakePreferencesDataStore(),
            scaleFactories = listOf(FakeScaleDriverFactory("manual")),
            printerFactories = emptyList(),
            drawerFactories = emptyList(),
            terminalFactories = emptyList(),
            ioCoroutineScope = backgroundScope,
        )

        registry.select(DeviceInterface.SCALE, DriverChoice("serial"))
        val scale = registry.scale.first() as FakeScale

        assertEquals("manual", scale.driverId)
    }

    @Test
    fun `serial settings are persisted under hw scale serial and reach the scale factory`() = runTest {
        val dataStore = FakePreferencesDataStore()
        val registry = registryWith(dataStore, scaleIds = listOf("manual", "serial"))
        val settings = SerialScaleSettings(port = "ttyUSB0", baud = 4800, framing = SerialFraming.SEVEN_E_ONE)

        registry.select(DeviceInterface.SCALE, DriverChoice("serial", settings.toConfig()))

        val stored = dataStore.data.first().asMap().mapKeys { it.key.name }
        assertEquals("ttyUSB0", stored["hw.scale.serial.port"])
        assertEquals("4800", stored["hw.scale.serial.baud"])
        assertEquals("7E1", stored["hw.scale.serial.framing"])
        assertEquals(settings, SerialScaleSettings.fromConfig(registry.profile.first().scale.config))
        val scale = registry.scale.first() as FakeScale
        assertEquals("serial", scale.driverId)
        assertEquals(settings.toConfig(), scale.config.filterKeys { it in SerialScaleSettings.CONFIG_KEYS })
    }

    @Test
    fun `reselecting a driver without config keeps the stored serial settings`() = runTest {
        val registry = registryWith(FakePreferencesDataStore(), scaleIds = listOf("manual", "serial"))
        val settings = SerialScaleSettings(port = "COM3")
        registry.select(DeviceInterface.SCALE, DriverChoice("serial", settings.toConfig()))

        registry.select(DeviceInterface.SCALE, DriverChoice("manual"))
        registry.select(DeviceInterface.SCALE, DriverChoice("serial"))

        assertEquals(settings, SerialScaleSettings.fromConfig(registry.profile.first().scale.config))
    }

    @Test
    fun `applying changed serial settings restarts the driver`() = runTest {
        val ioScope = CoroutineScope(Dispatchers.Unconfined + Job())
        val registry = registryWith(FakePreferencesDataStore(), scaleIds = listOf("manual", "serial"), scope = ioScope)
        registry.select(DeviceInterface.SCALE, DriverChoice("serial", SerialScaleSettings(port = "COM3").toConfig()))
        val instances = mutableListOf<FakeScale>()
        val job = ioScope.launch { registry.scale.collect { instances += it as FakeScale } }

        registry.select(DeviceInterface.SCALE, DriverChoice("serial", SerialScaleSettings(port = "COM4").toConfig()))

        assertEquals(2, instances.size)
        assertTrue(instances[0].closed)
        assertEquals("COM4", instances[1].config["port"])
        job.cancel()
        ioScope.cancel()
    }

    @Test
    fun `the chosen print service is persisted under hw printer name and reaches the printer factory`() = runTest {
        val dataStore = FakePreferencesDataStore()
        val factory = RecordingPrinterFactory()
        val registry = DriverRegistryImpl(
            dataStore = dataStore,
            scaleFactories = listOf(FakeScaleDriverFactory("manual")),
            printerFactories = listOf(MockPrinterDriverFactory(), factory),
            drawerFactories = emptyList(),
            terminalFactories = emptyList(),
            ioCoroutineScope = backgroundScope,
        )

        registry.select(DeviceInterface.PRINTER, DriverChoice("escpos", mapOf("printerName" to "EPSON TM-T20")))
        registry.printer.first()

        assertEquals("EPSON TM-T20", dataStore.data.first().asMap().mapKeys { it.key.name }["hw.printer.name"])
        assertEquals(mapOf("printerName" to "EPSON TM-T20"), registry.profile.first().printer.config)
        assertEquals(mapOf("printerName" to "EPSON TM-T20"), factory.lastConfig)
    }

    @Test
    fun `a changed print service yields a fresh printer instance`() = runTest {
        val ioScope = CoroutineScope(Dispatchers.Unconfined + Job())
        val factory = RecordingPrinterFactory()
        val registry = DriverRegistryImpl(
            dataStore = FakePreferencesDataStore(),
            scaleFactories = listOf(FakeScaleDriverFactory("manual")),
            printerFactories = listOf(MockPrinterDriverFactory(), factory),
            drawerFactories = emptyList(),
            terminalFactories = emptyList(),
            ioCoroutineScope = ioScope,
        )
        registry.select(DeviceInterface.PRINTER, DriverChoice("escpos", mapOf("printerName" to "A")))
        val seen = mutableListOf<ReceiptPrinter>()
        val job = ioScope.launch { registry.printer.collect { seen += it } }

        registry.select(DeviceInterface.PRINTER, DriverChoice("escpos", mapOf("printerName" to "B")))

        assertEquals(2, seen.size)
        assertEquals(listOf(mapOf("printerName" to "A"), mapOf("printerName" to "B")), factory.configs)
        job.cancel()
        ioScope.cancel()
    }

    @Test
    fun `script and paperWidthMm are forwarded to the mock scale and printer`() = runTest {
        val dataStore = FakePreferencesDataStore()
        val registry = registryWith(dataStore, scaleIds = listOf("manual", "mock"))

        registry.select(DeviceInterface.SCALE, DriverChoice("mock", mapOf("script" to "120,340")))
        registry.select(DeviceInterface.PRINTER, DriverChoice("mock", mapOf("paperWidthMm" to "58")))

        val profile = registry.profile.first()
        assertEquals("120,340", profile.scale.config["script"])
        assertEquals("58", profile.printer.config["paperWidthMm"])
        assertEquals("120,340", (registry.scale.first() as FakeScale).config["script"])
        val stored = dataStore.data.first().asMap().mapKeys { it.key.name }
        assertEquals("120,340", stored["hw.scale.script"])
        assertEquals("58", stored["hw.printer.paperWidthMm"])
    }

    @Test
    fun `reconnecting the scale opens a fresh instance of the same choice`() = runTest {
        val ioScope = CoroutineScope(Dispatchers.Unconfined + Job())
        val registry = registryWith(FakePreferencesDataStore(), scaleIds = listOf("manual"), scope = ioScope)
        val instances = mutableListOf<FakeScale>()
        val job = ioScope.launch { registry.scale.collect { instances += it as FakeScale } }

        registry.reconnectScale()

        assertEquals(2, instances.size)
        assertTrue(instances[0].closed)
        assertFalse(instances[1].closed)
        job.cancel()
        ioScope.cancel()
    }

    private fun registryWith(
        dataStore: FakePreferencesDataStore,
        scaleIds: List<String>,
        scope: CoroutineScope,
    ) = DriverRegistryImpl(
        dataStore = dataStore,
        scaleFactories = scaleIds.map { FakeScaleDriverFactory(it) },
        printerFactories = emptyList(),
        drawerFactories = emptyList(),
        terminalFactories = emptyList(),
        ioCoroutineScope = scope,
    )

    private fun kotlinx.coroutines.test.TestScope.registryWith(dataStore: FakePreferencesDataStore, scaleIds: List<String>) =
        registryWith(dataStore, scaleIds, backgroundScope)

    private class RecordingPrinterFactory : PrinterDriverFactory {
        override val descriptor = DriverDescriptor("escpos", "escpos", platformOnly = true)
        var lastConfig: Map<String, String>? = null
        val configs = mutableListOf<Map<String, String>>()
        override fun create(config: Map<String, String>): ReceiptPrinter {
            lastConfig = config
            configs += config
            return MockPrinterDriverFactory().create(config)
        }
    }
}
