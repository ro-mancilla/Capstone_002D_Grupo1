package com.f776.unigrid.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Schema + state-machine smoke tests for the outbox table (03 §2), added in schema v5. */
class OutboxSchemaTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    private val now: Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `re-enqueuing the same mutationId is a harmless no-op`() = runTest {
        val dao = database.outboxDao()
        val mutationId = Uuid.random()
        val row = row(mutationId)

        dao.insert(row)
        dao.insert(row.copy(payload = "a different payload — must be ignored"))

        assertEquals(1, dao.observePendingCount().first())
        assertEquals(row.payload, dao.takeBatch(10).single().payload)
    }

    @Test
    fun `a batch and the stock movements it derived count as one registered operation`() = runTest {
        val dao = database.outboxDao()
        val batchId = Uuid.random()
        dao.insert(row(Uuid.random()).copy(entityType = "PRODUCTION_BATCH", payload = """{"id":"$batchId"}"""))
        repeat(4) {
            dao.insert(
                row(Uuid.random()).copy(
                    payload = """{"id":"${Uuid.random()}","kind":"FORMULA_DECREMENT","sourceBatchId":"$batchId","note":null}""",
                ),
            )
        }
        // A manual adjustment is its own thing the baker did: no source batch.
        dao.insert(row(Uuid.random()).copy(payload = """{"id":"${Uuid.random()}","kind":"ADJUSTMENT","sourceBatchId":null}"""))

        assertEquals(6, dao.observePendingCount().first())
        assertEquals(2, dao.observePendingRegisteredCount().first())
    }

    @Test
    fun `takeBatch returns pending rows in rowid ascending order`() = runTest {
        val dao = database.outboxDao()
        val ids = List(3) { Uuid.random() }
        ids.forEach { dao.insert(row(it)) }

        val batch = dao.takeBatch(10)

        assertEquals(ids, batch.map { it.mutationId })
    }

    @Test
    fun `the full push lifecycle -- pending to in-flight to acked (deleted)`() = runTest {
        val dao = database.outboxDao()
        val mutationId = Uuid.random()
        dao.insert(row(mutationId))

        dao.markInFlight(listOf(mutationId))
        assertEquals(OutboxState.IN_FLIGHT, dao.stateOf(mutationId))
        assertEquals(0, dao.observePendingCount().first())

        dao.deleteAcked(listOf(mutationId))
        assertNull(dao.stateOf(mutationId))
    }

    @Test
    fun `a transport failure reverts in-flight rows to pending for retry`() = runTest {
        val dao = database.outboxDao()
        val mutationId = Uuid.random()
        dao.insert(row(mutationId))
        dao.markInFlight(listOf(mutationId))

        dao.revertToPending(listOf(mutationId))

        assertEquals(OutboxState.PENDING, dao.stateOf(mutationId))
        assertEquals(1, dao.observePendingCount().first())
    }

    @Test
    fun `crash recovery reverts every in-flight row, not just one`() = runTest {
        val dao = database.outboxDao()
        val ids = List(3) { Uuid.random() }
        ids.forEach { dao.insert(row(it)) }
        dao.markInFlight(ids)

        dao.revertAllInFlightToPending()

        assertEquals(3, dao.observePendingCount().first())
    }

    @Test
    fun `a rejected mutation dead-letters with its error, without touching other rows`() = runTest {
        val dao = database.outboxDao()
        val rejected = Uuid.random()
        val stillPending = Uuid.random()
        dao.insert(row(rejected))
        dao.insert(row(stillPending))
        dao.markInFlight(listOf(rejected, stillPending))

        dao.markFailed(rejected, "BRANCH_FORBIDDEN")
        dao.deleteAcked(listOf(stillPending))

        val failed = dao.observeFailed().first().single()
        assertEquals(rejected, failed.mutationId)
        assertEquals("BRANCH_FORBIDDEN", failed.lastError)
        assertNull(dao.stateOf(stillPending))
    }

    @Test
    fun `attempts increments per retry without changing state`() = runTest {
        val dao = database.outboxDao()
        val mutationId = Uuid.random()
        dao.insert(row(mutationId))

        dao.incrementAttempts(listOf(mutationId))
        dao.incrementAttempts(listOf(mutationId))

        assertEquals(2, dao.takeBatch(10).single().attempts)
    }

    private fun row(mutationId: Uuid) = OutboxEntity(
        mutationId = mutationId,
        entityType = "STOCK_MOVEMENT",
        opType = "CREATE",
        payload = """{"id":"$mutationId"}""",
        createdAt = now,
        state = OutboxState.PENDING,
    )
}
