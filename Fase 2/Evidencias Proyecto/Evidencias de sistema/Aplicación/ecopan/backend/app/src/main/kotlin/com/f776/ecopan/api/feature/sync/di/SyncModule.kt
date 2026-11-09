package com.f776.ecopan.api.feature.sync.di

import com.f776.ecopan.api.feature.sync.domain.SyncIngestService
import com.f776.ecopan.api.feature.sync.domain.SyncIngestServiceImpl
import com.f776.ecopan.api.feature.sync.domain.SyncPullService
import com.f776.ecopan.api.feature.sync.domain.SyncPullServiceImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val SyncModule = module {
    singleOf(::SyncIngestServiceImpl).bind<SyncIngestService>()
    singleOf(::SyncPullServiceImpl).bind<SyncPullService>()
}
