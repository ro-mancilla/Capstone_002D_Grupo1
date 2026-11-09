package com.f776.ecopan.data.messagebroker.rabbitmq

import dev.kourier.amqp.connection.AMQPConnection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select

/** The broker connection dropped under a consumer; the caller's reconnect loop should start over. */
internal class AmqpConnectionLostException(cause: Throwable?) : IllegalStateException("AMQP connection closed", cause)

/**
 * Runs a consume loop until it ends or [connection] closes. kourier's delivery channel does not end
 * when the broker goes away -- a consumer simply stops receiving, and after a broker restart the
 * queue sits with messages and no consumers. Racing the loop against `connectionClosed` turns that
 * into an [AmqpConnectionLostException], which every caller's reconnect loop already handles.
 */
internal suspend fun consumeUntilConnectionCloses(connection: AMQPConnection, consume: suspend () -> Unit) = coroutineScope {
    val work = async { consume() }
    select {
        work.onAwait { }
        connection.connectionClosed.onAwait { closed ->
            work.cancel()
            throw AmqpConnectionLostException(closed)
        }
    }
}
