package com.f776.ecopan.api.core

import com.f776.ecopan.api.feature.analytics.di.AnalyticsModule
import com.f776.ecopan.api.feature.auth.di.AuthModule
import com.f776.ecopan.api.feature.catalog.di.CatalogModule
import com.f776.ecopan.api.feature.events.di.EventsModule
import com.f776.ecopan.api.feature.forecasts.di.ForecastsModule
import com.f776.ecopan.api.feature.inventory.di.InventoryModule
import com.f776.ecopan.api.feature.sync.di.SyncModule
import com.f776.ecopan.api.feature.tenant.di.TenantModule
import com.f776.ecopan.data.core.DataModule
import io.ktor.server.application.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(
            DataModule,
            provideCoreModule(this@configureKoin),
            EventsModule,
            AuthModule,
            TenantModule,
            CatalogModule,
            InventoryModule,
            SyncModule,
            AnalyticsModule,
            ForecastsModule
        )
    }
}
