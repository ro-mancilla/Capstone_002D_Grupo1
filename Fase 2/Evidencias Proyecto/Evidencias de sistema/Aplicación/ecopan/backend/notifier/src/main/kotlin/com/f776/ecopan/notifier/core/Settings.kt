package com.f776.ecopan.notifier.core

import com.f776.ecopan.data.core.BaseSettings
import com.f776.ecopan.data.core.BrokerBackend
import com.f776.ecopan.data.core.DatabaseSettings
import com.f776.ecopan.data.core.RabbitMqSettings
import com.f776.ecopan.data.core.ServiceBusSettings

class Settings : BaseSettings {
    override val databaseSettings: DatabaseSettings = DatabaseSettings(
        host = readEnv("DB_HOST", "db"),
        port = readEnv("DB_PORT", "5432").toInt(),
        name = readEnv("DB_NAME", "ecopan"),
        username = readEnv("DB_USERNAME", "postgres"),
        password = readEnv("DB_PASSWORD", "postgres"),
        driver = readEnv("DB_DRIVER", "org.postgresql.Driver")
    )

    override val brokerBackend: BrokerBackend = BrokerBackend.parse(readEnv("BROKER_BACKEND", ""))

    override val serviceBusSettings: ServiceBusSettings = ServiceBusSettings(
        connectionString = readEnv("SERVICE_BUS_CONNECTION_STRING", ""),
        productionQueue = readEnv("SERVICE_BUS_PRODUCTION_QUEUE", "production"),
        batchEventsQueue = readEnv("SERVICE_BUS_BATCH_EVENTS_QUEUE", "batch.events"),
        forecastRefreshQueue = readEnv("SERVICE_BUS_FORECAST_REFRESH_QUEUE", "forecast.refresh"),
        sseTopic = readEnv("SERVICE_BUS_SSE_TOPIC", "notifications.sse"),
        maxConcurrentCalls = readEnv("SERVICE_BUS_MAX_CONCURRENT_CALLS", "4").toInt(),
        prefetch = readEnv("SERVICE_BUS_PREFETCH", "0").toInt(),
        maxDeliveryCount = readEnv("SERVICE_BUS_MAX_DELIVERY_COUNT", "5").toInt(),
        sseSubscriptionIdleTtlMinutes = readEnv("SERVICE_BUS_SSE_SUBSCRIPTION_IDLE_TTL_MIN", "5").toLong(),
        provisionTopology = readEnv("SERVICE_BUS_PROVISION_TOPOLOGY", "false").toBoolean()
    )

    override val rabbitMqSettings: RabbitMqSettings = RabbitMqSettings(
        host = readEnv("RABBITMQ_HOST", "rabbitmq"),
        port = readEnv("RABBITMQ_PORT", "5672").toInt(),
        username = readEnv("RABBITMQ_USERNAME", "guest"),
        password = readEnv("RABBITMQ_PASSWORD", "guest"),
        exchange = readEnv("RABBITMQ_EXCHANGE", "production"),
        queue = readEnv("RABBITMQ_QUEUE", "production"),
        routingKey = readEnv("RABBITMQ_ROUTING_KEY", "production"),
        prefetch = readEnv("RABBITMQ_PREFETCH", "4").toInt(),
        deadLetterExchange = readEnv("RABBITMQ_DEAD_LETTER_EXCHANGE", "production.dlx"),
        deadLetterQueue = readEnv("RABBITMQ_DEAD_LETTER_QUEUE", "production.dlq"),
        batchEventsExchange = readEnv("RABBITMQ_BATCH_EVENTS_EXCHANGE", "batch.events"),
        batchEventsQueue = readEnv("RABBITMQ_BATCH_EVENTS_QUEUE", "batch.events"),
        forecastRefreshExchange = readEnv("RABBITMQ_FORECAST_REFRESH_EXCHANGE", "forecast.refresh"),
        forecastRefreshQueue = readEnv("RABBITMQ_FORECAST_REFRESH_QUEUE", "forecast.refresh"),
        sseExchange = readEnv("RABBITMQ_SSE_EXCHANGE", "notifications.sse"),
        sseDebounceMs = readEnv("RABBITMQ_SSE_DEBOUNCE_MS", "250").toLong()
    )

    private fun readEnv(path: String, default: String) =
        System.getenv(path) ?: default
}
