package com.f776.ecopan.data.messagebroker

import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested

/** F2's fire-and-forget hop into the worker (02 §3/§9 item 7). */
interface ForecastRefreshBroker {
    suspend fun publish(message: ForecastRefreshRequested)
    suspend fun consume(block: suspend (ForecastRefreshRequested) -> Unit)
}
