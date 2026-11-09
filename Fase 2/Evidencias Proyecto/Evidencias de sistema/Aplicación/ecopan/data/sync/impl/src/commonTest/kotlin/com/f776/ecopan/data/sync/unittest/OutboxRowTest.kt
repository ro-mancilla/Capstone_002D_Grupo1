package com.f776.ecopan.data.sync.unittest

import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.sync.outboxRow
import com.f776.unigrid.core.database.entity.OutboxState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The invariants that used to be restated at every write site, now stated once. Each was a way to
 * queue a mutation the push side would never pick up.
 */
class OutboxRowTest {

    private val mutationId = Uuid.random()
    private val at = Instant.fromEpochMilliseconds(1_700_000_000_000)

    @Test
    fun `a minted row is always pending never already in flight`() {
        val row = outboxRow(mutationId, SyncEntityType.SALE, SyncOpType.CREATE, "{}", at)

        // A row minted IN_FLIGHT is invisible to takeBatch forever -- the failure SyncEngineImpl's
        // crash recovery exists to undo.
        assertEquals(OutboxState.PENDING, row.state)
    }

    @Test
    fun `the stored spellings are the enum names the push worker parses back`() {
        val row = outboxRow(mutationId, SyncEntityType.PRODUCTION_BATCH, SyncOpType.CREATE, "{}", at)

        assertEquals("PRODUCTION_BATCH", row.entityType)
        assertEquals("CREATE", row.opType)
        assertEquals(SyncEntityType.PRODUCTION_BATCH, SyncEntityType.valueOf(row.entityType))
        assertEquals(SyncOpType.CREATE, SyncOpType.valueOf(row.opType))
    }

    @Test
    fun `every entity and op type round-trips through the stored string`() {
        for (entityType in SyncEntityType.entries) {
            for (opType in SyncOpType.entries) {
                val row = outboxRow(mutationId, entityType, opType, "{}", at)
                assertEquals(entityType, SyncEntityType.valueOf(row.entityType))
                assertEquals(opType, SyncOpType.valueOf(row.opType))
            }
        }
    }

    @Test
    fun `the row carries its mutation id payload and timestamp unchanged`() {
        val row = outboxRow(mutationId, SyncEntityType.STOCK_MOVEMENT, SyncOpType.CREATE, """{"a":1}""", at)

        assertEquals(mutationId, row.mutationId)
        assertEquals("""{"a":1}""", row.payload)
        assertEquals(at, row.createdAt)
    }
}
