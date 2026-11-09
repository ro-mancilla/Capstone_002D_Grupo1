package com.f776.ecopan.data.messagebroker.servicebus

import com.f776.ecopan.data.core.ServiceBusSettings
import com.f776.ecopan.data.messagebroker.SsePushBroker
import com.f776.ecopan.data.messagebroker.model.SsePushEvent
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

/**
 * The notifier->app-instances fanout, on a Service Bus **topic**.
 *
 * This is the one hop where the two backends are not a like-for-like swap, so it is worth spelling
 * out. RabbitMQ models fanout as an exchange plus one exclusive, auto-delete queue per consumer:
 * the queue is created on connect and destroyed by the broker the moment that connection drops.
 * Service Bus models it as a topic plus one durable subscription per consumer, and a subscription
 * outlives the client that made it.
 *
 * So the lifecycle RabbitMQ gets for free is reconstructed here from two halves:
 *
 * 1. **Clean shutdown** deletes the subscription explicitly, in a `finally`.
 * 2. **Unclean shutdown** — SIGKILL, an App Service instance recycled out from under us — is
 *    covered by `autoDeleteOnIdle`, whose floor Azure sets at 5 minutes.
 *
 * Between a crash and that timer expiring, an orphaned subscription accrues pushes it will never
 * deliver. That costs nothing but a little broker storage, and every message in it carries a
 * 5-minute TTL, so the queue drains itself as fast as it fills. Acceptable precisely because this
 * hop is best-effort by design: the persisted notification tray, not the broker, is the durable
 * source of truth.
 */
internal class ServiceBusSsePushBroker(
    private val runtime: ServiceBusRuntime,
    private val config: ServiceBusSettings
) : SsePushBroker {

    private val logger = LoggerFactory.getLogger(ServiceBusSsePushBroker::class.java)

    override suspend fun publish(event: SsePushEvent) {
        runtime.ensureTopic(config.sseTopic)
        runtime.sendToTopic(config.sseTopic, Json.encodeToString(event))
    }

    override suspend fun consume(block: suspend (SsePushEvent) -> Unit) {
        runtime.ensureTopic(config.sseTopic)

        // One subscription per app instance, named after a fresh random id, so every instance sees
        // every broadcast and no shared state is needed to coordinate them. Subscription names cap
        // at 50 characters; "sse-" plus a UUID is 40.
        val subscription = "sse-${Uuid.random()}"
        runtime.createSubscription(config.sseTopic, subscription)
        logger.info(" [*] Consuming SSE push events on '{}/{}'", config.sseTopic, subscription)

        try {
            runtime.consumeSubscription(config.sseTopic, subscription, logger) { body ->
                block(Json.decodeFromString<SsePushEvent>(body))
            }
        } finally {
            // NonCancellable: this runs *because* we were cancelled, so without it the delete would
            // be cancelled too and every restart would leak a subscription for five minutes.
            withContext(NonCancellable) {
                runtime.deleteSubscription(config.sseTopic, subscription)
            }
        }
    }
}
