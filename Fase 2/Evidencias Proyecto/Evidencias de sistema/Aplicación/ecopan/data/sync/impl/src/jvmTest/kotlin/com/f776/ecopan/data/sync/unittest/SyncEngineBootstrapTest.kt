package com.f776.ecopan.data.sync.unittest

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.data.sync.CursorStore
import com.f776.ecopan.data.sync.SyncEngineImpl
import com.f776.ecopan.data.sync.pull.PullWorker
import com.f776.ecopan.data.sync.push.PushWorker
import com.f776.ecopan.data.sync.transport.SyncTransientException
import com.f776.ecopan.data.sync.transport.SyncTransport
import com.f776.ecopan.data.sync.unittest.fake.FakeConnectivityMonitor
import com.f776.ecopan.data.sync.unittest.fake.FakeOutboxDao
import com.f776.ecopan.data.sync.unittest.fake.FakePreferencesDataStore
import com.f776.ecopan.data.sync.unittest.fake.FakeServer
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncAuthProvider
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncTransport
import com.f776.ecopan.domain.sync.ConnectivityStatus
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncStatus
import com.f776.ecopan.domain.sync.wire.PullResponse
import com.f776.ecopan.domain.sync.wire.PushRequest
import com.f776.ecopan.domain.sync.wire.PushResponse
import com.f776.unigrid.core.database.AppDatabase
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * `bootstrap()` does a full pull from cursor 0. If a sale the server already applied is still
 * queued locally, the pulled stock already includes it and the local read model subtracts it a
 * second time, so bootstrap must drain the outbox first (same mutex, like `syncNow`) -- without
 * ever hanging on an unreachable server, which would also block every later `syncNow`.
 */
class SyncEngineBootstrapTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val outbox = FakeOutboxDao()
    private val inner = FakeSyncTransport(FakeServer())
    private val authProvider = FakeSyncAuthProvider()
    private val pushedMutationIds = mutableListOf<String>()
    private val calls = mutableListOf<String>()
    private val pushCalls get() = calls.count { it == "push" }
    private val pullCalls get() = calls.count { it == "pull" }

    /** Scripted failure thrown by [transport]'s next pushes, in addition to [inner]'s own. */
    private var pushFailure: (() -> Throwable)? = null
    private var pullFailure: (() -> Throwable)? = null

    private val transport = object : SyncTransport {
        override suspend fun push(request: PushRequest): PushResponse {
            calls += "push"
            pushFailure?.let { throw it() }
            pushedMutationIds += request.mutations.map { it.mutationId }
            return inner.push(request)
        }

        override suspend fun pull(since: Long, limit: Int, entityTypes: Set<SyncEntityType>?): PullResponse {
            calls += "pull"
            pullFailure?.let { throw it() }
            return inner.pull(since, limit, entityTypes)
        }
    }

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    private fun TestScope.engine(): SyncEngineImpl {
        val cursorStore = CursorStore(FakePreferencesDataStore())
        return engine(cursorStore)
    }

    private fun TestScope.engine(cursorStore: CursorStore) = SyncEngineImpl(
        pushWorker = PushWorker(outbox, transport, authProvider, deviceIdProvider = { "till" }),
        pullWorker = PullWorker(transport, cursorStore, database, appliers = emptyList()),
        cursorStore = cursorStore,
        outboxDao = outbox,
        connectivityMonitor = FakeConnectivityMonitor(),
        scope = backgroundScope,
    )

    private suspend fun enqueueSale(): Uuid {
        val id = Uuid.random()
        outbox.insert(
            OutboxEntity(
                mutationId = id,
                entityType = "STOCK_MOVEMENT",
                opType = "CREATE",
                payload = """{"id":"$id"}""",
                createdAt = Clock.System.now(),
                state = OutboxState.PENDING,
            ),
        )
        return id
    }

    @Test
    fun bootstrapPushesPendingMutationsBeforeTheFullPull() = runTest(UnconfinedTestDispatcher()) {
        val saleId = enqueueSale()

        val result = engine().bootstrap()

        assertTrue(result.success)
        assertEquals(listOf("push", "pull"), calls, "drained first, then exactly one full pull")
        assertEquals(listOf(saleId.toString()), pushedMutationIds.distinct(), "the sale itself was sent")
        assertNull(outbox.stateOf(saleId), "the sale was acked and removed")
    }

    @Test
    fun bootstrapDoesNotHangWhenTheServerIsUnreachable() = runTest(UnconfinedTestDispatcher(), timeout = 5.seconds) {
        val saleId = enqueueSale()
        pushFailure = { SyncTransientException("offline (scripted)") }

        val result = engine().bootstrap()

        assertTrue(result.success, "the pull still ran")
        assertEquals(1, pushCalls, "a single push attempt, no backoff loop")
        assertEquals(1, pullCalls)
        assertEquals(OutboxState.PENDING, outbox.stateOf(saleId), "unsynced row stays queued")
    }

    @Test
    fun bootstrapStillPullsOnceWhenThePushFailsUnexpectedly() = runTest(UnconfinedTestDispatcher()) {
        val saleId = enqueueSale()
        pushFailure = { IllegalStateException("undeclared transport failure") }

        val result = engine().bootstrap()

        assertTrue(result.success)
        assertEquals(1, pullCalls)
        assertEquals(OutboxState.PENDING, outbox.stateOf(saleId), "PushWorker unwound the in-flight mark")
    }

    @Test
    fun cancellationDuringThePushPropagatesAndSkipsThePull() = runTest(UnconfinedTestDispatcher()) {
        enqueueSale()
        pushFailure = { CancellationException("scope cancelled") }

        assertFailsWith<CancellationException> { engine().bootstrap() }

        assertEquals(0, pullCalls)
    }

    @Test
    fun aDeadSessionOnPushStopsBootstrapWithAuthRequiredAndNoPull() = runTest(UnconfinedTestDispatcher()) {
        enqueueSale()
        inner.pushUnauthorizedOnce = true // refresh fails (FakeSyncAuthProvider default)
        val engine = engine()

        val result = engine.bootstrap()

        assertFalse(result.success)
        assertEquals(0, pullCalls)
        assertEquals(SyncStatus.AuthRequired(1, ConnectivityStatus.ONLINE), engine.status.value)
    }

    @Test
    fun statusSettlesToIdleAfterASuccessfulBootstrap() = runTest(UnconfinedTestDispatcher()) {
        enqueueSale()
        val engine = engine()

        engine.bootstrap()

        assertEquals(SyncStatus.Idle(ConnectivityStatus.ONLINE), engine.status.value)
    }

    @Test
    fun statusReportsPendingWhenBootstrapCouldNotDrain() = runTest(UnconfinedTestDispatcher(), timeout = 5.seconds) {
        enqueueSale()
        pushFailure = { SyncTransientException("offline (scripted)") }
        val engine = engine()

        engine.bootstrap()

        assertEquals(SyncStatus.Pending(1, ConnectivityStatus.ONLINE), engine.status.value)
    }

    @Test
    fun statusReportsErrorWhenTheFullPullFails() = runTest(UnconfinedTestDispatcher()) {
        pullFailure = { IllegalStateException("boom") }
        val engine = engine()

        val result = engine.bootstrap()

        assertFalse(result.success)
        assertTrue(engine.status.value is SyncStatus.Error)
    }
}
