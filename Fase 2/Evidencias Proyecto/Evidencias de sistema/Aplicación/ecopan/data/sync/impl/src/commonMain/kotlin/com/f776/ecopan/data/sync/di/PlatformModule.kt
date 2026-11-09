package com.f776.ecopan.data.sync.di

import org.koin.core.module.Module

/**
 * `ConnectivityMonitor` actuals land here (03-sync.md work item 6) — androidMain
 * (`ConnectivityManager`), iosMain (`platform.Network.nw_path_monitor_*`, context7-verified at
 * that item), jvmMain (periodic health-probe). Until then every platform actual is an empty
 * stub: no binding exists yet, so `SyncDataModule` does not claim to provide `ConnectivityMonitor`.
 */
internal expect val PlatformModule: Module
