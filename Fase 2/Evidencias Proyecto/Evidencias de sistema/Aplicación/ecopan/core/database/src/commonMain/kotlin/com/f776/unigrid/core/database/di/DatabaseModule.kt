package com.f776.unigrid.core.database.di

import com.f776.unigrid.core.database.AppDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val DatabaseModule = module {
    includes(PlatformDatabaseModule)
    single { get<AppDatabase>().noteDao() }
    single { get<AppDatabase>().tenantDao() }
    single { get<AppDatabase>().branchDao() }
    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().productDao() }
    single { get<AppDatabase>().ingredientDao() }
    single { get<AppDatabase>().formulaDao() }
    single { get<AppDatabase>().outboxDao() }
    single { get<AppDatabase>().saleDao() }
    single { get<AppDatabase>().stockMovementDao() }
    single { get<AppDatabase>().productionBatchDao() }
    single { get<AppDatabase>().forecastDao() }
}

internal expect val PlatformDatabaseModule: Module
