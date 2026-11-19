package com.f776.ecopan.data.sync.unittest

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.data.sync.CursorStore
import com.f776.ecopan.data.sync.SyncEngineImpl
import com.f776.ecopan.data.sync.pull.PullWorker
import com.f776.ecopan.data.sync.push.PushWorker
import com.f776.ecopan.data.sync.transport.SyncRefreshOutcome
import com.f776.ecopan.data.sync.unittest.fake.FakeConnectivityMonitor
import com.f776.ecopan.data.sync.unittest.fake.FakeOutboxDao
import com.f776.ecopan.data.sync.unittest.fake.FakePreferencesDataStore
import com.f776.ecopan.data.sync.unittest.fake.FakeServer
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncAuthProvider
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncTransport
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.unigrid.core.database.AppDatabase
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * What the sync indicator is told in the three situations manual testing got wrong (#82): a
 * device that starts without a network, a server that answers with an error, and a queue made of
 * more rows than the baker registered.
 */
class SyncEngineStatusTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val outboxDao = FakeOutboxDao()
    private val transport = FakeSyncTransport(FakeServer())

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    private val authProvider = FakeSyncAuthProvider()

    private fun TestScope.engine(connectivity: FakeConnectivityMonitor): SyncEngineImpl = SyncEngineImpl(
        pushWorker = PushWorker(outboxDao, transport, authProvider, deviceIdProvider = { "device" }),
        pullWorker = PullWorker(transport, CursorStore(FakePreferencesDataStore()), database, appliers = emptyList()),
        outboxDao = outboxDao,
        connectivityMonitor = connectivity,
        cursorStore = CursorStore(FakePreferencesDataStore()),
        scope = backgroundScope,
    )

    private suspend fun enqueue(entityType: String, payload: String) {
        outboxDao.insert(
            OutboxEntity(
                mutationId = Uuid.random(),
                entityType = entityType,
                opType = "CREATE",
                payload = payload,
                createdAt = Clock.System.now(),
                state = OutboxState.PENDING,
            ),
        )
    }

    private suspend fun enqueueBatchWithFourDecrements() {
        val batchId = Uuid.random()
        enqueue("PRODUCTION_BATCH", """{"id":"$batchId"}""")
        repeat(4) {
            enqueue("STOCK_MOVEMENT", """{"id":"${Uuid.random()}","kind":"FORMULA_DECREMENT","sourceBatchId":"$batchId"}""")
        }
    }

    @Test
    fun aDeviceThatStartsOfflineReportsOffline() = runTest {
        val engine = engine(FakeConnectivityMonitor(initial = ConnectivityStatus.OFFLINE))
        runCurrent()

        assertEquals(SyncStatus.Idle(ConnectivityStatus.OFFLINE), engine.status.value)
    }

    @Test
    fun aServerErrorWhileOnlineNeedsAttentionRatherThanReadingAsOffline() = runTest {
        enqueueBatchWithFourDecrements()
        transport.pushFailuresRemaining = Int.MAX_VALUE
        val engine = engine(FakeConnectivityMonitor(initial = ConnectivityStatus.ONLINE))

        backgroundScope.launch { engine.syncNow() }
        runCurrent()

        val status = assertIs<SyncStatus.Error>(engine.status.value)
        assertTrue(status.lastError.retriable, "the push is still being retried with backoff")
        assertEquals(ConnectivityStatus.ONLINE, status.connectivity)
        assertEquals(1, status.pendingCount)
    }

    @Test
    fun aFailedPushWhileOfflineStillReadsAsOfflineWithPendingWork() = runTest {
        enqueueBatchWithFourDecrements()
        transport.pushFailuresRemaining = Int.MAX_VALUE
        val engine = engine(FakeConnectivityMonitor(initial = ConnectivityStatus.OFFLINE))

        backgroundScope.launch { engine.syncNow() }
        runCurrent()

        assertEquals(SyncStatus.Pending(1, ConnectivityStatus.OFFLINE), engine.status.value)
    }

    @Test
    fun oneQueuedBatchCountsAsOnePendingNotFive() = runTest {
        enqueueBatchWithFourDecrements()
        val engine = engine(FakeConnectivityMonitor(initial = ConnectivityStatus.OFFLINE))
        runCurrent()

        assertEquals(SyncStatus.Pending(1, ConnectivityStatus.OFFLINE), engine.status.value)
    }

    @Test
    fun decrementsLeftInTheQueueAfterTheirBatchStillReadAsPending() = runTest {
        // The batch row was acked, its decrements were not: nothing "registered" is left, but the
        // device has unsent ledger rows and must not claim to be synced.
        enqueue("STOCK_MOVEMENT", """{"id":"${Uuid.random()}","kind":"FORMULA_DECREMENT","sourceBatchId":"${Uuid.random()}"}""")
        val engine = engine(FakeConnectivityMonitor(initial = ConnectivityStatus.OFFLINE))
        runCurrent()

        assertEquals(SyncStatus.Pending(1, ConnectivityStatus.OFFLINE), engine.status.value)
    }

    @Test
    fun aRetriedPushErrorStepsDownToPendingWhenTheDeviceGoesOffline() = runTest {
        enqueueBatchWithFourDecrements()
        transport.pushFailuresRemaining = Int.MAX_VALUE
        val connectivity = FakeConnectivityMonitor(initial = ConnectivityStatus.ONLINE)
        val engine = engine(connectivity)
        backgroundScope.launch { engine.syncNow() }
        runCurrent()
        assertIs<SyncStatus.Error>(engine.status.value)

        connectivity.emit(ConnectivityStatus.OFFLINE)
        runCurrent()

        assertEquals(SyncStatus.Pending(1, ConnectivityStatus.OFFLINE), engine.status.value)
    }

    /** #133: a 429/5xx/I/O refresh after a 401 keeps the device session and retries on its own. */
    @Test
    fun aTransientRefreshNeverNeedsSignInAndTheRetrySyncs() = runTest {
        enqueueBatchWithFourDecrements()
        transport.pushUnauthorizedOnce = true
        authProvider.outcome = SyncRefreshOutcome.TRANSIENT
        val engine = engine(FakeConnectivityMonitor(initial = ConnectivityStatus.ONLINE))
        val seen = mutableListOf<SyncStatus>()
        backgroundScope.launch { engine.status.collect { seen += it } }

        backgroundScope.launch { engine.syncNow() }
        runCurrent()
        assertTrue(seen.none { it is SyncStatus.AuthRequired }, "statuses: $seen")
        assertEquals(1, authProvider.refreshCallCount)

        // The server recovers; the backoff retry (no new 401) drains the queue.
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(seen.none { it is SyncStatus.AuthRequired }, "statuses: $seen")
        assertEquals(0, outboxDao.rows.size, "the queued batch was pushed once the server recovered")
    }

    @Test
    fun aRejectedRefreshStillNeedsSignIn() = runTest {
        enqueueBatchWithFourDecrements()
        transport.pushUnauthorizedOnce = true
        authProvider.outcome = SyncRefreshOutcome.REJECTED
        val engine = engine(FakeConnectivityMonitor(initial = ConnectivityStatus.ONLINE))

        backgroundScope.launch { engine.syncNow() }
        runCurrent()

        assertIs<SyncStatus.AuthRequired>(engine.status.value)
    }
}
