package com.f776.ecopan.api.core

import com.f776.ecopan.api.core.storage.AzureBlobObjectStorage
import com.f776.ecopan.api.core.storage.ObjectStorage
import com.f776.ecopan.api.seed.SeedLoader
import com.f776.ecopan.data.core.BaseSettings
import io.ktor.server.application.Application
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

fun provideCoreModule(application: Application) = module {
    single { Settings(application.environment.config) }.bind<BaseSettings>()
    single { get<Settings>().storage }
    singleOf(::AzureBlobObjectStorage).bind<ObjectStorage>()
    single {
        SeedLoader(
            tenantRepository = get(),
            branchRepository = get(),
            userRepository = get(),
            ingredientRepository = get(),
            catalogRepository = get(),
            saleRepository = get(),
            productStockRepository = get(),
            seedRevisionRepository = get(),
            reportingTz = get<Settings>().reportingTz,
            forecastRefreshBroker = get()
        )
    }
}