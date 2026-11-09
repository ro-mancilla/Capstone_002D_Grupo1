package com.f776.ecopan.api.feature.events.di

import com.f776.ecopan.api.feature.events.domain.SseConnectionRegistry
import org.koin.dsl.module

val EventsModule = module {
    single { SseConnectionRegistry() }
}
