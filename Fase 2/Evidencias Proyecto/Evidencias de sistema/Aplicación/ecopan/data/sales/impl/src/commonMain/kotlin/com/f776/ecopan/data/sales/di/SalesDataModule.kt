package com.f776.ecopan.data.sales.di

import com.f776.ecopan.data.sales.SaleRepositoryImpl
import com.f776.ecopan.domain.sales.SaleRepository
import com.f776.unigrid.core.database.di.DatabaseModule
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val SalesDataModule = module {
    includes(DatabaseModule)

    factoryOf(::SaleRepositoryImpl).bind<SaleRepository>()
}
