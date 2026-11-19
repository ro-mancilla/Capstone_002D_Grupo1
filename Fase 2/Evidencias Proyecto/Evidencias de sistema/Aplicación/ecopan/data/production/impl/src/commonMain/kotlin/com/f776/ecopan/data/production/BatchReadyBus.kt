package com.f776.ecopan.data.production

import com.f776.ecopan.domain.production.BatchReadyAnnouncement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.uuid.Uuid

/**
 * The single process-wide fan-out point for "a batch just became ready" (06 §6). Three producers
 * publish here: `ProductionRepositoryImpl.logBatch` (this device's own batches),
 * `ProductionBatchSyncApplier` (batches learned from the pull feed), and `BatchReadySseListener`
 * (the same batches, usually sooner, over SSE).
 *
 * It has to be its own Koin `single` rather than a field on `ProductionRepositoryImpl`, because
 * that repository is bound as a `factory` — every injection site gets a fresh instance, so a
 * `MutableSharedFlow` living inside it would give POS's banner collector a *different* flow from
 * the one the producers emit into, and nothing would ever arrive.
 *
 * **[publish] is de-duplicated by batch id**, which is load-bearing rather than defensive: SSE and
 * pull deliver the same batch by design (SSE for latency, pull for durability), and the server
 * replays the last 24h of batches on *every* SSE reconnect (`EventsRouter`'s snapshot-on-connect).
 * Without this, a flaky link would repaint the counter's banner stack on each reconnect.
 *
 * Public only because it appears in [com.f776.ecopan.data.production.sync.ProductionBatchSyncApplier]'s
 * constructor, which is itself public so cross-device sync tests can build the real applier. It is
 * not part of any `api` module and nothing outside `:data:production:impl` injects it — consumers
 * read the feed through [com.f776.ecopan.domain.production.ProductionRepository.batchReadyFeed].
 */
class BatchReadyBus {
    private val announcements = MutableSharedFlow<BatchReadyAnnouncement>(extraBufferCapacity = 16)

    /** Bounded so a long-lived till can't grow this without limit; FIFO eviction. A batch evicted
     *  after [MAX_REMEMBERED] later announcements could re-announce, which is harmless — by then
     *  it is hours old and long dismissed. */
    private val announced = LinkedHashSet<Uuid>()

    val feed: Flow<BatchReadyAnnouncement> = announcements.asSharedFlow()

    /**
     * Announces [announcement] unless this batch has already been announced in this process.
     * Returns whether it was actually emitted — callers use it to avoid duplicating side effects.
     *
     * Non-suspending by design: a slow or absent collector must never stall a batch write or a
     * sync apply. Drops rather than blocks once the buffer is full — the banner is informational
     * (05 §3 item 9), the durable record is the `production_batches` row.
     */
    fun publish(announcement: BatchReadyAnnouncement): Boolean {
        if (!announced.add(announcement.batchId)) return false
        if (announced.size > MAX_REMEMBERED) {
            announced.iterator().let { iterator ->
                iterator.next()
                iterator.remove()
            }
        }
        announcements.tryEmit(announcement)
        return true
    }

    private companion object {
        const val MAX_REMEMBERED = 512
    }
}
