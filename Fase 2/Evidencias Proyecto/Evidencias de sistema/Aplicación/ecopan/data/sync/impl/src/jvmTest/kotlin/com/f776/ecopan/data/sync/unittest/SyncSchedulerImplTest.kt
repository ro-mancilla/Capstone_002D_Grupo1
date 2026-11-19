package com.f776.ecopan.data.sync.unittest

import com.f776.ecopan.data.sync.SyncSchedulerImpl
import com.f776.ecopan.data.sync.unittest.fake.FakeConnectivityMonitor
import com.f776.ecopan.data.sync.unittest.fake.FakeOutboxDao
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncEngine
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/**
 * 03 §7 work item 7 -- scheduler trigger sources, single-flight coalescing, start/stop idempotency.
 *
 * Deliberately uses [runCurrent]/[advanceTimeBy] throughout, never `advanceUntilIdle()`, while
 * [SyncSchedulerImpl.start] is active: its loop is a perpetual coroutine (the periodic tick
 * re-schedules itself forever), so `advanceUntilIdle()` would drain the virtual-time scheduler by
 * running that loop's ticks forever and never return. `advanceUntilIdle()` is only safe after
 * [SyncSchedulerImpl.stop] has cancelled the loop.
 */
class SyncSchedulerImplTest {

    private val connectivityMonitor = FakeConnectivityMonitor(initial = ConnectivityStatus.ONLINE)
    private val outboxDao = FakeOutboxDao()

    @Test
    fun `starting fires a sync from the first observed connectivity value`() = runTest {
        val syncEngine = FakeSyncEngine()
        val scheduler = SyncSchedulerImpl(syncEngine, connectivityMonitor, outboxDao, scope = this, tickInterval = 5.minutes)

        scheduler.start()
        runCurrent()

        assertEquals(1, syncEngine.syncNowCalls.size)
        scheduler.stop()
    }

    @Test
    fun `starting twice does not spawn a second loop`() = runTest {
        val syncEngine = FakeSyncEngine()
        val scheduler = SyncSchedulerImpl(syncEngine, connectivityMonitor, outboxDao, scope = this, tickInterval = 5.minutes)

        scheduler.start()
        scheduler.start()
        runCurrent()

        assertEquals(1, syncEngine.syncNowCalls.size, "second start() must be a no-op while already running")
        scheduler.stop()
    }

    /**
     * The storm this guards against: a failing push marks its batch in flight (pending count -> 0),
     * fails to refresh, and reverts the batch (0 -> N). That rising edge is indistinguishable from
     * a genuinely new mutation, so the failure re-triggered the scheduler, which failed again —
     * measured against the real backend at ~7.6 requests per second, indefinitely.
     */
    @Test
    fun `outbox churn does not re-trigger a sync while the session is dead`() = runTest {
        val syncEngine = FakeSyncEngine()
        val scheduler = SyncSchedulerImpl(syncEngine, connectivityMonitor, outboxDao, scope = this, tickInterval = 5.minutes)

        scheduler.start()
        runCurrent()
        syncEngine.status.value = SyncStatus.AuthRequired(pendingCount = 1, connectivity = ConnectivityStatus.ONLINE)
        runCurrent()
        val callsBefore = syncEngine.syncNowCalls.size

        repeat(5) {
            outboxDao.insert(
                OutboxEntity(
                    mutationId = Uuid.random(),
                    entityType = "STOCK_MOVEMENT",
                    opType = "CREATE",
                    payload = "{}",
                    createdAt = Clock.System.now(),
                    state = OutboxState.PENDING,
                ),
            )
            runCurrent()
        }

        assertEquals(callsBefore, syncEngine.syncNowCalls.size, "a dead session must not be retried on every outbox edge")
        scheduler.stop()
    }

    /** ...but the periodic tick still runs, so the device recovers on its own after a re-login. */
    @Test
    fun `the periodic tick still retries a dead session`() = runTest {
        val syncEngine = FakeSyncEngine()
        val scheduler = SyncSchedulerImpl(syncEngine, connectivityMonitor, outboxDao, scope = this, tickInterval = 5.minutes)

        scheduler.start()
        runCurrent()
        syncEngine.status.value = SyncStatus.AuthRequired(pendingCount = 1, connectivity = ConnectivityStatus.ONLINE)
        runCurrent()
        val callsBefore = syncEngine.syncNowCalls.size

        advanceTimeBy(5.minutes + 1.milliseconds)
        runCurrent()

        assertEquals(callsBefore + 1, syncEngine.syncNowCalls.size)
        scheduler.stop()
    }

    @Test
    fun `outbox becoming non-empty triggers a sync`() = runTest {
        val syncEngine = FakeSyncEngine()
        val scheduler = SyncSchedulerImpl(syncEngine, connectivityMonitor, outboxDao, scope = this, tickInterval = 5.minutes)

        scheduler.start()
        runCurrent() // consume the initial connectivity-driven sync
        val callsAfterStart = syncEngine.syncNowCalls.size

        outboxDao.insert(
            OutboxEntity(
                mutationId = Uuid.random(),
                entityType = "STOCK_MOVEMENT",
                opType = "CREATE",
                payload = "{}",
                createdAt = Clock.System.now(),
                state = OutboxState.PENDING,
            ),
        )
        runCurrent()

        assertEquals(callsAfterStart + 1, syncEngine.syncNowCalls.size)
        scheduler.stop()
    }

    @Test
    fun `periodic tick fires a sync while alive`() = runTest {
        val syncEngine = FakeSyncEngine()
        val scheduler = SyncSchedulerImpl(syncEngine, connectivityMonitor, outboxDao, scope = this, tickInterval = 100.milliseconds)

        scheduler.start()
        runCurrent() // consume the initial connectivity-driven sync
        val callsAfterStart = syncEngine.syncNowCalls.size

        advanceTimeBy(150.milliseconds)
        runCurrent()

        assertEquals(callsAfterStart + 1, syncEngine.syncNowCalls.size)
        scheduler.stop()
    }

    @Test
    fun `stop cancels the loop -- no further ticks fire`() = runTest {
        val syncEngine = FakeSyncEngine()
        val scheduler = SyncSchedulerImpl(syncEngine, connectivityMonitor, outboxDao, scope = this, tickInterval = 100.milliseconds)

        scheduler.start()
        runCurrent()
        scheduler.stop()
        val callsAfterStop = syncEngine.syncNowCalls.size

        // Safe to fully drain now -- stop() cancelled the only long-lived coroutine.
        advanceTimeBy(1.minutes)
        runCurrent()

        assertEquals(callsAfterStop, syncEngine.syncNowCalls.size)
    }

    @Test
    fun `a burst of triggers while a sync is in flight coalesces into one call`() = runTest {
        val syncEngine = FakeSyncEngine().apply { syncDelayMillis = 10_000 }
        val scheduler = SyncSchedulerImpl(syncEngine, connectivityMonitor, outboxDao, scope = this, tickInterval = 5.minutes)

        scheduler.start()
        runCurrent() // first sync starts and is now "in flight", suspended mid-delay

        // More trigger sources fire while the first sync is still running -- both should be
        // coalesced away (mutex.tryLock fails), not queued to run immediately after.
        connectivityMonitor.emit(ConnectivityStatus.OFFLINE)
        connectivityMonitor.emit(ConnectivityStatus.ONLINE)
        runCurrent()

        assertEquals(1, syncEngine.syncNowCalls.size, "burst during an in-flight sync must coalesce, not queue")

        scheduler.stop()
    }
}
