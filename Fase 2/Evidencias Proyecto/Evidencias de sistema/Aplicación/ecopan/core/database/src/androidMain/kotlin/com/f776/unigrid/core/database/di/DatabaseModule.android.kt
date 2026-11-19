package com.f776.unigrid.core.database.di

import com.f776.unigrid.core.database.DatabaseFactory
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val PlatformDatabaseModule: Module
    get() = module {
        single {
            DatabaseFactory(context = get())
                .create()
                .build()
        }
    }