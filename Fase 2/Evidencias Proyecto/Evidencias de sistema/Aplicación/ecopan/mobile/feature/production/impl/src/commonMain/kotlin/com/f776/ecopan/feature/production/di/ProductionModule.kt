package com.f776.ecopan.feature.production.di

import com.f776.ecopan.data.catalog.di.CatalogDataModule
import com.f776.ecopan.data.inventory.di.InventoryDataModule
import com.f776.ecopan.data.production.di.ProductionDataModule
import com.f776.ecopan.data.tenant.di.TenantDataModule
import com.f776.ecopan.feature.shell.di.ShellModule
import com.f776.ecopan.feature.production.navigation.ProductionNavigationModule
import com.f776.ecopan.feature.production.screens.batchentry.BatchEntryViewModel
import com.f776.ecopan.feature.production.screens.stockglance.StockGlanceViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val ProductionModule = module {
    includes(ProductionNavigationModule, ProductionDataModule, InventoryDataModule, CatalogDataModule, TenantDataModule, ShellModule)

    viewModel {
        BatchEntryViewModel(
            productionRepository = get(),
            productRepository = get(),
            formulaRepository = get(),
            entitlementsProvider = get(),
            shellContext = get(),
            suggestedProductionRepository = get(),
        )
    }
    viewModel {
        StockGlanceViewModel(
            inventoryRepository = get(),
            ingredientRepository = get(),
            shellContext = get(),
        )
    }
}
