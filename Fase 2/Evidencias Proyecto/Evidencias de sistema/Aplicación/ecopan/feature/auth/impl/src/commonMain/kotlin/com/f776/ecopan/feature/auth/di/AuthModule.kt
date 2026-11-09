package com.f776.ecopan.feature.auth.di

import com.f776.ecopan.data.sync.config.BuildKonfig
import com.f776.ecopan.data.sync.di.SyncDataModule
import com.f776.ecopan.data.tenant.di.TenantDataModule
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.tenant.AuthRepository
import com.f776.ecopan.domain.tenant.SessionProvider
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.feature.auth.PinUnlockSupported
import com.f776.ecopan.feature.auth.screens.auth.AuthViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * `SyncDataModule` is included here (not just relied upon transitively via `composeApp`'s
 * `AppModule`) so [AuthViewModel]'s `SyncEngine` dependency resolves even if this module is ever
 * loaded standalone (tests aside, which use fakes and never touch Koin) — same reasoning as every
 * other feature module explicitly including the data modules it needs (e.g. `ProductionModule`
 * including `InventoryDataModule`/`CatalogDataModule`).
 */
val AuthModule = module {
    includes(TenantDataModule, SyncDataModule)

    viewModel {
        AuthViewModel(
            tenantRepository = get<TenantRepository>(),
            authRepository = get<AuthRepository>(),
            syncEngine = get<SyncEngine>(),
            sessionProvider = get<SessionProvider>(),
            pinUnlockSupported = PinUnlockSupported,
            devQuickLoginEnabled = BuildKonfig.DEV_QUICK_LOGIN_ENABLED,
        )
    }
}
