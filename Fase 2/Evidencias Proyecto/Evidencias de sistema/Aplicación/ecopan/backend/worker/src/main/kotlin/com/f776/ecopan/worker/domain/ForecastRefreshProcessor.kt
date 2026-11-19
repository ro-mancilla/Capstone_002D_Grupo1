package com.f776.ecopan.worker.domain

import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested

interface ForecastRefreshProcessor {
    suspend fun processMessage(message: ForecastRefreshRequested)
}
