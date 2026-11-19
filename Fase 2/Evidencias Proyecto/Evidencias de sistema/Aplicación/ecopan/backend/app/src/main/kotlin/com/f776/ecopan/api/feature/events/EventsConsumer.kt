package com.f776.ecopan.api.feature.events

import com.f776.ecopan.api.feature.events.domain.SseConnectionRegistry
import com.f776.ecopan.data.messagebroker.SsePushBroker
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import kotlin.time.Duration.Companion.seconds

/**
 * Subscribes this app instance to the SSE-push fanout and pushes each event to its own live SSE
 * connections. Reconnects with backoff; if it dies, only *live* pushes stop -- a client reconnecting
 * still gets the snapshot-on-connect.
 */
fun Application.startEventsConsumer() {
    val broker by inject<SsePushBroker>()
    val registry by inject<SseConnectionRegistry>()

    launch {
        while (isActive) {
            try {
                broker.consume { push ->
                    registry.push(
                        push.tenantId,
                        push.branchId,
                        ServerSentEvent(event = push.type, data = push.payload)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error("SSE fanout consumer failed; reconnecting in 5s", e)
                delay(5.seconds)
            }
        }
    }
}
