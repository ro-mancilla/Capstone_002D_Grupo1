package com.f776.ecopan.api.feature.inventory.di

import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.feature.inventory.domain.InventoryService
import com.f776.ecopan.api.feature.inventory.domain.InventoryServiceImpl
import com.f776.ecopan.api.feature.inventory.domain.ProductStockCalculator
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val InventoryModule = module {
    singleOf(::ProductStockCalculator)
    single<InventoryService> {
        InventoryServiceImpl(
            ingredientRepository = get(),
            stockMovementRepository = get(),
            catalogRepository = get(),
            productStockRepository = get(),
            stockCalculator = get(),
            reportingTz = get<Settings>().reportingTz
        )
    }
}
