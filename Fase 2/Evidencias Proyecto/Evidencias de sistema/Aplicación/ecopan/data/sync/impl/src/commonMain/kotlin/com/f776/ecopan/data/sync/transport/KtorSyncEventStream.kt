package com.f776.ecopan.data.sync.transport

import com.f776.ecopan.domain.sync.SyncEventStream
import com.f776.ecopan.domain.sync.SyncStreamEvent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Ktor-client implementation of [SyncEventStream] against `GET /events/sse` (02 §5).
 *
 * **Auth is a query parameter, not a header** — deliberately, and matching the server: browsers'
 * `EventSource` cannot set headers, so `EventsRouter` authenticates `?access_token=`. This client
 * *could* send a header, but sending what the server actually reads keeps one code path for both
 * the web dashboard and this client rather than two auth branches on the route.
 *
 * **Reconnection is hand-rolled rather than the SSE plugin's `maxReconnectionAttempts`**, because
 * a dropped connection here usually means either "network went away" (retry forever, backing off)
 * or "the access token expired" (refresh, then retry) — a fixed attempt budget handles neither.
 * Each attempt re-reads the token, so a refresh performed by [SyncAuthProvider] is picked up
 * automatically on the next loop.
 *
 * Heartbeat frames (the server sends one every 15s) are dropped here: they exist to keep the
 * connection alive through proxies, and [SyncEventStream]'s contract promises collectors only
 * real events.
 */
internal class KtorSyncEventStream(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val authProvider: SyncAuthProvider,
    private val initialRetryDelay: Duration = 2.seconds,
    private val maxRetryDelay: Duration = 60.seconds,
) : SyncEventStream {

    override fun events(): Flow<SyncStreamEvent> = flow {
        var retryDelay = initialRetryDelay

        while (true) {
            val token = authProvider.currentAccessToken()
            if (token == null) {
                // Not enrolled yet (or the session was wiped). Nothing to authenticate with, so
                // wait rather than hammering the endpoint with connections that can only 401.
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
                continue
            }

            try {
                httpClient.sse("$baseUrl/events/sse?access_token=${token.encodeURLParameter()}") {
                    // Connected: the next drop should retry promptly, not inherit the backoff that
                    // got us here.
                    retryDelay = initialRetryDelay
                    incoming.collect { event ->
                        val type = event.event ?: return@collect
                        if (type == HEARTBEAT_EVENT) return@collect
                        val data = event.data ?: return@collect
                        emit(SyncStreamEvent(type = type, data = data))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Includes a 401 from an expired token: refreshing here means the next iteration
                // reconnects with a fresh one instead of backing off pointlessly.
                authProvider.refresh()
            }

            delay(retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(maxRetryDelay)
        }
    }

    private companion object {
        const val HEARTBEAT_EVENT = "heartbeat"
    }
}
