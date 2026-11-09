package com.f776.ecopan.data.sync.unittest

import com.f776.ecopan.data.sync.rejected.RejectedOperationsImpl
import com.f776.ecopan.data.sync.unittest.fake.FakeOutboxDao
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.sync.outboxRow
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** HU-SYN-0006: a rejected operation can be reviewed, sent again, or given up on with a record. */
class RejectedOperationsImplTest {

    private val dao = FakeOutboxDao()
    private val rejectedOperations = RejectedOperationsImpl(dao)
    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private suspend fun rejectedSale(error: String = "BRANCH_FORBIDDEN"): Uuid {
        val id = Uuid.random()
        dao.insert(outboxRow(id, SyncEntityType.SALE, SyncOpType.CREATE, "{}", at))
        dao.markFailed(id, error)
        return id
    }

    @Test
    fun `a rejected row is listed with its kind and the server's reason`() = runTest {
        val id = rejectedSale()

        val listed = rejectedOperations.rejected.first().single()

        assertEquals(id, listed.mutationId)
        assertEquals(SyncEntityType.SALE, listed.entityType)
        assertEquals("BRANCH_FORBIDDEN", listed.reason)
    }

    @Test
    fun `pending and acked rows are not offered for review`() = runTest {
        dao.insert(outboxRow(Uuid.random(), SyncEntityType.SALE, SyncOpType.CREATE, "{}", at))

        assertEquals(emptyList(), rejectedOperations.rejected.first())
    }

    @Test
    fun `retry puts the row back in the push queue and clears the old error`() = runTest {
        val id = rejectedSale()

        rejectedOperations.retry(id)

        assertEquals(OutboxState.PENDING, dao.stateOf(id))
        assertEquals(listOf(id), dao.takeBatch(10).map { it.mutationId })
        assertNull(dao.rows.single { it.mutationId == id }.lastError)
        assertEquals(emptyList(), rejectedOperations.rejected.first())
    }

    @Test
    fun `discard keeps the row as a record but never sends or lists it again`() = runTest {
        val id = rejectedSale()

        rejectedOperations.discard(id)

        assertEquals(OutboxState.DISCARDED, dao.stateOf(id))
        assertEquals(emptyList(), dao.takeBatch(10))
        assertEquals(emptyList(), rejectedOperations.rejected.first())
        assertEquals(0, dao.observeFailedCount().first())
    }

    @Test
    fun `retry and discard leave a row that is not rejected alone`() = runTest {
        val id = Uuid.random()
        dao.insert(outboxRow(id, SyncEntityType.SALE, SyncOpType.CREATE, "{}", at))

        rejectedOperations.discard(id)

        assertEquals(OutboxState.PENDING, dao.stateOf(id))
    }
}
