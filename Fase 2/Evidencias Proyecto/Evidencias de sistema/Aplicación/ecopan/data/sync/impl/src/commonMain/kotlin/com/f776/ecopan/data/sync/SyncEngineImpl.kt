package com.f776.ecopan.data.sync

import com.f776.ecopan.data.sync.pull.PullWorker
import com.f776.ecopan.data.sync.push.PushOutcome
import com.f776.ecopan.data.sync.push.PushPhase
import com.f776.ecopan.data.sync.push.PushWorker
import com.f776.ecopan.data.sync.transport.SyncAuthException
import com.f776.ecopan.domain.sync.BootstrapResult
import com.f776.ecopan.domain.sync.ConnectivityMonitor
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncEngine
import com.f776.ecopan.domain.sync.SyncError
import com.f776.ecopan.domain.sync.SyncResult
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.unigrid.core.database.dao.OutboxDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Instant
import kotlin.concurrent.Volatile

/**
 * Drives [PushWorker]/[PullWorker] end to end (03 work items 4/5/7). [status] is a hot,
 * replay-1 [StateFlow] (readable synchronously via `.value` with no active collector, per the
 * indicator's requirement, 03 §6.1) reporting every phase the plan's tests observe (`Idle` →
 * `Syncing` → `Pending` → ... → `Idle`, 03 §9 T5) because [PushWorker.drainAll]'s `onPhase`
 * callback already distinguishes them.
 *
 * Between [syncNow]/[bootstrap] calls (i.e. whenever [mutex] is free), a background collector
 * keeps the *connectivity* and *pending-count* facets of [status] fresh reactively -- otherwise an
 * offline device with a growing outbox would show a stale `Idle` until the next sync attempt fired
 * (03 §6.1: the indicator "must be visible at all times"). It deliberately never clobbers a sticky
 * `AuthRequired`/`Error` phase (those only clear via an actual successful [syncNow]) and, while
 * [mutex] is held, leaves [status] to the in-flight call -- with one exception: a push being
 * retried with backoff shows as a retriable `Error` while online, and that must step down to
 * `Pending` the moment the device goes offline rather than wait out the backoff. The in-flight
 * call reads [latestConnectivity] for the same reason, so its next backoff phase agrees.
 */
