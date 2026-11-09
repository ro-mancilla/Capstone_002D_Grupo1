package com.f776.ecopan.feature.pos.di

import com.f776.ecopan.data.catalog.di.CatalogDataModule
import com.f776.ecopan.data.hardware.di.HardwareDataModule
import com.f776.ecopan.data.production.di.ProductionDataModule
import com.f776.ecopan.data.sales.di.SalesDataModule
import com.f776.ecopan.data.tenant.di.TenantDataModule
import com.f776.ecopan.feature.shell.di.ShellModule
import com.f776.ecopan.feature.pos.navigation.PosNavigationModule
import com.f776.ecopan.feature.pos.screens.pos.PosViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val PosModule = module {
    includes(PosNavigationModule, CatalogDataModule, HardwareDataModule, SalesDataModule, TenantDataModule, ProductionDataModule, ShellModule)
    viewModel {
        PosViewModel(
            productRepository = get(),
            driverRegistry = get(),
            saleRepository = get(),
            authRepository = get(),
            shellContext = get(),
            productionRepository = get(),
            syncEngine = get(),
        )
    }
}
