package com.f776.ecopan.data.sync

import com.f776.ecopan.domain.sync.ConnectivityMonitor
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.sync.SyncScheduler
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.unigrid.core.database.dao.OutboxDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The common (all-platforms) sync loop (03 §7): reacts to (a) connectivity edges -- including the
 * very first observed value, which is what drives the "start on session-ready/foreground" case,
 * since a fresh [ConnectivityMonitor.observe] collector always emits its first status regardless
 * of [distinctUntilChanged] -- (b) the outbox becoming non-empty (Room `Flow` on pending count),
 * and (c) a periodic tick every [tickInterval] (default 5 min, 03 §7) while alive. [start]/[stop]
 * are idempotent; [stop] cancels the loop outright.
 *
 * Single-flighted via [mutex]`.tryLock` rather than `withLock`: [SyncEngineImpl.syncNow] already
 * has its own mutex for *correctness* (it's always safe to call concurrently), so this one exists
 * purely to *coalesce* -- a burst of triggers (connectivity flapping while the outbox is also
 * non-empty) collapses into "at most one more sync after the current one finishes" instead of
 * queueing up a redundant call per trigger.
 */
internal class SyncSchedulerImpl(
    private val syncEngine: SyncEngine,
    private val connectivityMonitor: ConnectivityMonitor,
    private val outboxDao: OutboxDao,
    private val scope: CoroutineScope,
    private val tickInterval: Duration = 5.minutes,
) : SyncScheduler {

    private val mutex = Mutex()
    private var job: Job? = null

    /**
     * Mirror of [SyncEngine.status], because the interface exposes a plain `Flow` and [triggerSync]
     * needs the *current* phase synchronously, without suspending on a collection of its own.
     */
    private var lastStatus: SyncStatus = SyncStatus.Idle(ConnectivityStatus.ONLINE)

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            launch { syncEngine.status.collect { lastStatus = it } }
            merge(
                connectivityOnlineEdges().map { Trigger.CONNECTIVITY },
                outboxBecameNonEmpty().map { Trigger.OUTBOX },
                periodicTicks().map { Trigger.TICK },
            ).collect { triggerSync(it) }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun triggerSync(trigger: Trigger) {
        // A dead device session is terminal by the engine's own reckoning ("only re-enrollment
        // fixes it"), so reacting to edge triggers while in that state is not a retry — it is a
        // hot loop, and it was measured at ~7.6 requests per second against the backend,
        // indefinitely, on a device whose refresh token had expired.
        //
        // The loop closes through the outbox trigger: a failing push marks its batch in flight
        // (pending count -> 0), fails, and reverts it (0 -> N). That is a rising edge
        // indistinguishable from a genuinely new mutation, so the failure re-triggers itself.
        // Blocking edge triggers breaks it while leaving the periodic tick to retry, which is
        // what lets the device recover on its own once someone logs back in.
        if (trigger != Trigger.TICK && lastStatus is SyncStatus.AuthRequired) return
        if (!mutex.tryLock()) return
        try {
            syncEngine.syncNow()
        } finally {
            mutex.unlock()
        }
    }

    /** Which of the three signals in [start]'s merge woke the loop — see [triggerSync]. */
    private enum class Trigger { CONNECTIVITY, OUTBOX, TICK }

    private fun connectivityOnlineEdges(): Flow<Unit> = connectivityMonitor.observe()
        .distinctUntilChanged()
        .filter { it == ConnectivityStatus.ONLINE }
        .map {}

    private fun outboxBecameNonEmpty(): Flow<Unit> = outboxDao.observePendingCount()
        .distinctUntilChanged()
        .filter { it > 0 }
        .map {}

    private fun periodicTicks(): Flow<Unit> = flow {
        while (currentCoroutineContext().isActive) {
            delay(tickInterval)
            emit(Unit)
        }
    }
}
