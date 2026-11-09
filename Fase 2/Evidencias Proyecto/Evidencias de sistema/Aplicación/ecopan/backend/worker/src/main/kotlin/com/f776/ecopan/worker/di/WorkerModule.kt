package com.f776.ecopan.worker.di

import com.f776.ecopan.data.core.BaseSettings
import com.f776.ecopan.data.core.DataModule
import com.f776.ecopan.worker.core.Settings
import com.f776.ecopan.worker.domain.DailyForecastScheduler
import com.f776.ecopan.worker.domain.ForecastRefreshProcessor
import com.f776.ecopan.worker.domain.ForecastRefreshProcessorImpl
import com.f776.ecopan.worker.domain.FormulaDecrementProcessor
import com.f776.ecopan.worker.domain.FormulaDecrementProcessorImpl
import com.f776.ecopan.worker.ml.KtorMlForecastClient
import com.f776.ecopan.worker.ml.MlForecastClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.bind
import org.koin.dsl.module

val WorkerModule = module {
    includes(DataModule)

    single { Settings() }.bind<BaseSettings>()

    single {
        val settings = get<Settings>()
        FormulaDecrementProcessorImpl(
            productionBatchRepository = get(),
            catalogRepository = get(),
            tenantRepository = get(),
            decrementEnabled = settings.decrementEnabled
        )
    }.bind<FormulaDecrementProcessor>()

    single {
        HttpClient(CIO) {
            // Non-2xx from the ML service (bad key, validation error) should throw with the
            // status attached, not fail later as an undecodable success body.
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                // One batched /forecast call covers every series for a branch; LightGBM inference
                // over ~90 days x hundreds of series is seconds, not minutes -- but give cold
                // container starts room.
                requestTimeoutMillis = 60_000
            }
        }
    }

    single<MlForecastClient> {
        val settings = get<Settings>()
        KtorMlForecastClient(
            baseUrl = settings.mlBaseUrl,
            apiKey = settings.mlApiKey,
            httpClient = get(),
        )
    }

    single {
        ForecastRefreshProcessorImpl(
            analyticsRepository = get(),
            forecastRepository = get(),
            mlForecastClient = get(),
            reportingTz = get<Settings>().reportingTz,
        )
    }.bind<ForecastRefreshProcessor>()

    single {
        DailyForecastScheduler(
            tenants = get(),
            branches = get(),
            runs = get(),
            broker = get(),
            reportingTz = get<Settings>().reportingTz,
        )
    }
}
