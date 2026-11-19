package com.f776.ecopan.data.messagebroker

import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage

/**
 * The app->worker hop: a logged production batch handed to the worker so it can decrement the
 * ingredient stock its formula consumes. Durable and acked (at-least-once) with competing
 * consumers, so `docker compose up --scale worker=N` distributes work rather than duplicating it.
 *
 * Two implementations satisfy this contract, selected by
 * [com.f776.ecopan.data.core.BrokerBackend]: `messagebroker.rabbitmq` (self-hosted AMQP 0-9-1) and
 * `messagebroker.servicebus` (Azure Service Bus). Both are `internal`, so callers only ever see
 * this interface — which is why the name no longer mentions a product.
 */
interface ProductionBatchBroker {
    suspend fun publish(message: ProductionBatchMessage)

    /**
     * Consumes until the calling coroutine is cancelled. Implementations block for the lifetime of
     * the consumer, so callers wrap this in their own reconnect loop.
     */
    suspend fun consume(block: suspend (ProductionBatchMessage) -> Unit)
}
