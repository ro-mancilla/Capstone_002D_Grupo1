package com.f776.ecopan.data.sync.di

import com.f776.ecopan.data.sync.ConnectivityMonitorImpl
import com.f776.ecopan.domain.sync.BackgroundSyncEnqueuer
import com.f776.ecopan.domain.sync.ConnectivityMonitor
import com.f776.ecopan.domain.sync.NoOpBackgroundSyncEnqueuer
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Real `ConnectivityMonitor` binding (03-sync.md work item 6): periodic `/health` probe using the
 * same `HttpClient`/`"backendBaseUrl"` singles `SyncDataModule` already binds — lazy `get()` resolution
 * means the include order in `SyncDataModule` (`PlatformModule` included before those singles are
 * declared) doesn't matter, only first-use order.
 */
internal actual val PlatformModule = module {

    factory {
        ConnectivityMonitorImpl(httpClient = get<HttpClient>(), baseUrl = get(named("backendBaseUrl")))
    }.bind<ConnectivityMonitor>()

    // 03 §7: no BGTaskScheduler/daemon on JVM desktop -- the in-process coroutine loop is the whole
    // story here, and the plan documents that as a deliberate limitation, not an omission.
    single<BackgroundSyncEnqueuer> { NoOpBackgroundSyncEnqueuer }
}
