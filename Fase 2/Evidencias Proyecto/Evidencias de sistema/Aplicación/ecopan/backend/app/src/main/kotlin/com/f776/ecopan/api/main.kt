package com.f776.ecopan.api

import com.f776.ecopan.api.core.PermissionRegistry
import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureCors
import com.f776.ecopan.api.core.configureHttp
import com.f776.ecopan.api.core.configureKoin
import com.f776.ecopan.api.core.configureLogging
import com.f776.ecopan.api.core.configureRateLimit
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.core.configureRequestValidation
import com.f776.ecopan.api.core.configureSse
import com.f776.ecopan.api.core.storage.ObjectStorage
import com.f776.ecopan.api.core.storage.StorageUnavailableException
import com.f776.ecopan.api.feature.events.startEventsConsumer
import com.f776.ecopan.api.seed.SeedLoader
import com.f776.ecopan.data.database.connectToDb
import com.f776.ecopan.data.database.repository.OutboxRepository
import com.f776.ecopan.data.database.repository.RolePermissionRepository
import com.f776.ecopan.data.messagebroker.ProductionBatchBroker
import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage
import com.f776.ecopan.data.outbox.OutboxRelay
import com.f776.ecopan.data.outbox.OutboxTopic
import kotlinx.serialization.json.Json
import io.ktor.server.application.*
import io.ktor.server.netty.EngineMain
import org.koin.ktor.ext.inject

fun main(args: Array<String>) {
    EngineMain.main(args)
}

suspend fun Application.modules() {
    configureKoin()
    configureLogging()
    // Before auth/routing: a rejected preflight must still carry CORS headers, and an OPTIONS
    // preflight carries no Authorization header, so anything that 401s first would break it.
    configureCors()
    configureRateLimit()
    configureAuth()
    configureHttp()
    configureSerialization()
    configureSse()
    configureRequestValidation()
    configureRouting()

    val settings by inject<Settings>()
    connectToDb(settings.databaseSettings, init = true)

    // Role -> permission grid, loaded once at startup (01 §4, 02 §2).
    val rolePermissionRepository by inject<RolePermissionRepository>()
    PermissionRegistry.refresh(rolePermissionRepository)

    // Boot-time-only seed loader (01 §7, 02 §9 item 8) -- idempotent, safe to run every boot.
    val seedLoader by inject<SeedLoader>()
    seedLoader.loadAll(settings.seedProfiles)

    // Local only (no-op under managed identity): make the Azurite container browser-usable.
    // Best-effort -- a backend with no photo store still sells bread; the first photo upload
    // will report the 503 if the emulator is still down.
    val objectStorage by inject<ObjectStorage>()
    try {
        objectStorage.provisionForDevelopment(settings.corsAllowedOrigins)
    } catch (e: StorageUnavailableException) {
        log.warn("Object storage not provisioned at boot: {}", e.message)
    }

    // Bridge the SSE-push fanout to this instance's live SSE connections.
    startEventsConsumer()

    // Publishes the production batches the sync push wrote to the outbox (#142). Every replica runs
    // one; SKIP LOCKED keeps them from publishing the same row.
    val outbox by inject<OutboxRepository>()
    val productionBatchBroker by inject<ProductionBatchBroker>()
    OutboxRelay(
        outbox,
        mapOf(OutboxTopic.PRODUCTION_BATCH to { payload -> productionBatchBroker.publish(Json.decodeFromString<ProductionBatchMessage>(payload)) }),
    ).start(this)
}