package com.f776.ecopan.data.messagebroker.rabbitmq

import com.f776.ecopan.data.core.RabbitMqSettings
import com.f776.ecopan.data.messagebroker.BatchEventBroker
import com.f776.ecopan.data.messagebroker.model.BatchProcessed
import dev.kourier.amqp.BuiltinExchangeType
import dev.kourier.amqp.Field
import dev.kourier.amqp.Properties
import dev.kourier.amqp.channel.AMQPChannel
import dev.kourier.amqp.connection.createAMQPConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

internal class RabbitBatchEventBroker(
    private val scope: CoroutineScope,
    private val config: RabbitMqSettings
) : BatchEventBroker {
    private val logger = LoggerFactory.getLogger(RabbitBatchEventBroker::class.java)

    override suspend fun publishProcessed(event: BatchProcessed) {
        val connection = createAMQPConnection(scope, config.toAmqpConfig())
        val channel = connection.openChannel()
        declare(channel)
        channel.basicPublish(
            Json.encodeToString(event).toByteArray(),
            exchange = config.batchEventsExchange,
            routingKey = config.batchEventsQueue,
            properties = Properties()
        )
        channel.close()
        connection.close()
    }

    override suspend fun consumeProcessed(block: suspend (BatchProcessed) -> Unit) {
        val connection = createAMQPConnection(scope, config.toAmqpConfig())
        val channel = connection.openChannel()
        declare(channel)
        channel.basicQos(config.prefetch.toUShort(), false)

        logger.info(" [*] Consuming batch completion events from '${config.batchEventsQueue}'")
        val consumer = channel.basicConsume(config.batchEventsQueue, noAck = false)

        consumeUntilConnectionCloses(connection) {
            for (delivery in consumer) {
                // Decoded inside the loop's own guard, not above it. A message this service cannot
                // parse used to throw straight out of `for (delivery in consumer)`, killing the consume
                // loop and with it every subsequent message -- one malformed body, published by an
                // older or newer build, took the whole consumer down and left itself unacked to be
                // redelivered into the same crash on restart. Rejecting the individual message keeps
                // the loop alive.
                val event = try {
                    Json.decodeFromString<BatchProcessed>(delivery.message.body.decodeToString())
                } catch (e: Exception) {
                    logger.error("Undecodable batch event; dropping", e)
                    channel.basicNack(delivery.message, false, false)
                    continue
                }
                try {
                    block(event)
                    channel.basicAck(delivery.message, false)
                } catch (e: Exception) {
                    logger.error("Failed to handle $event; dropping", e)
                    channel.basicNack(delivery.message, false, false)
                }
            }
        }

        channel.close()
        connection.close()
    }

    private suspend fun declare(channel: AMQPChannel) {
        channel.exchangeDeclare(config.batchEventsExchange, BuiltinExchangeType.DIRECT, durable = true)
        channel.queueDeclare(
            config.batchEventsQueue,
            durable = true,
            exclusive = false,
            autoDelete = false,
            arguments = mapOf("x-queue-type" to Field.LongString("quorum"))
        )
        channel.queueBind(
            queue = config.batchEventsQueue,
            exchange = config.batchEventsExchange,
            routingKey = config.batchEventsQueue
        )
    }
}
