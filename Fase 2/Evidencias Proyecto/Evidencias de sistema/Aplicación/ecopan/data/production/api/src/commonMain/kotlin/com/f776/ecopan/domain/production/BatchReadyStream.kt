package com.f776.ecopan.domain.production

/**
 * Session-scoped lifecycle for the live batch-ready subscription (06 §6), started and stopped from
 * `:composeApp` alongside `SyncScheduler` and on the same signal (a cached tenant = an enrolled
 * device). Mirrors `SyncScheduler`'s shape deliberately: both are long-lived background consumers
 * that only make sense once the device holds a session, and both must be idempotent so a repeated
 * start is a no-op rather than a second connection.
 *
 * Purely a latency optimization. Stopping it — or never starting it — costs nothing but freshness:
 * batches still arrive on the pull feed, which is the source of truth.
 */
interface BatchReadyStream {
    fun start()
    fun stop()
}
