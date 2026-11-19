package com.f776.ecopan.data.database.di

import com.f776.ecopan.data.database.repository.AnalyticsRepository
import com.f776.ecopan.data.database.repository.AnalyticsRepositoryImpl
import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.ForecastRepository
import com.f776.ecopan.data.database.repository.ForecastRepositoryImpl
import com.f776.ecopan.data.database.repository.IngredientRepository
import com.f776.ecopan.data.database.repository.IngredientRepositoryImpl
import com.f776.ecopan.data.database.repository.OutboxRepository
import com.f776.ecopan.data.database.repository.OutboxRepositoryImpl
import com.f776.ecopan.data.database.repository.RefreshTokenRepository
import com.f776.ecopan.data.database.repository.RefreshTokenRepositoryImpl
import com.f776.ecopan.data.database.repository.ProductStockRepository
import com.f776.ecopan.data.database.repository.ProductStockRepositoryImpl
import com.f776.ecopan.data.database.repository.ProductionBatchRepository
import com.f776.ecopan.data.database.repository.ProductionBatchRepositoryImpl
import com.f776.ecopan.data.database.repository.RolePermissionRepository
import com.f776.ecopan.data.database.repository.RolePermissionRepositoryImpl
import com.f776.ecopan.data.database.repository.SaleRepository
import com.f776.ecopan.data.database.repository.SaleRepositoryImpl
import com.f776.ecopan.data.database.repository.ScheduledRunRepository
import com.f776.ecopan.data.database.repository.ScheduledRunRepositoryImpl
import com.f776.ecopan.data.database.repository.SeedRevisionRepository
import com.f776.ecopan.data.database.repository.SeedRevisionRepositoryImpl
import com.f776.ecopan.data.database.repository.StockMovementRepository
import com.f776.ecopan.data.database.repository.StockMovementRepositoryImpl
import com.f776.ecopan.data.database.repository.SyncPullRepository
import com.f776.ecopan.data.database.repository.SyncPullRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepository
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

internal val DatabaseModule = module {
    // Server receipt time is stamped through this, so tests can pin it.
    single<Clock> { Clock.System }
    singleOf(::TenantRepositoryImpl).bind<TenantRepository>()
    singleOf(::BranchRepositoryImpl).bind<BranchRepository>()
    singleOf(::UserRepositoryImpl).bind<UserRepository>()
    singleOf(::RefreshTokenRepositoryImpl).bind<RefreshTokenRepository>()
    singleOf(::RolePermissionRepositoryImpl).bind<RolePermissionRepository>()
    singleOf(::CatalogRepositoryImpl).bind<CatalogRepository>()
    singleOf(::IngredientRepositoryImpl).bind<IngredientRepository>()
    singleOf(::SaleRepositoryImpl).bind<SaleRepository>()
    singleOf(::SeedRevisionRepositoryImpl).bind<SeedRevisionRepository>()
    singleOf(::ProductionBatchRepositoryImpl).bind<ProductionBatchRepository>()
    singleOf(::StockMovementRepositoryImpl).bind<StockMovementRepository>()
    singleOf(::ProductStockRepositoryImpl).bind<ProductStockRepository>()
    singleOf(::SyncPullRepositoryImpl).bind<SyncPullRepository>()
    singleOf(::AnalyticsRepositoryImpl).bind<AnalyticsRepository>()
    singleOf(::ForecastRepositoryImpl).bind<ForecastRepository>()
    singleOf(::ScheduledRunRepositoryImpl).bind<ScheduledRunRepository>()
    singleOf(::OutboxRepositoryImpl).bind<OutboxRepository>()
}
