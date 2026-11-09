package com.f776.ecopan.api.feature.events.domain

import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-instance registry of live SSE connections, keyed by **tenant** (02 §5 -- batch-ready is a
 * tenant/branch broadcast, not per-user). The SSE route registers a channel on connect and drops it
 * on disconnect; the fanout consumer pushes events into the matching channels. Each app node only
 * knows about its own connections -- the broadcast bus reaches the rest.
 */
class SseConnectionRegistry {

    private class Connection(val branchId: String?, val channel: Channel<ServerSentEvent>)

    private val connections = ConcurrentHashMap<String, MutableSet<Connection>>()

    fun register(tenantId: String, branchId: String?, channel: Channel<ServerSentEvent>) {
        connections.computeIfAbsent(tenantId) { ConcurrentHashMap.newKeySet() }.add(Connection(branchId, channel))
    }

    fun unregister(tenantId: String, channel: Channel<ServerSentEvent>) {
        connections[tenantId]?.let { set ->
            set.removeIf { it.channel === channel }
            if (set.isEmpty()) connections.remove(tenantId)
        }
        channel.close()
    }

    /**
     * Best-effort push to every live connection for [tenantId]. When [eventBranchId] is non-null,
     * a connection scoped to a *different* branch is skipped; a tenant-wide connection (no branch
     * of its own) always receives the event regardless of [eventBranchId] (02 §5).
     */
    fun push(tenantId: String, eventBranchId: String?, event: ServerSentEvent) {
        connections[tenantId]?.forEach { connection ->
            if (connection.branchId == null || eventBranchId == null || connection.branchId == eventBranchId) {
                connection.channel.trySend(event)
            }
        }
    }
}
