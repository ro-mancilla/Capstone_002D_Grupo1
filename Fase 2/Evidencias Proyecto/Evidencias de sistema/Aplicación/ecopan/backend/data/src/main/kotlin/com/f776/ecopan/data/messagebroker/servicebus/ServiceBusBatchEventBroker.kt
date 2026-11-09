package com.f776.ecopan.data.messagebroker.servicebus

import com.f776.ecopan.data.core.ServiceBusSettings
import com.f776.ecopan.data.messagebroker.BatchEventBroker
import com.f776.ecopan.data.messagebroker.model.BatchProcessed
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/** The worker->notifier hop on a Service Bus queue: durable, settled, competing consumers. */
internal class ServiceBusBatchEventBroker(
    private val runtime: ServiceBusRuntime,
    private val config: ServiceBusSettings
) : BatchEventBroker {

    private val logger = LoggerFactory.getLogger(ServiceBusBatchEventBroker::class.java)

    override suspend fun publishProcessed(event: BatchProcessed) {
        runtime.ensureQueue(config.batchEventsQueue)
        runtime.sendToQueue(config.batchEventsQueue, Json.encodeToString(event))
    }

    override suspend fun consumeProcessed(block: suspend (BatchProcessed) -> Unit) {
        runtime.ensureQueue(config.batchEventsQueue)
        logger.info(" [*] Consuming batch completion events from '{}'", config.batchEventsQueue)
        runtime.consumeQueue(config.batchEventsQueue, logger) { body ->
            block(Json.decodeFromString<BatchProcessed>(body))
        }
    }
}
