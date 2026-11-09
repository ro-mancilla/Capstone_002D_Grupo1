package com.f776.ecopan.data.outbox

import com.f776.ecopan.data.database.repository.OutboxRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("com.f776.ecopan.data.outbox")

/**
 * Publishes what [OutboxRepository.enqueue] wrote, through the process's existing broker clients
 * (#136). [publishers] maps each topic this process owns to the call that sends its payload, e.g.
 * `PRODUCTION_BATCH to { productionBatchBroker.publish(json.decodeFromString(it)) }`, so the relay
 * works the same over RabbitMQ and Service Bus.
 *
 * At-least-once: a publish that succeeds but whose "sent" mark is lost is sent again, so consumers
 * stay idempotent (they already are, for broker redelivery). Every replica can run one: rows are
 * claimed with `SKIP LOCKED`, so two relays never publish the same row at the same time.
 */
class OutboxRelay(
    private val repository: OutboxRepository,
    private val publishers: Map<OutboxTopic, suspend (payload: String) -> Unit>,
    val interval: Duration = 1.seconds,
    private val batchSize: Int = 50,
) {
    /** One pass over the due rows; returns how many were published. */
    suspend fun relayOnce(): Int = repository.relayDue(batchSize, publishers.keys) { message ->
        val publish = checkNotNull(publishers[message.topic]) { "no publisher for ${message.topic} in this process" }
        publish(message.payload)
    }

    /** Relays until [scope] is cancelled: drains while there is work, then waits [interval]. */
    fun start(scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            val published = try {
                relayOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("outbox relay pass failed; retrying in {}", interval, e)
                0
            }
            if (published < batchSize) delay(interval)
        }
    }
}
