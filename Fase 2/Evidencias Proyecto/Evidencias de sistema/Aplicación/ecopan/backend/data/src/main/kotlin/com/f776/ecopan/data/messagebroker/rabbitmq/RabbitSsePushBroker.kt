package com.f776.ecopan.data.messagebroker.rabbitmq

import com.f776.ecopan.data.core.RabbitMqSettings
import com.f776.ecopan.data.messagebroker.SsePushBroker
import com.f776.ecopan.data.messagebroker.model.SsePushEvent
import dev.kourier.amqp.BuiltinExchangeType
import dev.kourier.amqp.Properties
import dev.kourier.amqp.connection.createAMQPConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

internal class RabbitSsePushBroker(
    private val scope: CoroutineScope,
    private val config: RabbitMqSettings
) : SsePushBroker {
    private val logger = LoggerFactory.getLogger(RabbitSsePushBroker::class.java)

    override suspend fun publish(event: SsePushEvent) {
        val connection = createAMQPConnection(scope, config.toAmqpConfig())
        val channel = connection.openChannel()
        channel.exchangeDeclare(config.sseExchange, BuiltinExchangeType.FANOUT, durable = true)
        channel.basicPublish(
            Json.encodeToString(event).toByteArray(),
            exchange = config.sseExchange,
            routingKey = "",
            properties = Properties()
        )
        channel.close()
        connection.close()
    }

    override suspend fun consume(block: suspend (SsePushEvent) -> Unit) {
        val connection = createAMQPConnection(scope, config.toAmqpConfig())
        val channel = connection.openChannel()
        channel.exchangeDeclare(config.sseExchange, BuiltinExchangeType.FANOUT, durable = true)

        // One exclusive, auto-delete queue per app instance: it disappears when this node
        // disconnects, so each node sees every broadcast without any shared state.
        val queueName = "${config.sseExchange}.${Uuid.random()}"
        channel.queueDeclare(queueName, durable = false, exclusive = true, autoDelete = true, arguments = emptyMap())
        channel.queueBind(queue = queueName, exchange = config.sseExchange, routingKey = "")

        logger.info(" [*] Consuming SSE push events on '$queueName'")
        val consumer = channel.basicConsume(queueName, noAck = true)

        consumeUntilConnectionCloses(connection) {
            for (delivery in consumer) {
                val event = Json.decodeFromString<SsePushEvent>(delivery.message.body.decodeToString())
                block(event)
            }
        }

        channel.close()
        connection.close()
    }
}
