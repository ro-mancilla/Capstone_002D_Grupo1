package com.f776.ecopan.api.events

import com.f776.ecopan.api.feature.events.domain.BATCH_SNAPSHOT_EVENT
import com.f776.ecopan.api.feature.events.domain.SNAPSHOT_WINDOW
import com.f776.ecopan.api.feature.events.domain.snapshotFrames
import com.f776.ecopan.api.feature.events.domain.snapshotSince
import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.repository.model.ProductionBatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class BatchSnapshotTest {

    private fun batch(branchId: Uuid) = ProductionBatch(
        id = Uuid.random(), tenantId = Uuid.random(), branchId = branchId, bakerId = Uuid.random(),
        productId = Uuid.random(), mixWeightGrams = 5000, batchAt = Instant.fromEpochMilliseconds(1_000),
        movementsMinted = true, decrementStatus = DecrementStatus.APPLIED, deviceId = "d",
        serverSeq = 1, receivedAt = Instant.fromEpochMilliseconds(2_000)
    )

    @Test
    fun `snapshot window is 24 hours, not days`() {
        assertEquals(24.hours, SNAPSHOT_WINDOW)
        val now = Instant.fromEpochMilliseconds(1_000_000_000_000)
        assertEquals(now - 24.hours, snapshotSince(now))
    }

    @Test
    fun `replayed batches are not named batch-ready, so clients can tell them from live pushes`() {
        val frames = snapshotFrames(listOf(batch(Uuid.random())), branchId = null)
        assertEquals(BATCH_SNAPSHOT_EVENT, frames.single().event)
        assertNotEquals("batch-ready", frames.single().event)
    }

    @Test
    fun `branch-scoped connections only get their own branch`() {
        val mine = Uuid.random()
        val frames = snapshotFrames(listOf(batch(mine), batch(Uuid.random())), branchId = mine.toString())
        assertEquals(1, frames.size)
    }
}
