package com.f776.ecopan.data.messagebroker

import com.f776.ecopan.data.messagebroker.model.SsePushEvent

/**
 * The notifier→app-instances hop: a fanout broadcast so every app node can push to its own SSE
 * connections. Best-effort (at-most-once) — the persisted tray is the durable source of truth.
 */
interface SsePushBroker {
    suspend fun publish(event: SsePushEvent)
    suspend fun consume(block: suspend (SsePushEvent) -> Unit)
}
