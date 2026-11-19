package com.f776.ecopan.data.catalog.di

import com.f776.ecopan.data.catalog.FormulaRepositoryImpl
import com.f776.ecopan.data.catalog.ProductRepositoryImpl
import com.f776.ecopan.data.catalog.sync.FormulaSyncApplier
import com.f776.ecopan.data.catalog.sync.ProductSyncApplier
import com.f776.ecopan.data.tenant.di.TenantDataModule
import com.f776.ecopan.domain.catalog.FormulaRepository
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.unigrid.core.database.di.DatabaseModule
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Ingredients live in `:data:inventory` (01 §2's module table), not here — 06 §1 corrected this. */
val CatalogDataModule = module {
    includes(DatabaseModule, TenantDataModule)

    factoryOf(::ProductRepositoryImpl).bind<ProductRepository>()
    factoryOf(::FormulaRepositoryImpl).bind<FormulaRepository>()

    // Config pull appliers (03 §4.3 family 1). Each has a distinct primary type, so unlike the
    // hardware driver factories these do not collide on a single Koin key -- `getAll<SyncApplier>()`
    // sees all of them. Bound here rather than in `:data:sync` for the usual reason (SyncApplier's
    // KDoc): PullWorker collects them at runtime, so :data:sync never depends on this module.
    factoryOf(::ProductSyncApplier).bind<SyncApplier>()
    factoryOf(::FormulaSyncApplier).bind<SyncApplier>()
}
