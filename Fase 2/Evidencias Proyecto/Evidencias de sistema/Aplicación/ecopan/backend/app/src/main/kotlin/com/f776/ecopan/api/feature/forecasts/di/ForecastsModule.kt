package com.f776.ecopan.api.feature.forecasts.di

import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.feature.forecasts.domain.ForecastsService
import com.f776.ecopan.api.feature.forecasts.domain.ForecastsServiceImpl
import org.koin.dsl.module

val ForecastsModule = module {
    single<ForecastsService> {
        ForecastsServiceImpl(
            forecastRepository = get(),
            forecastRefreshBroker = get(),
            reportingTz = get<Settings>().reportingTz
        )
    }
}