internal class SyncEngineImpl(
    private val pushWorker: PushWorker,
    private val pullWorker: PullWorker,
    private val outboxDao: OutboxDao,
    private val connectivityMonitor: ConnectivityMonitor,
    cursorStore: CursorStore,
    scope: CoroutineScope,
) : SyncEngine {

    private val mutex = Mutex()

    /** Last connectivity the monitor reported; null until it has reported once. */
    @Volatile
    private var latestConnectivity: ConnectivityStatus? = null
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle(ConnectivityStatus.ONLINE))
    override val status: StateFlow<SyncStatus> get() = _status

    override val lastSyncedAt: Flow<Instant?> = cursorStore.observeLastPulledAt()

    init {
        // Crash recovery (03 §2/T7). `OutboxDao.revertAllInFlightToPending` was written for this
        // and never called from anywhere, so a row that went IN_FLIGHT and did not get acked --
        // the app killed mid-push, or a push that 401'd on a dead session -- stayed IN_FLIGHT for
        // good. `takeBatch` only returns PENDING rows, so those mutations became invisible to
        // every future sync: a sale the till had accepted, that the server would never hear about,
        // with nothing on screen to say so. Anything still in flight when this process starts was
        // by definition never acknowledged, so it belongs back in the queue.
        scope.launch { outboxDao.revertAllInFlightToPending() }

        combine(
            connectivityMonitor.observe(),
            outboxDao.observePendingRegisteredCount(),
            outboxDao.observePendingCount(),
            outboxDao.observeFailedCount(),
        ) { connectivity, registered, queued, failed -> Triple(connectivity, displayedPending(registered, queued), failed) }
            .onEach { (connectivity, pending, failed) ->
                latestConnectivity = connectivity
                reflectIdleConnectivity(connectivity, pending, failed)
            }
            .launchIn(scope)
    }

    private fun reflectIdleConnectivity(connectivity: ConnectivityStatus, pending: Int, failed: Int) {
        if (mutex.isLocked) {
            val current = _status.value
            if (current is SyncStatus.Error && current.lastError.retriable && connectivity != ConnectivityStatus.ONLINE) {
                _status.value = SyncStatus.Pending(pending, connectivity)
            }
            return
        }
        _status.value = when (val current = _status.value) {
            is SyncStatus.AuthRequired -> current.copy(pendingCount = pending)
            // A push that was being retried is not a problem a person must look at once the device
            // is offline: it is ordinary queued work again. A server-refused (non-retriable) error
            // stays: going offline does not make it any less true.
            is SyncStatus.Error ->
                if (current.lastError.retriable && connectivity != ConnectivityStatus.ONLINE) {
                    settledStatus(connectivity, pending, failed)
                } else {
                    current.copy(pendingCount = pending)
                }
            else -> settledStatus(connectivity, pending, failed)
        }
    }

    /**
     * What the indicator should read once nothing is in flight.
     *
     * Rejected rows outrank an empty queue. `PushWorker` moves a mutation the server refused to
     * `FAILED` and leaves it there forever -- correct, since re-sending would only repeat a
     * conversation the server has already ended -- but the row is then invisible to
     * `observePendingCount`, so the pill used to settle on "Sincronizado" while a sale the till had
     * taken sat permanently unaccepted. Reporting it as a non-retriable [SyncStatus.Error] is the
     * weakest claim that is still true: something happened that the device cannot fix by itself.
     */
    private fun settledStatus(connectivity: ConnectivityStatus, pending: Int, failed: Int): SyncStatus = when {
        failed > 0 -> SyncStatus.Error(
            pendingCount = pending,
            lastError = SyncError("$failed mutation(s) rejected by the server", retriable = false),
            connectivity = connectivity,
        )

        pending > 0 -> SyncStatus.Pending(pending, connectivity)
        else -> SyncStatus.Idle(connectivity)
    }

    override suspend fun syncNow(): SyncResult = mutex.withLock {
        val connectivity = runCatching { connectivityMonitor.observe().first() }.getOrDefault(ConnectivityStatus.ONLINE)

        val pushOutcome = try {
            pushWorker.drainAll(onPhase = { phase -> _status.value = phase.toStatus(latestConnectivity ?: connectivity, pendingCount()) })
        } catch (e: Exception) {
            _status.value = SyncStatus.Error(pendingCount(), SyncError(e.message ?: "push failed", retriable = true), connectivity)
            return SyncResult(pushed = 0, pulled = 0, error = SyncError(e.message ?: "push failed", retriable = true))
        }

        if (pushOutcome is PushOutcome.AuthRequired) {
            _status.value = SyncStatus.AuthRequired(pendingCount(), connectivity)
            return SyncResult(pushed = pushOutcome.pushedBeforeFailure, pulled = 0, error = null)
        }

        val pulled = try {
            pullWorker.pullAll().changesApplied
        } catch (unauthorized: SyncAuthException) {
            // Same terminal state push reports (PushOutcome.AuthRequired): PullWorker already tried
            // one refresh, so reaching here means the device session is dead and only re-enrollment
            // fixes it. Reporting a generic retriable Error instead would leave the indicator
            // implying the next tick might succeed, which it cannot.
            _status.value = SyncStatus.AuthRequired(pendingCount(), connectivity)
            return SyncResult(pushed = (pushOutcome as PushOutcome.Done).pushed, pulled = 0, error = null)
        } catch (e: Exception) {
            _status.value = SyncStatus.Error(pendingCount(), SyncError(e.message ?: "pull failed", retriable = true), connectivity)
            return SyncResult(pushed = (pushOutcome as PushOutcome.Done).pushed, pulled = 0, error = SyncError(e.message ?: "pull failed", retriable = true))
        }

        // Not an unconditional `Idle`: a drain that pushed everything it *could* still leaves any
        // server-rejected row behind, and settling to "synced" would bury it.
        _status.value = settledStatus(connectivity, pendingCount(), failedCount())
        return SyncResult(pushed = (pushOutcome as PushOutcome.Done).pushed, pulled = pulled, error = null)
    }

    /**
     * Full pull from `since=0` (03 §8) -- the richer blocking-UI/first-login flow is work item 9.
     *
     * Drains the outbox first, under the same [mutex] as [syncNow]. A sale the server already
     * applied but whose ack we never saw is still queued locally; the full pull would bring the
     * server's stock figure (which already includes it) and the local read model would subtract
     * the queued sale a second time. The push is a single attempt: [PushWorker.drainAll] otherwise
     * retries a transient failure forever while holding the mutex, so an offline bootstrap would
     * never reach the pull. If the push fails or is unreachable the pull still runs and unsynced
     * rows stay subtracted, exactly as before. A dead session ends the bootstrap the way it ends
     * [syncNow]: the pull would only 401 too.
     */
    override suspend fun bootstrap(): BootstrapResult = mutex.withLock {
        val connectivity = runCatching { connectivityMonitor.observe().first() }.getOrDefault(ConnectivityStatus.ONLINE)

        val pushOutcome = try {
            pushWorker.drainAll(
                maxAttempts = 1,
                onPhase = { phase -> _status.value = phase.toStatus(latestConnectivity ?: connectivity, pendingCount()) },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null // best effort: the pull below is still worth doing
        }

        if (pushOutcome is PushOutcome.AuthRequired) {
            _status.value = SyncStatus.AuthRequired(pendingCount(), connectivity)
            return BootstrapResult(success = false, error = SyncError("device session expired", retriable = false))
        }

        try {
            pullWorker.pullAll(since = 0L)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unauthorized: SyncAuthException) {
            _status.value = SyncStatus.AuthRequired(pendingCount(), connectivity)
            return BootstrapResult(success = false, error = SyncError("device session expired", retriable = false))
        } catch (e: Exception) {
            val error = SyncError(e.message ?: "bootstrap failed", retriable = true)
            _status.value = SyncStatus.Error(pendingCount(), error, connectivity)
            return BootstrapResult(success = false, error = error)
        }

        // A completed full pull is proof the credential works, so it also clears `AuthRequired`
        // (otherwise terminal by design): a device that just re-logged in must not stay red, and
        // `SyncSchedulerImpl` must stop suppressing non-tick pushes.
        _status.value = settledStatus(connectivity, pendingCount(), failedCount())
        return BootstrapResult(success = true, error = null)
    }

    /**
     * What the pill shows as "N pendientes": operations a person registered, not outbox rows. A
     * batch queues its ingredient decrements beside it, and those are the protocol's business.
     */
    private suspend fun pendingCount(): Int =
        displayedPending(outboxDao.observePendingRegisteredCount().first(), outboxDao.observePendingCount().first())

    /**
     * Whether anything is pending is decided by outbox rows, so the pill never says "Sincronizado"
     * while a batch's decrements are still queued (they can be split from their batch by a push
     * page, or outlive it after its ack). The *number* shown is the registered operations, at least
     * one while any row waits.
     */
    private fun displayedPending(registered: Int, queued: Int): Int = if (queued > 0) maxOf(registered, 1) else 0

    private suspend fun failedCount(): Int = outboxDao.observeFailedCount().first()

    private fun PushPhase.toStatus(connectivity: ConnectivityStatus, pending: Int): SyncStatus = when (this) {
        PushPhase.SYNCING -> SyncStatus.Syncing(connectivity)
        // A push that failed while the network is up reached (or tried to reach) a server that
        // is not accepting it: that is not "no connection", and waiting will not obviously fix
        // it, so it needs a person's eyes. It stays retriable -- the drain loop keeps backing off
        // -- and clears by itself when a later attempt succeeds.
        PushPhase.PENDING_BACKOFF ->
            if (connectivity == ConnectivityStatus.ONLINE) {
                SyncStatus.Error(pending, SyncError("push failed, retrying", retriable = true), connectivity)
            } else {
                SyncStatus.Pending(pending, connectivity)
            }
    }
}
