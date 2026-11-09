package com.f776.ecopan.data.hardware.di

import com.f776.ecopan.data.hardware.NoDeviceDiscovery
import com.f776.ecopan.domain.hardware.DeviceDiscovery
import org.koin.dsl.module

/** Serial drivers exist only on JVM (01 §1) — Android registers only the common mock/manual set. */
internal actual val PlatformModule = module {
    single<DeviceDiscovery> { NoDeviceDiscovery }
}
