package com.f776.ecopan.api.feature.catalog.di

import com.f776.ecopan.api.feature.catalog.domain.CatalogService
import com.f776.ecopan.api.feature.catalog.domain.CatalogServiceImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val CatalogModule = module {
    singleOf(::CatalogServiceImpl).bind<CatalogService>()
}
