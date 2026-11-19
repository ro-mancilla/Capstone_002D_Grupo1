package com.f776.ecopan.api.core

import com.f776.ecopan.data.core.BaseSettings
import com.f776.ecopan.data.core.BrokerBackend
import com.f776.ecopan.data.core.DatabaseSettings
import com.f776.ecopan.data.core.RabbitMqSettings
import com.f776.ecopan.data.core.ServiceBusSettings
import io.ktor.server.config.ApplicationConfig

/** App-only: worker/notifier don't need JWT/device-session/reporting-tz settings (02 §7). */
data class JwtSettings(
    val secret: String,
    val issuer: String,
    val audience: String,
    val accessTtlMinutes: Int,
    val refreshTtlDays: Int
)

class Settings(private val config: ApplicationConfig): BaseSettings {

    val jwtSettings: JwtSettings = JwtSettings(
        secret = usableJwtSecret(
            secret = readEnv("jwt.secret", ""),
            allowDevSecret = readEnv("jwt.allowDevSecret", "false").toBoolean(),
        ),
        issuer = readEnv("jwt.issuer", "ecopan"),
        audience = readEnv("jwt.audience", "ecopan-clients"),
        accessTtlMinutes = readEnv("jwt.accessTokenTtlMin", "15").toInt(),
        refreshTtlDays = readEnv("jwt.refreshTokenTtlDays", "30").toInt()
    )

    /** Device-session (till/baker install) refresh-token TTL (02 §1/§2). */
    val deviceSessionTtlDays: Int = readEnv("device.sessionTtlDays", "180").toInt()

    /** Fixed per-deployment reporting timezone for analytics "day" bucketing (02 §3/open Q2). */
    val reportingTz: String = readEnv("reporting.tz", "America/Santiago")

    /**
     * Comma-separated browser origins allowed to call this API (plan 07). Defaults cover Vite's dev
     * server and its `preview` build server, so `webApp` works against a fresh `docker compose up`
     * with no extra configuration. Deployments set `CORS_ALLOWED_ORIGINS` to their dashboard origin.
     */
    val corsAllowedOrigins: List<String> = readEnv("cors.allowedOrigins", "")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    /** Where product photos live and how their URLs are built -- see [StorageSettings]. */
    val storage: StorageSettings = run {
        val accountUrl = readEnv("storage.accountUrl", "").ifBlank { null }?.trimEnd('/')
        StorageSettings(
            connectionString = readEnv("storage.connectionString", ""),
            accountUrl = accountUrl,
            container = readEnv("storage.container", "product-images"),
            publicBlobBaseUrl = readEnv("storage.publicBlobBaseUrl", "").ifBlank { accountUrl ?: "http://localhost:10000/devstoreaccount1" }.trimEnd('/')
        )
    }

    /** Comma-separated profile names loaded idempotently at boot (01 §7, 02 §9 item 8). Empty = no seeding. */
    val seedProfiles: List<String> = readEnv("seed.profile", "")
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    override val databaseSettings: DatabaseSettings = DatabaseSettings(
        host = readEnv("database.host", "db"),
        port =readEnv("database.port", "5432").toInt(),
        name = readEnv("database.name", "ecopan"),
        username = readEnv("database.username", "postgres"),
        password = readEnv("database.password", "postgres"),
        driver = readEnv("database.driver", "org.postgresql.Driver")
    )

    override val brokerBackend: BrokerBackend = BrokerBackend.parse(readEnv("broker.backend", ""))

    override val serviceBusSettings: ServiceBusSettings = ServiceBusSettings(
        connectionString = readEnv("servicebus.connectionString", ""),
        productionQueue = readEnv("servicebus.productionQueue", "production"),
        batchEventsQueue = readEnv("servicebus.batchEventsQueue", "batch.events"),
        forecastRefreshQueue = readEnv("servicebus.forecastRefreshQueue", "forecast.refresh"),
        sseTopic = readEnv("servicebus.sseTopic", "notifications.sse"),
        maxConcurrentCalls = readEnv("servicebus.maxConcurrentCalls", "4").toInt(),
        prefetch = readEnv("servicebus.prefetch", "0").toInt(),
        maxDeliveryCount = readEnv("servicebus.maxDeliveryCount", "5").toInt(),
        sseSubscriptionIdleTtlMinutes = readEnv("servicebus.sseSubscriptionIdleTtlMin", "5").toLong(),
        provisionTopology = readEnv("servicebus.provisionTopology", "false").toBoolean()
    )

