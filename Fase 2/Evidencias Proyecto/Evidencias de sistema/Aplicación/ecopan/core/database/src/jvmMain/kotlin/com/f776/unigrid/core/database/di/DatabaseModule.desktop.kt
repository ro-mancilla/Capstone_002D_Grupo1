package com.f776.unigrid.core.database.di

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.unigrid.core.database.DatabaseFactory
import com.f776.unigrid.core.database.MIGRATION_1_2
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val PlatformDatabaseModule: Module
    get() = module {
        single {
            DatabaseFactory()
                .create()
                .setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
