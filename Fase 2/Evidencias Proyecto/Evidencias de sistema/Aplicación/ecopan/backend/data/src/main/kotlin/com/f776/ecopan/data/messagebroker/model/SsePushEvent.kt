package com.f776.ecopan.data.messagebroker.model

import kotlinx.serialization.Serializable

/**
 * Broadcast by the notifier to every app instance over the fanout exchange (02 §5). Each app
 * instance pushes it to whichever of its SSE connections belong to [tenantId] -- and, when
 * [branchId] is non-null, further filters out connections scoped to a *different* branch (a
 * tenant-wide connection with no branch of its own still receives every event).
 */
@Serializable
data class SsePushEvent(
    val tenantId: String,
    val branchId: String?,
    /** "heartbeat" | "batch-ready" | "sync-available". */
    val type: String,
    /** JSON blob with the event-specific details. */
    val payload: String
)
