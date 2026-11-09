package com.f776.ecopan.data.sync.di

import com.f776.ecopan.data.sync.ConnectivityMonitorImpl
import com.f776.ecopan.data.sync.background.AndroidBackgroundSyncEnqueuer
import com.f776.ecopan.data.sync.background.OutboxFlushWorker
import com.f776.ecopan.domain.sync.BackgroundSyncEnqueuer
import com.f776.ecopan.domain.sync.ConnectivityMonitor
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Real `ConnectivityMonitor` binding (03-sync.md work item 6): `ConnectivityManager`-based,
 * needs a Koin-injected `Context` — already bound app-wide via `androidContext(...)` in
 * `EcopanApplication.onCreate` (org.koin.android.ext.koin), so `factoryOf` resolves it like any
 * other dependency.
 */
internal actual val PlatformModule = module {
    factoryOf(::ConnectivityMonitorImpl).bind<ConnectivityMonitor>()


    // 03 §7's optional Android hardening: a constrained, best-effort outbox flush after the app
    // is backgrounded. `workerOf` mirrors `:data:reminder:impl`'s `BootSchedulerWorker` wiring.
    factoryOf(::AndroidBackgroundSyncEnqueuer).bind<BackgroundSyncEnqueuer>()
    workerOf(::OutboxFlushWorker)
}
