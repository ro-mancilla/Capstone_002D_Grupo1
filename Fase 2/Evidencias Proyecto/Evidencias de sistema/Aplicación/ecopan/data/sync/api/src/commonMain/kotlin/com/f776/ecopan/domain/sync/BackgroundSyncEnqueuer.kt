package com.f776.ecopan.domain.sync

/**
 * Asks the OS to flush the outbox after the app stops running (03 §7's Android hardening item).
 *
 * 03 §7 is explicit that the product promises **no** background-OS sync guarantees — sync happens
 * while the app is alive, which is the honest capability on iOS and desktop regardless. This is
 * therefore hardening, not a correctness dependency: on Android it buys a best-effort flush of a
 * till that was backgrounded mid-shift with unsynced sales, and everywhere else it is a documented
 * no-op. Nothing may be built to depend on it having run.
 */
interface BackgroundSyncEnqueuer {
    /** Enqueues one constrained, best-effort flush. Safe to call repeatedly — implementations
     *  de-duplicate, so a user toggling in and out of the app doesn't queue a pile of work. */
    fun enqueueOutboxFlush()
}

/** iOS and JVM desktop: 03 §7 — "the common coroutine loop only. No BGTaskScheduler (not worth the
 *  review/complexity budget; document as a limitation)." This object *is* that documentation. */
object NoOpBackgroundSyncEnqueuer : BackgroundSyncEnqueuer {
    override fun enqueueOutboxFlush() = Unit
}