    override val rabbitMqSettings: RabbitMqSettings = RabbitMqSettings(
        host = readEnv("rabbitmq.host", "rabbitmq"),
        port = readEnv("rabbitmq.port", "5672").toInt(),
        username = readEnv("rabbitmq.username", "guest"),
        password = readEnv("rabbitmq.password", "guest"),
        exchange = readEnv("rabbitmq.exchange", "production"),
        queue = readEnv("rabbitmq.queue", "production"),
        routingKey = readEnv("rabbitmq.routingKey", "production"),
        prefetch = readEnv("rabbitmq.prefetch", "4").toInt(),
        deadLetterExchange = readEnv("rabbitmq.deadLetterExchange", "production.dlx"),
        deadLetterQueue = readEnv("rabbitmq.deadLetterQueue", "production.dlq"),
        batchEventsExchange = readEnv("rabbitmq.batchEventsExchange", "batch.events"),
        batchEventsQueue = readEnv("rabbitmq.batchEventsQueue", "batch.events"),
        forecastRefreshExchange = readEnv("rabbitmq.forecastRefreshExchange", "forecast.refresh"),
        forecastRefreshQueue = readEnv("rabbitmq.forecastRefreshQueue", "forecast.refresh"),
        sseExchange = readEnv("rabbitmq.sseExchange", "notifications.sse"),
        sseDebounceMs = readEnv("rabbitmq.sseDebounceMs", "250").toLong()
    )

    private fun readEnv(path: String, default: String) =
        config.propertyOrNull(path)?.getString() ?: default

}

/** The well-known placeholder in `.env.example` and application.yaml: anyone can mint tokens with it. */
private const val DEV_JWT_SECRET = "dev-secret"

/**
 * #128: refuse to start rather than sign tokens with no secret or the published development one.
 * Local stacks opt in with `JWT_ALLOW_DEV_SECRET=true` (docker-compose.yml sets it); Azure sets a
 * real `JWT_SECRET` and never the flag.
 */
private fun usableJwtSecret(secret: String, allowDevSecret: Boolean): String {
    if (allowDevSecret) return secret.ifBlank { DEV_JWT_SECRET }
    check(secret.isNotBlank() && secret != DEV_JWT_SECRET) {
        "jwt.secret (JWT_SECRET) is unset or the development value '$DEV_JWT_SECRET'. Set a real secret, " +
            "or JWT_ALLOW_DEV_SECRET=true for local development only."
    }
    return secret
}

/**
 * Object-storage coordinates for `AzureBlobObjectStorage`.
 *
 * Exactly one of [accountUrl] / [connectionString] is used: [accountUrl] (`STORAGE_ACCOUNT_URL`,
 * e.g. `https://ecopanst….blob.core.windows.net`) selects the managed-identity path and is what
 * the Azure deployment sets; [connectionString] (`STORAGE_CONNECTION_STRING`) is the Azurite path
 * docker-compose and local runs use.
 *
 * [publicBlobBaseUrl] (`STORAGE_PUBLIC_BLOB_BASE_URL`) is the account root *as browsers and devices
 * reach it*; photo URLs and upload URLs are minted from it. In Azure it is the account URL itself
 * (the default when [accountUrl] is set). Under compose the app signs through `storage:10000` but
 * a browser needs `localhost:10000`, hence the separate setting -- and an Android emulator needs
 * `adb reverse tcp:10000 tcp:10000` next to the one it already has for 8080 (README §4).
 */
data class StorageSettings(
    val connectionString: String,
    val accountUrl: String?,
    val container: String,
    val publicBlobBaseUrl: String
)
