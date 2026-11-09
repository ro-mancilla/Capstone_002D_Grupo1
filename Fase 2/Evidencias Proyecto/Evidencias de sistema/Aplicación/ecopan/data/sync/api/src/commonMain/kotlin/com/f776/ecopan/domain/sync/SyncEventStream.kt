package com.f776.ecopan.domain.sync

import kotlinx.coroutines.flow.Flow

/** One frame off the backend's `GET /events/sse` stream (02 §5). [data] is the raw JSON body —
 *  decoding it is the consuming module's job, since only that module knows the payload shape. */
data class SyncStreamEvent(val type: String, val data: String)

/**
 * The client half of 02 §5 / 06 §6's SSE fan-out: a live push channel that complements — never
 * replaces — the pull loop. Pull remains the source of truth (it is what survives being offline);
 * this only shortens the latency between another device writing something and this device hearing
 * about it.
 *
 * Lives in `:data:sync:api` so consumers like `:data:production:impl` can subscribe without
 * depending on `:data:sync:impl`, exactly as [SyncApplier] does for the pull direction.
 *
 * The returned flow is **cold**: collecting opens a connection, cancelling closes it. Reconnection
 * and heartbeat frames are the implementation's problem, not the collector's — a collector sees
 * only real events, and sees them again after a reconnect gap only if the server replays them.
 */
interface SyncEventStream {
    fun events(): Flow<SyncStreamEvent>
}
