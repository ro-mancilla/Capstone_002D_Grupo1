package com.f776.ecopan.data.messagebroker.servicebus

import com.azure.core.exception.ResourceExistsException
import com.azure.messaging.servicebus.ServiceBusClientBuilder
import com.azure.messaging.servicebus.ServiceBusErrorContext
import com.azure.messaging.servicebus.ServiceBusException
import com.azure.messaging.servicebus.ServiceBusFailureReason
import com.azure.messaging.servicebus.ServiceBusMessage
import com.azure.messaging.servicebus.ServiceBusProcessorClient
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext
import com.azure.messaging.servicebus.ServiceBusSenderClient
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder
import com.azure.messaging.servicebus.administration.models.CreateQueueOptions
import com.azure.messaging.servicebus.administration.models.CreateSubscriptionOptions
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode
import com.f776.ecopan.data.core.ServiceBusSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

/**
 * Shared Service Bus plumbing for the four broker implementations in this package.
 *
 * Everything that is identical between them lives here: one builder (and therefore one AMQP 1.0
 * connection multiplexed across all senders and receivers), a sender cache, idempotent topology
 * provisioning, and the bridge from the SDK's callback-driven processor to the `suspend fun
 * consume(block)` shape the [com.f776.ecopan.data.messagebroker] interfaces are written in.
 *
 * One instance is held as a Koin singleton, so the connection outlives individual publishes. That
 * is a deliberate difference from the RabbitMQ implementations, which open and tear down a
 * connection per publish: the Azure SDK explicitly documents its clients as long-lived objects
 * because each one costs a TLS handshake and an AMQP session to establish.
 */
