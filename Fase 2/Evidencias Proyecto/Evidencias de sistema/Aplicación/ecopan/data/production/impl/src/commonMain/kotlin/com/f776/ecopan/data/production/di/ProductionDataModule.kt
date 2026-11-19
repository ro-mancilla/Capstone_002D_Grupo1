package com.f776.ecopan.data.production.di

import com.f776.ecopan.data.catalog.di.CatalogDataModule
import com.f776.ecopan.data.inventory.di.InventoryDataModule
import com.f776.ecopan.data.production.BatchReadyBus
import com.f776.ecopan.data.production.ProductionRepositoryImpl
import com.f776.ecopan.data.production.SuggestedProductionRepositoryImpl
import com.f776.ecopan.data.production.sync.ForecastSyncApplier
import com.f776.ecopan.data.production.sse.BatchReadySseListener
import com.f776.ecopan.data.production.sync.ProductionBatchSyncApplier
import com.f776.ecopan.data.production.domain.FormulaPreview
import com.f776.ecopan.data.production.sideeffects.FormulaDecrementSideEffect
import com.f776.ecopan.data.production.sideeffects.NoOpBatchSideEffects
import com.f776.ecopan.data.tenant.di.TenantDataModule
import com.f776.ecopan.domain.production.BatchReadyStream
import com.f776.ecopan.domain.production.ProductionRepository
import com.f776.ecopan.domain.production.SuggestedProductionRepository
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.unigrid.core.database.di.DatabaseModule
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Both [FormulaDecrementSideEffect] and [NoOpBatchSideEffects] are always bound (06 §5) —
 * `ProductionRepositoryImpl` picks between them per call via `EntitlementsProvider`, since Koin
 * bindings are static but the tenant's `INVENTORY_FORMULAS` toggle must take effect without an
 * app restart.
 */
val ProductionDataModule = module {
    includes(DatabaseModule, InventoryDataModule, CatalogDataModule, TenantDataModule)

    factoryOf(::FormulaPreview)
    factoryOf(::FormulaDecrementSideEffect)
    factoryOf(::NoOpBatchSideEffects)
    // single, not factory: it is the one fan-out point every producer and collector must share
    // -- see BatchReadyBus's KDoc for why a field on the (factory-scoped) repository can't work.
    singleOf(::BatchReadyBus)
    factoryOf(::ProductionRepositoryImpl).bind<ProductionRepository>()

    // Bound here rather than in `:data:sync`, for the reason `StockMovementSyncApplier` and
    // `BranchSyncApplier` are bound in their own modules (03 §1.1/§4.3, `SyncApplier`'s KDoc):
    // `PullWorker` collects appliers at runtime via `getKoin().getAll<SyncApplier>()`, so
    // `:data:sync:impl` never becomes a compile-time dependent of this module.
    factoryOf(::ProductionBatchSyncApplier).bind<SyncApplier>()
    factoryOf(::ForecastSyncApplier).bind<SyncApplier>()
    factoryOf(::SuggestedProductionRepositoryImpl).bind<SuggestedProductionRepository>()

    // Started/stopped from :composeApp on the same session-ready signal as SyncScheduler (06 §6).
    single {
        BatchReadySseListener(
            eventStream = get(),
            batchReadyBus = get(),
            productRepository = get(),
            scope = get(named("ioCoroutineScope")),
        )
    }.bind<BatchReadyStream>()
}
