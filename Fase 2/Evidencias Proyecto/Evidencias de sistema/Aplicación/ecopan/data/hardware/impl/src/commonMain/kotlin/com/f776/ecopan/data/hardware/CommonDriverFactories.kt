package com.f776.ecopan.data.hardware

import com.f776.ecopan.domain.hardware.DriverDescriptor
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Bound on every platform (04 §2 matrix). JVM's `PlatformModule` additionally binds Serial/EscPos
 * factories. [DriverDescriptor.displayName] feeds the Settings → Devices picker directly (08) —
 * this app is Spanish-only (no UI-language switcher), so these are Spanish, not translation keys.
 */
internal class MockScaleDriverFactory(
    private val defaultDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher,
) : ScaleDriverFactory {
    override val descriptor = DriverDescriptor(id = "mock", displayName = "Balanza simulada (demo)", platformOnly = false, simulated = true)
    override fun create(config: Map<String, String>) = MockScale(config, defaultDispatcher, ioDispatcher) { DemoWeightScript.fromConfig(config) }
}

internal class ManualScaleDriverFactory : ScaleDriverFactory {
    override val descriptor = DriverDescriptor(id = "manual", displayName = "Ingreso manual de peso", platformOnly = false)
    override fun create(config: Map<String, String>) = ManualScale(config)
}

internal class MockPrinterDriverFactory : PrinterDriverFactory {
    override val descriptor = DriverDescriptor(id = "mock", displayName = "Sin impresora — boleta en pantalla", platformOnly = false, none = true)
    override fun create(config: Map<String, String>) = MockPrinter(config)
}

internal class MockDrawerDriverFactory : DrawerDriverFactory {
    override val descriptor = DriverDescriptor(id = "mock", displayName = "Cajón simulado (demo)", platformOnly = false, simulated = true)
    override fun create(config: Map<String, String>) = MockDrawer()
}

internal class SimulatedTerminalDriverFactory : TerminalDriverFactory {
    override val descriptor = DriverDescriptor(id = "mock", displayName = "Terminal simulado (demo)", platformOnly = false, simulated = true)
    override fun create(config: Map<String, String>) = SimulatedTerminal()
}
