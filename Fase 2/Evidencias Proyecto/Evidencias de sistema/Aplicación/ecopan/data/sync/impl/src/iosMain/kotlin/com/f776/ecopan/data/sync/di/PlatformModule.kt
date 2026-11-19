package com.f776.ecopan.data.sync.di

import com.f776.ecopan.data.sync.ConnectivityMonitorImpl
import com.f776.ecopan.domain.sync.BackgroundSyncEnqueuer
import com.f776.ecopan.domain.sync.ConnectivityMonitor
import com.f776.ecopan.domain.sync.NoOpBackgroundSyncEnqueuer
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Real `ConnectivityMonitor` binding (03-sync.md work item 6): `platform.Network.nw_path_monitor_*`. */
internal actual val PlatformModule = module {
    factoryOf(::ConnectivityMonitorImpl).bind<ConnectivityMonitor>()


    // 03 §7: no BGTaskScheduler/daemon on iOS -- the in-process coroutine loop is the whole
    // story here, and the plan documents that as a deliberate limitation, not an omission.
    single<BackgroundSyncEnqueuer> { NoOpBackgroundSyncEnqueuer }
}
