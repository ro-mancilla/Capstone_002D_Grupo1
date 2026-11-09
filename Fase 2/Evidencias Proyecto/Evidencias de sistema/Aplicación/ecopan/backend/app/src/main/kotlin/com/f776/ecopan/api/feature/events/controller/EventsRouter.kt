package com.f776.ecopan.api.feature.events.controller

import com.f776.ecopan.api.core.principalContext
import com.f776.ecopan.api.feature.events.domain.SseConnectionRegistry
import com.f776.ecopan.api.feature.events.domain.snapshotFrames
import com.f776.ecopan.api.feature.events.domain.snapshotSince
import com.f776.ecopan.data.database.repository.ProductionBatchRepository
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.routing
import io.ktor.server.sse.heartbeat
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.channels.Channel
import org.koin.ktor.ext.inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

fun Application.eventsRouter() {
    routing {
        val productionBatchRepository by inject<ProductionBatchRepository>()
        val registry by inject<SseConnectionRegistry>()

        // Live stream. Snapshot-on-connect heals anything missed while offline (DB is truth), then
        // events flow in via the registry until the client disconnects. Auth via `?access_token=`
        // query param -- browsers' EventSource can't set headers (02 §2).
        authenticate("auth-jwt") {
            sse("events/sse") {
                val context = call.principalContext()
                val tenantId = context.tenantId.toString()
                val branchId = context.branchId?.toString()

                heartbeat {
                    period = 15.seconds
                    event = ServerSentEvent(event = "heartbeat", data = "")
                }

                // Snapshot-on-connect: production batches from the last 24h (02 §5) so a
                // reconnecting POS/web client repaints its kitchen feed without a live push. Sent as
                // `batch-snapshot`, not `batch-ready`, so clients can tell a replay from a live push.
                val since = snapshotSince(Clock.System.now())
                snapshotFrames(productionBatchRepository.listReceivedSince(context.tenantId, since), branchId)
                    .forEach { send(it) }

                val channel = Channel<ServerSentEvent>(capacity = 64)
                registry.register(tenantId, branchId, channel)
                try {
                    for (event in channel) {
                        send(event)
                    }
                } finally {
                    registry.unregister(tenantId, channel)
                }
            }
        }
    }
}
