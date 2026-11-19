package com.f776.ecopan.data.sync.unittest

import com.f776.ecopan.data.sync.push.PushOutcome
import com.f776.ecopan.data.sync.push.PushPhase
import com.f776.ecopan.data.sync.push.PushWorker
import com.f776.ecopan.data.sync.transport.KtorSyncTransport
import com.f776.ecopan.data.sync.transport.SyncTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import com.f776.ecopan.data.sync.unittest.fake.FakeOutboxDao
import com.f776.ecopan.data.sync.unittest.fake.FakeServer
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncAuthProvider
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncTransport
import com.f776.ecopan.domain.sync.wire.MutationDto
import com.f776.ecopan.domain.sync.wire.PushRequest
import com.f776.ecopan.domain.sync.wire.PushResponse
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PushWorkerTest {

    private val outboxDao = FakeOutboxDao()
    private val server = FakeServer()
    private val transport = FakeSyncTransport(server)
    private val authProvider = FakeSyncAuthProvider()

    private fun worker(batchSize: Int = 50) = PushWorker(
        outboxDao = outboxDao,
        transport = transport,
        authProvider = authProvider,
        deviceIdProvider = { "device-under-test" },
        batchSize = batchSize,
    )

    private suspend fun enqueue(entityType: String = "STOCK_MOVEMENT", createdAt: Instant = Clock.System.now()): Uuid {
        val id = Uuid.random()
        outboxDao.insert(
            OutboxEntity(
                mutationId = id,
                entityType = entityType,
                opType = "CREATE",
                payload = """{"id":"$id"}""",
                createdAt = createdAt,
                state = OutboxState.PENDING,
            ),
        )
        return id
    }

    /**
     * Every anticipated failure puts the batch back in the queue; an unanticipated one used not to.
     * The rows stayed `IN_FLIGHT`, `takeBatch` only reads `PENDING`, and so the mutations went
     * silent for the rest of the process -- later syncs found "nothing to push" and the indicator
     * settled on synced while accepted sales sat unsent.
     */
    @Test
    fun unexpectedPushFailureLeavesTheBatchQueuedRatherThanInFlight() = runTest {
        val mutationId = enqueue()
        transport.pushUnexpectedOnce = true

        runCatching { worker().drainAll() }

        assertEquals(OutboxState.PENDING, outboxDao.stateOf(mutationId))
        assertEquals(1, outboxDao.observePendingCount().first())
    }

    @Test
    fun duplicatePushAfterDroppedAckIsIdempotent() = runTest {
        val mutationId = enqueue()
        transport.dropAckOnNextPush = true

        val outcome = worker().drainAll()

        assertIs<PushOutcome.Done>(outcome)
        assertEquals(1, outcome.pushed) // acked once the retry saw DUPLICATE
        assertTrue(outboxDao.rows.isEmpty(), "outbox should be fully ACKED (deleted) after the retry")
        assertEquals(setOf(mutationId.toString()), server.appliedMutationIds, "server applied it exactly once, not twice")
    }

    @Test
    fun outboxDrainsFifoByRowid() = runTest {
        // Enqueued out of wall-clock order -- rowid (insertion order) must still win.
        val first = enqueue(createdAt = Instant.fromEpochMilliseconds(3_000))
        val second = enqueue(createdAt = Instant.fromEpochMilliseconds(1_000))
        val third = enqueue(createdAt = Instant.fromEpochMilliseconds(2_000))

        var observedOrder: List<String> = emptyList()
        val recordingTransport = object : SyncTransport by transport {
            override suspend fun push(request: PushRequest): PushResponse {
                observedOrder = request.mutations.map { it.mutationId }
                return transport.push(request)
            }
        }

        val worker = PushWorker(outboxDao, recordingTransport, authProvider, { "device" })
        val outcome = worker.drainAll()

        assertIs<PushOutcome.Done>(outcome)
        assertEquals(listOf(first, second, third).map { it.toString() }, observedOrder)
        assertTrue(outboxDao.rows.isEmpty())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun transientFailureBacksOffExponentiallyAndRecovers() = runTest {
        enqueue()
        transport.pushFailuresRemaining = 3

        val phases = mutableListOf<PushPhase>()
        val timestamps = mutableListOf<Long>()

        val outcome = worker().drainAll(onPhase = { phase ->
            phases += phase
            timestamps += currentTime
        })

        assertIs<PushOutcome.Done>(outcome)
        assertEquals(1, outcome.pushed)
        assertTrue(outboxDao.rows.isEmpty())

        // SYNCING, PENDING(backoff), SYNCING, PENDING, SYNCING, PENDING, SYNCING -- 3 failures then success.
        assertEquals(
            listOf(
                PushPhase.SYNCING, PushPhase.PENDING_BACKOFF,
                PushPhase.SYNCING, PushPhase.PENDING_BACKOFF,
                PushPhase.SYNCING, PushPhase.PENDING_BACKOFF,
                PushPhase.SYNCING,
            ),
            phases,
        )

        // Backoff deltas are measured between consecutive PENDING_BACKOFF -> SYNCING timestamps,
        // 5s * 2^n +/- 20% jitter (03 §3.3): attempt 1 ~5s, attempt 2 ~10s, attempt 3 ~20s.
        val delay1 = timestamps[2] - timestamps[1]
        val delay2 = timestamps[4] - timestamps[3]
        val delay3 = timestamps[6] - timestamps[5]
        assertTrue(delay1 in 4_000..6_000, "expected ~5s, was $delay1")
        assertTrue(delay2 in 8_000..12_000, "expected ~10s, was $delay2")
        assertTrue(delay3 in 16_000..24_000, "expected ~20s, was $delay3")
    }

    @Test
    fun authExpiredKeepsQueueingAndResumesAfterRefresh() = runTest {
        val mutationId = enqueue()
        transport.pushUnauthorizedOnce = true
        authProvider.refreshSucceeds = false

        val outcome = worker().drainAll()

        assertIs<PushOutcome.AuthRequired>(outcome)
        assertEquals(OutboxState.PENDING, outboxDao.stateOf(mutationId), "row must revert to PENDING, not stay IN_FLIGHT or FAILED")

        // New enqueues are still accepted while parked (03 §3.4: "local operation continues unimpeded").
        val secondMutationId = enqueue()
        assertEquals(2, outboxDao.observePendingCount().first())

        // Simulated re-login: refresh now succeeds, and the *next* push attempt (no more scripted 401) drains everything.
        authProvider.refreshSucceeds = true
        transport.pushUnauthorizedOnce = false

        val resumed = worker().drainAll()

        assertIs<PushOutcome.Done>(resumed)
        assertEquals(2, resumed.pushed)
        assertTrue(outboxDao.rows.isEmpty())
        assertEquals(setOf(mutationId.toString(), secondMutationId.toString()), server.appliedMutationIds)
    }

    @Test
    fun crashRecoveryRevertsInFlightToPending() = runTest {
        val mutationId = enqueue()
        // Simulate the crash: server already applied it pre-crash, client marked it IN_FLIGHT and died.
        outboxDao.markInFlight(listOf(mutationId))
        assertEquals(OutboxState.IN_FLIGHT, outboxDao.stateOf(mutationId))

        // Engine restart (03 §2/T7): revert any IN_FLIGHT row to PENDING before resuming.
        outboxDao.revertAllInFlightToPending()
        assertEquals(OutboxState.PENDING, outboxDao.stateOf(mutationId))

        // Pre-seed the server with an already-applied result for this mutationId (worst case: it
        // really did reach the server before the crash) -- the resumed push must see DUPLICATE, not error.
        server.push(
            PushRequest(
                deviceId = "device-before-crash",
                mutations = listOf(
                    MutationDto(
                        mutationId = mutationId.toString(),
                        entityType = "STOCK_MOVEMENT",
                        opType = "CREATE",
                        clientTimestamp = 0L,
                        payload = JsonNull,
                    ),
                ),
            ),
        )

        val outcome = worker().drainAll()

        assertIs<PushOutcome.Done>(outcome)
        assertTrue(outboxDao.rows.isEmpty(), "resumed push must still ack (DUPLICATE) and clear the row")
    }

    @Test
    fun rejectedMutationDeadLettersWithoutBlockingBatch() = runTest {
        val goodOne = enqueue(entityType = "PRODUCTION_BATCH")
        val bad = enqueue(entityType = "STOCK_MOVEMENT")
        val goodTwo = enqueue(entityType = "PRODUCTION_BATCH")
        server.rejectedEntityTypes = setOf("STOCK_MOVEMENT")

        val outcome = worker().drainAll()

        assertIs<PushOutcome.Done>(outcome)
        assertEquals(2, outcome.pushed)
        assertEquals(OutboxState.FAILED, outboxDao.stateOf(bad))
        assertEquals(null, outboxDao.stateOf(goodOne), "acked rows are deleted, not just marked")
        assertEquals(null, outboxDao.stateOf(goodTwo))
    }

    /**
     * #135: the backend answers a database or broker failure with a 503 instead of marking items
     * REJECTED. Through the real HTTP transport, that must leave every row pending for a retry.
     */
    @Test
    fun aServerErrorFromTheRealTransportLeavesTheRowsPending() = runTest {
        val first = enqueue()
        val second = enqueue()
        val http = HttpClient(MockEngine { respond("""{"code":"SYNC_UNAVAILABLE"}""", HttpStatusCode.ServiceUnavailable) }) {
            install(ContentNegotiation) { json() }
        }
        val realTransport = KtorSyncTransport(http, "http://backend", authProvider)

        val outcome = PushWorker(outboxDao, realTransport, authProvider, deviceIdProvider = { "till" }).drainAll(maxAttempts = 1)

        assertIs<PushOutcome.Unreachable>(outcome)
        assertEquals(OutboxState.PENDING, outboxDao.stateOf(first))
        assertEquals(OutboxState.PENDING, outboxDao.stateOf(second))
    }
}
