package com.f776.ecopan.data.messagebroker.rabbitmq

import com.f776.ecopan.data.core.RabbitMqSettings
import com.f776.ecopan.data.messagebroker.ForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested
import dev.kourier.amqp.BuiltinExchangeType
import dev.kourier.amqp.Field
import dev.kourier.amqp.Properties
import dev.kourier.amqp.channel.AMQPChannel
import dev.kourier.amqp.connection.createAMQPConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

internal class RabbitForecastRefreshBroker(
    private val scope: CoroutineScope,
    private val config: RabbitMqSettings
) : ForecastRefreshBroker {
    private val logger = LoggerFactory.getLogger(RabbitForecastRefreshBroker::class.java)

    override suspend fun publish(message: ForecastRefreshRequested) {
        val connection = createAMQPConnection(scope, config.toAmqpConfig())
        val channel = connection.openChannel()
        declare(channel)
        channel.basicPublish(
            Json.encodeToString(message).toByteArray(),
            exchange = config.forecastRefreshExchange,
            routingKey = config.forecastRefreshQueue,
            properties = Properties()
        )
        channel.close()
        connection.close()
    }

    override suspend fun consume(block: suspend (ForecastRefreshRequested) -> Unit) {
        val connection = createAMQPConnection(scope, config.toAmqpConfig())
        val channel = connection.openChannel()
        declare(channel)
        channel.basicQos(config.prefetch.toUShort(), false)

        logger.info(" [*] Consuming forecast refresh requests from '${config.forecastRefreshQueue}'")
        val consumer = channel.basicConsume(config.forecastRefreshQueue, noAck = false)

        consumeUntilConnectionCloses(connection) {
            for (delivery in consumer) {
                // Decoded inside the loop's own guard, not above it -- see `RabbitBatchEventBroker`:
                // an undecodable body thrown from here would take the whole consume loop with it.
                val message = try {
                    Json.decodeFromString<ForecastRefreshRequested>(delivery.message.body.decodeToString())
                } catch (e: Exception) {
                    logger.error("Undecodable forecast refresh request; dropping", e)
                    channel.basicNack(delivery.message, false, false)
                    continue
                }
                try {
                    block(message)
                    channel.basicAck(delivery.message, false)
                } catch (e: Exception) {
                    logger.error("Failed to handle $message; dropping", e)
                    channel.basicNack(delivery.message, false, false)
                }
            }
        }

        channel.close()
        connection.close()
    }

    private suspend fun declare(channel: AMQPChannel) {
        channel.exchangeDeclare(config.forecastRefreshExchange, BuiltinExchangeType.DIRECT, durable = true)
        channel.queueDeclare(
            config.forecastRefreshQueue,
            durable = true,
            exclusive = false,
            autoDelete = false,
            arguments = mapOf("x-queue-type" to Field.LongString("quorum"))
        )
        channel.queueBind(
            queue = config.forecastRefreshQueue,
            exchange = config.forecastRefreshExchange,
            routingKey = config.forecastRefreshQueue
        )
    }
}
