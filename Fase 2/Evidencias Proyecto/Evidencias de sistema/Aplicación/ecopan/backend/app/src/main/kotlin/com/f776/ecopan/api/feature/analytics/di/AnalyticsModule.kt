package com.f776.ecopan.api.feature.analytics.di

import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.feature.analytics.domain.AnalyticsService
import com.f776.ecopan.api.feature.analytics.domain.AnalyticsServiceImpl
import org.koin.dsl.module

val AnalyticsModule = module {
    single<AnalyticsService> {
        AnalyticsServiceImpl(
            analyticsRepository = get(),
            ingredientRepository = get(),
            stockMovementRepository = get(),
            forecastRepository = get(),
            reportingTz = get<Settings>().reportingTz
        )
    }
}
