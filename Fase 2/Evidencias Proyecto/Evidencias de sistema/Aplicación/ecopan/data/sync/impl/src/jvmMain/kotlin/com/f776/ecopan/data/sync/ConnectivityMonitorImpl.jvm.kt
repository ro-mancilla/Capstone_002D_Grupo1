package com.f776.ecopan.data.sync

import com.f776.ecopan.domain.sync.ConnectivityMonitor
import com.f776.ecopan.domain.sync.ConnectivityStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop has no OS-level "am I online" callback worth depending on, so this periodically probes
 * the backend's `/health` endpoint with the already-injected Ktor [httpClient] instead (03 §6):
 * "desktop 'ONLINE' means 'backend reachable', which is actually the more useful signal for sync
 * anyway". Probe interval backs off from 30s to 2min while offline, per 03 §6.
 */
internal actual class ConnectivityMonitorImpl(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : ConnectivityMonitor {

    actual override fun observe(): Flow<ConnectivityStatus> = flow {
        while (true) {
            // Reachable means *answered*, whatever the status. A backend replying 500 is a failing
            // server, not a dropped network; calling it offline sent the indicator to "Sin
            // conexión" for a fault the sync engine reports in its own words.
            val reachable = runCatching {
                val response: HttpResponse = httpClient.get("$baseUrl/health")
                response.status.value > 0
            }.getOrDefault(false)

            emit(if (reachable) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE)
            delay(if (reachable) ONLINE_PROBE_INTERVAL else OFFLINE_PROBE_INTERVAL)
        }
    }.distinctUntilChanged()

    private companion object {
        val ONLINE_PROBE_INTERVAL = 30.seconds
        val OFFLINE_PROBE_INTERVAL = 2.minutes
    }
}
