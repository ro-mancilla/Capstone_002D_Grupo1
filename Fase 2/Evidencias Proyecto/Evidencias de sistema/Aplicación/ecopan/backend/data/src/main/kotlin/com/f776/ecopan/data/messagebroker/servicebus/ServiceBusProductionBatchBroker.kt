package com.f776.ecopan.data.messagebroker.servicebus

import com.f776.ecopan.data.core.ServiceBusSettings
import com.f776.ecopan.data.messagebroker.ProductionBatchBroker
import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Service Bus counterpart of
 * [com.f776.ecopan.data.messagebroker.rabbitmq.RabbitProductionBatchBroker].
 *
 * Same guarantees, considerably less code, because a Service Bus queue *is* the topology: there is
 * no exchange to declare, no routing key to bind, and no dead-letter exchange/queue pair to wire up
 * by hand — the DLQ is a sub-queue every queue is born with.
 */
internal class ServiceBusProductionBatchBroker(
    private val runtime: ServiceBusRuntime,
    private val config: ServiceBusSettings
) : ProductionBatchBroker {

    private val logger = LoggerFactory.getLogger(ServiceBusProductionBatchBroker::class.java)

    override suspend fun publish(message: ProductionBatchMessage) {
        runtime.ensureQueue(config.productionQueue)
        runtime.sendToQueue(config.productionQueue, Json.encodeToString(message))
        logger.info(" [x] Sent '$message'")
    }

    override suspend fun consume(block: suspend (ProductionBatchMessage) -> Unit) {
        runtime.ensureQueue(config.productionQueue)
        logger.info(
            " [*] Waiting for messages on '{}' (maxConcurrentCalls={})",
            config.productionQueue,
            config.maxConcurrentCalls
        )
        runtime.consumeQueue(config.productionQueue, logger) { body ->
            val message = Json.decodeFromString<ProductionBatchMessage>(body)
            logger.info("Received message: $message")
            block(message)
        }
    }
}
