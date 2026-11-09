package com.f776.ecopan.data.tenant.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.f776.ecopan.core.crypto.di.CryptoModule
import com.f776.ecopan.core.datastore.di.DatastoreModule
import com.f776.ecopan.data.tenant.AuthRepositoryImpl
import com.f776.ecopan.data.tenant.EntitlementsProviderImpl
import com.f776.ecopan.data.tenant.SessionProviderImpl
import com.f776.ecopan.data.tenant.TenantRepositoryImpl
import com.f776.ecopan.data.tenant.TokenStore
import com.f776.ecopan.data.tenant.sync.BranchSyncApplier
import com.f776.ecopan.data.tenant.sync.UserSyncApplier
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.tenant.AuthRepository
import com.f776.ecopan.domain.tenant.DeviceSessionStore
import com.f776.ecopan.domain.tenant.EntitlementsProvider
import com.f776.ecopan.domain.tenant.SessionProvider
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.unigrid.core.database.di.DatabaseModule
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * [BranchSyncApplier] is bound here, not in `:data:sync`, on purpose (03 §1.1/§4.3,
 * `SyncApplier`'s KDoc) -- `:data:sync:impl`'s `PullWorker` collects it at runtime via
 * `getKoin().getAll<SyncApplier>()` without this module ever being a compile-time dependency of
 * `:data:sync`. `USER`/`PRODUCT`/`INGREDIENT`/`FORMULA` appliers follow the same
 * recipe but aren't wired yet (03 work item 5's narrowed scope, documented on `SyncEntityType`)
 * -- `PullWorker` skips any pulled row whose `entityType` has no registered applier rather than
 * crashing, so this is safe to leave for a follow-up. This is also why the real `/auth/login` call
 * ([AuthRepositoryImpl]) can only seed *this device's own logged-in user* into the cache directly
 * from the login response (no `pinHash` there) -- other staff's PINs only become unlockable once a
 * `USER` applier lands and a bootstrap pull runs.
 *
 * The backend host comes from the `"backendBaseUrl"` single that `:data:sync:impl`'s
 * `SyncDataModule` binds from `BuildKonfig` (both modules are always loaded together by
 * `:composeApp`'s `AppModule`). This module deliberately does not bind its own: it previously had a
 * separate `"authBaseUrl"` hardcoded to `localhost`, so repointing sync left *enrollment* still
 * calling the wrong host, and login failed while sync looked fine.
 */
val TenantDataModule = module {
    includes(DatabaseModule, CryptoModule, DatastoreModule)

    factoryOf(::TenantRepositoryImpl).bind<TenantRepository>()
    factoryOf(::EntitlementsProviderImpl).bind<EntitlementsProvider>()
    factoryOf(::BranchSyncApplier).bind<SyncApplier>()
    factoryOf(::UserSyncApplier).bind<SyncApplier>()

    factory {
        SessionProviderImpl(dataStore = get<DataStore<Preferences>>(named("datastore")))
    }.bind<SessionProvider>()

    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    factory {
        TokenStore(dataStore = get<DataStore<Preferences>>(named("datastore")))
    }.bind<DeviceSessionStore>()

    factory {
        AuthRepositoryImpl(
            httpClient = get(),
            baseUrl = get(named("backendBaseUrl")),
            tokenStore = get<TokenStore>(),
            tenantRepository = get(),
            sessionProvider = get(),
        )
    }.bind<AuthRepository>()
}
