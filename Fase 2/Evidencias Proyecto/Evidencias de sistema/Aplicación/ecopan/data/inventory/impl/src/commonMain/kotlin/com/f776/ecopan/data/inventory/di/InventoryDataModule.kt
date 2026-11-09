package com.f776.ecopan.data.inventory.di

import com.f776.ecopan.data.inventory.IngredientRepositoryImpl
import com.f776.ecopan.data.inventory.InventoryRepositoryImpl
import com.f776.ecopan.data.inventory.sync.IngredientSyncApplier
import com.f776.ecopan.data.inventory.sync.StockMovementSyncApplier
import com.f776.ecopan.data.tenant.di.TenantDataModule
import com.f776.ecopan.domain.inventory.IngredientRepository
import com.f776.ecopan.domain.inventory.InventoryRepository
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.unigrid.core.database.di.DatabaseModule
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * [StockMovementSyncApplier] is bound here, not in `:data:sync`, on purpose (03 §1.1/§4.3,
 * `SyncApplier`'s KDoc): `:data:sync:impl`'s `PullWorker` collects it at runtime via
 * `getKoin().getAll<SyncApplier>()` without this module ever being a compile-time dependency of
 * `:data:sync`.
 */
val InventoryDataModule = module {
    includes(DatabaseModule, TenantDataModule)

    factoryOf(::IngredientRepositoryImpl).bind<IngredientRepository>()
    factoryOf(::InventoryRepositoryImpl).bind<InventoryRepository>()
    factoryOf(::StockMovementSyncApplier).bind<SyncApplier>()
    factoryOf(::IngredientSyncApplier).bind<SyncApplier>()
}
