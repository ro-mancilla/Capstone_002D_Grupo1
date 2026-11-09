package com.f776.ecopan.data.core

interface BaseSettings {

    val databaseSettings: DatabaseSettings

    /** Which family of broker implementations Koin binds. See [BrokerBackend]. */
    val brokerBackend: BrokerBackend

    val rabbitMqSettings: RabbitMqSettings

    val serviceBusSettings: ServiceBusSettings
}

/**
 * The broker the four `messagebroker` interfaces are bound to at runtime.
 *
 * Both families implement identical semantics; only the plumbing differs. The choice is
 * deployment-shaped, not architectural:
 *
 * - [RABBITMQ] — the default, and what `docker compose up` runs. Self-hosted, free, works offline.
 * - [SERVICE_BUS] — Azure's managed broker. No container to run, host or patch, which is what lets
 *   the Azure deployment sit on App Service (see `infra/README.md`): App Service can expose exactly
 *   one HTTP port per container, so a self-hosted broker listening on AMQP 5672 cannot live there.
 */
enum class BrokerBackend {
    RABBITMQ,
    SERVICE_BUS;

    companion object {
        /** Tolerant parse for env vars: `service_bus`, `SERVICE-BUS` and `servicebus` all work. */
        fun parse(raw: String?): BrokerBackend {
            val normalised = raw?.trim()?.uppercase()?.replace('-', '_')?.replace(" ", "") ?: ""
            return when (normalised) {
                "SERVICE_BUS", "SERVICEBUS", "AZURE_SERVICE_BUS", "AZURESERVICEBUS" -> SERVICE_BUS
                "RABBITMQ", "RABBIT_MQ", "RABBIT", "" -> RABBITMQ
                else -> throw IllegalArgumentException(
                    "Unknown broker backend '$raw'. Expected 'rabbitmq' or 'service_bus'."
                )
            }
        }
    }
}

data class DatabaseSettings(
    val host: String,
    val port: Int,
    val name: String,
    val username: String,
    val password: String,
    val driver: String,
    val url: String = "jdbc:postgresql://$host:$port/$name?user=$username&password=$password"
)

data class RabbitMqSettings(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val exchange: String,
    val queue: String,
    val routingKey: String,
    val prefetch: Int,
    val deadLetterExchange: String,
    val deadLetterQueue: String,
    val batchEventsExchange: String,
    val batchEventsQueue: String,
    val forecastRefreshExchange: String,
    val forecastRefreshQueue: String,
    val sseExchange: String,
    val sseDebounceMs: Long
)

/**
 * Azure Service Bus equivalents of the RabbitMQ topology above.
 *
 * Notice what is *missing* relative to [RabbitMqSettings]: there is no exchange, no routing key and
 * no dead-letter queue. Service Bus has no exchange concept — a sender addresses a queue or topic
 * directly — and every queue and subscription ships with a built-in dead-letter sub-queue, so the
 * `production.dlx` / `production.dlq` pair that RabbitMQ needs declared by hand does not exist
 * here. [maxDeliveryCount] replaces it: the broker dead-letters a message automatically once it has
 * been delivered that many times, which also covers the case RabbitMQ's manual nack cannot — a
 * consumer that crashes mid-message and never nacks at all.
 */
data class ServiceBusSettings(
    /**
     * `Endpoint=sb://<ns>.servicebus.windows.net/;SharedAccessKeyName=...;SharedAccessKey=...`.
     *
     * A namespace-level SAS connection string rather than a managed identity, matching the ACR
     * admin-user tradeoff already recorded in `infra/README.md`: managed identity needs a role
     * assignment, role assignment needs Owner/User Access Administrator, and Azure for Students
     * subscriptions frequently do not have it. Empty when [BrokerBackend.RABBITMQ] is selected.
     */
    val connectionString: String,
    val productionQueue: String,
    val batchEventsQueue: String,
    val forecastRefreshQueue: String,
    /** A *topic*, not a queue: SSE pushes fan out to every app instance (see the SSE broker). */
    val sseTopic: String,
    /** Concurrent invocations of the message handler; the analogue of AMQP's basicQos prefetch. */
    val maxConcurrentCalls: Int,
    /**
     * Messages pulled to the client ahead of demand. Left at 0 (off) by default: the SDK's own
     * troubleshooting guide warns that a high prefetch combined with a high `maxConcurrentCalls`
     * can starve the reactor thread pool and stall the client outright.
     */
    val prefetch: Int,
    /** Deliveries before the broker dead-letters a message by itself. */
    val maxDeliveryCount: Int,
    /**
     * Idle time after which a per-instance SSE subscription self-destructs. Azure enforces a
     * 5-minute floor. This is the backstop for an app instance that dies without cleaning up.
     */
    val sseSubscriptionIdleTtlMinutes: Long,
    /**
     * Create queues/topics from the app at startup if absent. Bicep already declares them, so this
     * is off in Azure and on for a local Service Bus emulator or a hand-made namespace. The
     * per-instance SSE subscription is always created at runtime regardless — it cannot be
     * declared ahead of time, because its name is the instance's own random id.
     */
    val provisionTopology: Boolean
)