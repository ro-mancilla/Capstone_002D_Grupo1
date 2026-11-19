package com.f776.ecopan.api.fakes

import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.core.storage.ObjectStorage
import com.f776.ecopan.api.feature.auth.domain.AuthService
import com.f776.ecopan.api.feature.auth.domain.AuthServiceImpl
import com.f776.ecopan.api.feature.catalog.domain.CatalogService
import com.f776.ecopan.api.feature.catalog.domain.CatalogServiceImpl
import com.f776.ecopan.api.feature.inventory.domain.InventoryService
import com.f776.ecopan.api.feature.inventory.domain.InventoryServiceImpl
import com.f776.ecopan.api.feature.inventory.domain.ProductStockCalculator
import com.f776.ecopan.api.feature.sync.domain.SyncIngestService
import com.f776.ecopan.api.feature.sync.domain.SyncIngestServiceImpl
import com.f776.ecopan.api.feature.sync.domain.SyncPullService
import com.f776.ecopan.api.feature.sync.domain.SyncPullServiceImpl
import com.f776.ecopan.api.feature.tenant.domain.TenantService
import com.f776.ecopan.api.feature.tenant.domain.TenantServiceImpl
import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.IngredientRepository
import com.f776.ecopan.data.database.repository.ProductStockRepository
import com.f776.ecopan.data.database.repository.ProductionBatchRepository
import com.f776.ecopan.data.database.repository.RefreshTokenRepository
import com.f776.ecopan.data.database.repository.SaleRepository
import com.f776.ecopan.data.database.repository.StockMovementRepository
import com.f776.ecopan.data.database.repository.SyncPullRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.UserRepository
import com.f776.ecopan.data.messagebroker.ProductionBatchBroker
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.config.ApplicationConfig
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

/**
 * Minimal Koin wiring for router tests -- fakes in, no real Postgres/RabbitMQ (02 §8). Every new
 * fake is optional so existing auth-only tests don't need to change; router tests exercising
 * `tenant`/`catalog`/`inventory`/`sync` pass the fakes they need.
 */
fun Application.installTestKoin(
    config: ApplicationConfig,
    tenants: FakeTenantRepository,
    users: FakeUserRepository,
    branches: FakeBranchRepository,
    refreshTokens: FakeRefreshTokenRepository,
    ingredients: FakeIngredientRepository = FakeIngredientRepository(),
    catalog: FakeCatalogRepository = FakeCatalogRepository(),
    sales: FakeSaleRepository = FakeSaleRepository(),
    productionBatches: FakeProductionBatchRepository = FakeProductionBatchRepository(),
    stockMovements: FakeStockMovementRepository = FakeStockMovementRepository(),
    productStock: FakeProductStockRepository = FakeProductStockRepository(),
    syncPull: FakeSyncPullRepository = FakeSyncPullRepository(),
    rabbitMq: FakeProductionBatchBroker = FakeProductionBatchBroker(),
    objectStorage: InMemoryObjectStorage = InMemoryObjectStorage()
) {
    catalog.openingLedger = productStock
    install(Koin) {
        modules(
            module {
                single { Settings(config) }
                single { get<Settings>().storage }
                single<ObjectStorage> { objectStorage }
                single<TenantRepository> { tenants }
                single<UserRepository> { users }
                single<BranchRepository> { branches }
                single<RefreshTokenRepository> { refreshTokens }
                single<IngredientRepository> { ingredients }
                single<CatalogRepository> { catalog }
                single<SaleRepository> { sales }
                single<ProductionBatchRepository> { productionBatches }
                single<StockMovementRepository> { stockMovements }
                single<ProductStockRepository> { productStock }
                single<SyncPullRepository> { syncPull }
                single<ProductionBatchBroker> { rabbitMq }

                single<AuthService> {
                    val settings = get<Settings>()
                    AuthServiceImpl(
                        jwtSettings = settings.jwtSettings,
                        deviceSessionTtlDays = settings.deviceSessionTtlDays,
                        tenantRepository = get(),
                        userRepository = get(),
                        branchRepository = get(),
                        refreshTokenRepository = get()
                    )
                }
                singleOf(::TenantServiceImpl).bind<TenantService>()
                singleOf(::CatalogServiceImpl).bind<CatalogService>()
                singleOf(::ProductStockCalculator)
                single<InventoryService> {
                    InventoryServiceImpl(get(), get(), get(), get(), get(), reportingTz = get<Settings>().reportingTz)
                }
                singleOf(::SyncIngestServiceImpl).bind<SyncIngestService>()
                singleOf(::SyncPullServiceImpl).bind<SyncPullService>()
            }
        )
    }
}
