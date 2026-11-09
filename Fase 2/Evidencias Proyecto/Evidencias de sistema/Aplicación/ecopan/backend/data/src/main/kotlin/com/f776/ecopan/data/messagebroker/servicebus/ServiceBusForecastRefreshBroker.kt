package com.f776.ecopan.data.messagebroker.servicebus

import com.f776.ecopan.data.core.ServiceBusSettings
import com.f776.ecopan.data.messagebroker.ForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** F2's fire-and-forget hop into the worker, on a Service Bus queue. */
internal class ServiceBusForecastRefreshBroker(
    private val runtime: ServiceBusRuntime,
    private val config: ServiceBusSettings
) : ForecastRefreshBroker {

    private val logger = LoggerFactory.getLogger(ServiceBusForecastRefreshBroker::class.java)

    override suspend fun publish(message: ForecastRefreshRequested) {
        runtime.ensureQueue(config.forecastRefreshQueue)
        runtime.sendToQueue(config.forecastRefreshQueue, Json.encodeToString(message))
    }

    override suspend fun consume(block: suspend (ForecastRefreshRequested) -> Unit) {
        runtime.ensureQueue(config.forecastRefreshQueue)
        logger.info(" [*] Consuming forecast refresh requests from '{}'", config.forecastRefreshQueue)
        runtime.consumeQueue(config.forecastRefreshQueue, logger) { body ->
            block(Json.decodeFromString<ForecastRefreshRequested>(body))
        }
    }
}
