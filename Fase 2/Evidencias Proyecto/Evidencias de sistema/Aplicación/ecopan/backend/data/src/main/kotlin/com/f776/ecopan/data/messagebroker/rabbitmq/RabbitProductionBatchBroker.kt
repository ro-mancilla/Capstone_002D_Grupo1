package com.f776.ecopan.data.messagebroker.rabbitmq

import com.f776.ecopan.data.core.RabbitMqSettings
import com.f776.ecopan.data.messagebroker.ProductionBatchBroker
import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage
import dev.kourier.amqp.BuiltinExchangeType
import dev.kourier.amqp.Field
import dev.kourier.amqp.Properties
import dev.kourier.amqp.channel.AMQPChannel
import dev.kourier.amqp.connection.AMQPConfig
import dev.kourier.amqp.connection.amqpConfig
import dev.kourier.amqp.connection.createAMQPConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

internal class RabbitProductionBatchBroker(
    private val coroutineScope: CoroutineScope,
    private val jobDispatcher: CoroutineDispatcher,
    private val config: RabbitMqSettings
): ProductionBatchBroker {
    private val logger = LoggerFactory.getLogger(RabbitProductionBatchBroker::class.java)

    override suspend fun publish(message: ProductionBatchMessage) {
        val connection = createAMQPConnection(coroutineScope, amqpConfig())
        val channel = connection.openChannel()

        declareTopology(channel)

        channel.basicPublish(
            Json.encodeToString(message).toByteArray(),
            exchange = config.exchange,
            routingKey = config.routingKey,
            properties = Properties()
        )

        logger.info(" [x] Sent '$message'")

        channel.close()
        connection.close()
    }

    override suspend fun consume(block: suspend (ProductionBatchMessage) -> Unit) {
        val connection = createAMQPConnection(coroutineScope, amqpConfig())
        val channel = connection.openChannel()

        declareTopology(channel)

        // Only hand out `prefetch` unacked messages at a time. This is the backpressure lever
        // (RabbitMQ's equivalent of maxConcurrentCalls) and only takes effect with manual ack.
        channel.basicQos(config.prefetch.toUShort(), false)

        logger.info(" [*] Waiting for messages (prefetch=${config.prefetch}). To exit press CTRL+C")

        // Manual acknowledgement (noAck = false): a message is redelivered/dead-lettered on
        // failure instead of being lost the instant it is delivered.
        val consumer = channel.basicConsume(config.queue, noAck = false)

        consumeUntilConnectionCloses(connection) {
            for (delivery in consumer) {
                // Decoded inside the loop's own guard, not above it -- see `RabbitBatchEventBroker`:
                // an undecodable body thrown from here would take the whole consume loop with it, and
                // this is the queue carrying ingredient decrements. Dead-lettered like any other
                // failure, so the message is inspectable rather than gone.
                val message = try {
                    Json.decodeFromString<ProductionBatchMessage>(delivery.message.body.decodeToString())
                } catch (e: Exception) {
                    logger.error("Undecodable production batch message; dead-lettering", e)
                    channel.basicNack(delivery.message, multiple = false, requeue = false)
                    continue
                }
                logger.info("Received message: $message")

                // Dispatch the (blocking, CPU-bound) work onto a bounded pool so up to `prefetch`
                // messages process in parallel without starving this consume loop. Ack on success;
                // nack(requeue = false) routes a failed message to the dead-letter exchange.
                coroutineScope.launch(jobDispatcher) {
                    try {
                        block(message)
                        channel.basicAck(delivery.message, false)
                    } catch (e: Exception) {
                        logger.error("Processing failed for $message; dead-lettering", e)
                        channel.basicNack(delivery.message, multiple = false, requeue = false)
                    }
                }
            }
        }

        channel.close()
        connection.close()
    }

    /**
     * Declares the exchange/queue topology shared by producer and consumer. Both must declare
     * identical arguments or RabbitMQ rejects the redeclare with PRECONDITION_FAILED. The main
     * queue dead-letters nacked messages to [RabbitMqSettings.deadLetterExchange].
     */
    private suspend fun declareTopology(channel: AMQPChannel) {
        channel.exchangeDeclare(
            config.exchange,
            BuiltinExchangeType.DIRECT,
            durable = true
        )

        // Dead-letter exchange + queue: where messages land when processing nacks them.
        channel.exchangeDeclare(
            config.deadLetterExchange,
            BuiltinExchangeType.DIRECT,
            durable = true
        )
        channel.queueDeclare(
            config.deadLetterQueue,
            durable = true,
            exclusive = false,
            autoDelete = false,
            arguments = mapOf("x-queue-type" to Field.LongString("quorum"))
        )
        channel.queueBind(
            queue = config.deadLetterQueue,
            exchange = config.deadLetterExchange,
            routingKey = config.routingKey
        )

        // Main queue. Dead-lettered messages keep their original routing key, so the DLQ binding
        // above (same routing key) catches them.
        channel.queueDeclare(
            config.queue,
            durable = true,
            exclusive = false,
            autoDelete = false,
            arguments = mapOf(
                "x-queue-type" to Field.LongString("quorum"),
                "x-dead-letter-exchange" to Field.LongString(config.deadLetterExchange)
            )
        )
        channel.queueBind(
            queue = config.queue,
            exchange = config.exchange,
            routingKey = config.routingKey
        )
    }

    private fun amqpConfig(): AMQPConfig {
        return amqpConfig {
            server {
                this.host = config.host
                this.port = config.port
                this.user = config.username
                this.password = config.password
            }
        }
    }
}