internal class ServiceBusRuntime(private val settings: ServiceBusSettings) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(ServiceBusRuntime::class.java)

    private val builder: ServiceBusClientBuilder by lazy {
        require(settings.connectionString.isNotBlank()) {
            "SERVICE_BUS_CONNECTION_STRING is empty but the broker backend is SERVICE_BUS."
        }
        ServiceBusClientBuilder().connectionString(settings.connectionString)
    }

    private val admin: ServiceBusAdministrationClient by lazy {
        ServiceBusAdministrationClientBuilder()
            .connectionString(settings.connectionString)
            .buildClient()
    }

    private val senders = ConcurrentHashMap<String, ServiceBusSenderClient>()

    /**
     * Publishes [json] to a queue. Blocking, but a single AMQP frame on an already-open connection
     * — the same cost profile as the kourier `basicPublish` it replaces.
     */
    fun sendToQueue(queue: String, json: String) =
        send(queue, json) { builder.sender().queueName(queue).buildClient() }

    /** Publishes [json] to a topic, fanning it out to every current subscription. */
    fun sendToTopic(topic: String, json: String) =
        send(topic, json) { builder.sender().topicName(topic).buildClient() }

    /**
     * The sender builder has separate `queueName`/`topicName` setters and no shared one, hence
     * [build]. Senders are cached per entity and are thread-safe.
     */
    private inline fun send(
        entity: String,
        json: String,
        crossinline build: () -> ServiceBusSenderClient
    ) {
        senders.computeIfAbsent(entity) { build() }.sendMessage(ServiceBusMessage(json))
    }

    // -----------------------------------------------------------------------------------------
    // Topology
    // -----------------------------------------------------------------------------------------

    /**
     * Creates a work queue if it is absent. Mirrors what each RabbitMQ implementation does in its
     * own `declare()`: producer and consumer both provision, so whichever service starts first
     * makes the entity and the other finds it already there.
     *
     * No-op when `provisionTopology` is false, which is the Azure case — `infra/apps.bicep`
     * declares the queues, and letting infrastructure own infrastructure means the app's SAS key
     * does not need Manage rights in production.
     */
    fun ensureQueue(name: String) {
        if (!settings.provisionTopology) return
        ifAbsent("queue '$name'") {
            admin.createQueue(
                name,
                CreateQueueOptions()
                    .setMaxDeliveryCount(settings.maxDeliveryCount)
                    // 5 minutes is the Service Bus maximum. Generous on purpose: the lock is held
                    // for the whole of the handler, and a formula decrement touches several rows.
                    .setLockDuration(Duration.ofMinutes(5))
                    .setDeadLetteringOnMessageExpiration(true)
            )
        }
    }

    fun ensureTopic(name: String) {
        if (!settings.provisionTopology) return
        ifAbsent("topic '$name'") { admin.createTopic(name) }
    }

    /**
     * Creates the caller's private subscription on [topic] — the Service Bus counterpart of
     * RabbitMQ's exclusive, auto-delete queue bound to a fanout exchange.
     *
     * The mapping is close but not exact, and the gap is worth stating. RabbitMQ deletes an
     * exclusive queue the instant its connection drops. Service Bus has no such trigger, so the
     * subscription is deleted explicitly in [deleteSubscription] on clean shutdown, and
     * `autoDeleteOnIdle` cleans up after an unclean one. Azure's minimum for that is 5 minutes, so
     * a hard-killed instance leaves a subscription accruing messages for up to that long before it
     * disappears. Harmless here: these are best-effort SSE pushes with the persisted tray as the
     * durable source of truth, and the subscription is deleted before it can grow.
     */
    fun createSubscription(topic: String, subscription: String) {
        admin.createSubscription(
            topic,
            subscription,
            CreateSubscriptionOptions()
                .setAutoDeleteOnIdle(Duration.ofMinutes(settings.sseSubscriptionIdleTtlMinutes))
                // Nothing should linger: an SSE push that is minutes stale is worse than no push.
                .setDefaultMessageTimeToLive(Duration.ofMinutes(5))
        )
    }

    fun deleteSubscription(topic: String, subscription: String) {
        runCatching { admin.deleteSubscription(topic, subscription) }
            .onFailure { logger.warn("Could not delete SSE subscription '$subscription'", it) }
    }

    /**
     * Runs [create] and swallows the "already exists" outcome.
     *
     * Deliberately not a `getQueueExists()` check first: two services starting together would both
     * see "absent" and both create, and the loser gets this exception anyway. Attempting the create
     * and treating the conflict as success is the only genuinely race-free version.
     */
    private inline fun ifAbsent(what: String, create: () -> Unit) {
        try {
            create()
            logger.info("Created Service Bus $what")
        } catch (_: ResourceExistsException) {
            logger.debug("Service Bus $what already exists")
        }
    }

    // -----------------------------------------------------------------------------------------
    // Consuming
    // -----------------------------------------------------------------------------------------

    /**
     * Consumes a work queue at-least-once until the calling coroutine is cancelled.
     *
     * [ServiceBusProcessorClient] is the SDK's push-based consumer — the closest thing outside
     * Azure Functions to a "Service Bus trigger": you register a callback, the client owns the
     * receive loop, the lock renewal and the reconnects. That is the whole reason to prefer it over
     * a hand-rolled `ServiceBusReceiverClient` loop, which is what the kourier code has to be.
     */
    suspend fun consumeQueue(queue: String, logger: Logger, handle: suspend (String) -> Unit) {
        runProcessor(logger, settle = true, handle = handle) { onMessage, onError ->
            builder.processor()
                .queueName(queue)
                .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
                // Opt in to manual settlement so a failure dead-letters instead of vanishing —
                // the same reason the AMQP consumers pass noAck = false.
                .disableAutoComplete()
                .maxConcurrentCalls(settings.maxConcurrentCalls)
                .prefetchCount(settings.prefetch)
                .processMessage(onMessage)
                .processError(onError)
                .buildProcessorClient()
        }
    }

    /**
     * Consumes a topic subscription at-most-once until cancelled.
     *
     * `RECEIVE_AND_DELETE` is the counterpart of `noAck = true` on the AMQP side: the broker
     * considers the message settled the moment it is handed over. Correct for SSE fanout, where a
     * redelivered stale push is worse than a dropped one.
     */
    suspend fun consumeSubscription(
        topic: String,
        subscription: String,
        logger: Logger,
        handle: suspend (String) -> Unit
    ) {
        runProcessor(logger, settle = false, handle = handle) { onMessage, onError ->
            builder.processor()
                .topicName(topic)
                .subscriptionName(subscription)
                .receiveMode(ServiceBusReceiveMode.RECEIVE_AND_DELETE)
                .maxConcurrentCalls(settings.maxConcurrentCalls)
                .prefetchCount(settings.prefetch)
                .processMessage(onMessage)
                .processError(onError)
                .buildProcessorClient()
        }
    }

    private suspend fun runProcessor(
        logger: Logger,
        settle: Boolean = true,
        handle: suspend (String) -> Unit,
        build: (
            Consumer<ServiceBusReceivedMessageContext>,
            Consumer<ServiceBusErrorContext>
        ) -> ServiceBusProcessorClient
    ) {
        // Completed exceptionally only by a misconfiguration the SDK cannot retry its way out of.
        // Until then it never completes, so awaiting it is how this function "runs forever" while
        // staying cancellable.
        val fatal = CompletableDeferred<Nothing>()

        val onMessage = Consumer<ServiceBusReceivedMessageContext> { context ->
            // The SDK invokes this on one of its own threads, one per concurrent call, and treats
            // the return as "handled". Bridging a suspend handler therefore means blocking that
            // thread — which is exactly the intent: `maxConcurrentCalls` is the backpressure
            // bound, the same role AMQP's prefetch plays in the kourier implementations.
            runBlocking {
                try {
                    handle(context.message.body.toString())
                    if (settle) context.complete()
                } catch (failure: Exception) {
                    logger.error("Processing failed; dead-lettering", failure)
                    // Matches basicNack(requeue = false): straight to the dead-letter sub-queue,
                    // no redelivery. maxDeliveryCount still covers the crash-before-settle case.
                    if (settle) {
                        runCatching { context.deadLetter() }
                            .onFailure { logger.error("Dead-lettering failed", it) }
                    }
                }
            }
        }

        val onError = Consumer<ServiceBusErrorContext> { errorContext ->
            val cause = errorContext.exception
            val reason = (cause as? ServiceBusException)?.reason
            if (reason in TERMINAL_FAILURES) {
                // Retrying will never help: the entity is missing or the key is wrong. Surface it
                // so the caller's reconnect loop logs something actionable instead of spinning.
                logger.error("Unrecoverable Service Bus error ($reason)", cause)
                fatal.completeExceptionally(cause)
            } else {
                // Everything else — transient network faults, lock lost, throttling — is the
                // client's own business; it reconnects underneath us.
                logger.warn("Transient Service Bus error from ${errorContext.errorSource}", cause)
            }
        }

        val client = build(onMessage, onError)
        try {
            client.start()
            fatal.await()
        } finally {
            // close() waits for in-flight handlers. Under NonCancellable so shutdown actually
            // completes them rather than being cut off mid-transaction; anything still unsettled
            // loses its lock and is redelivered, which is what at-least-once buys us.
            withContext(NonCancellable) { client.close() }
        }
    }

    override fun close() {
        senders.values.forEach { runCatching { it.close() } }
        senders.clear()
    }

    private companion object {
        val TERMINAL_FAILURES = setOf(
            ServiceBusFailureReason.MESSAGING_ENTITY_NOT_FOUND,
            ServiceBusFailureReason.UNAUTHORIZED
        )
    }
}
