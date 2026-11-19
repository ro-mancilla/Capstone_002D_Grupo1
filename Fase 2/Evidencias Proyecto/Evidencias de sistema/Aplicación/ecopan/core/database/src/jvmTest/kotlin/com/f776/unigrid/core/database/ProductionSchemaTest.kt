package com.f776.unigrid.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import com.f776.unigrid.core.database.entity.ProductionBatchEntity
import com.f776.unigrid.core.database.entity.StockMovementEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Schema + atomicity smoke tests for `production_batches` (06 §1, §4), added in schema v8. */
class ProductionSchemaTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val now: Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `logBatch persists the batch, its movements, and every outbox row in one call`() = runTest {
        val dao = database.productionBatchDao()
        val batchId = Uuid.random()
        val movementId = Uuid.random()

        dao.logBatch(
            batch(batchId),
            listOf(movement(movementId, batchId)),
            listOf(outboxRow(batchId, "PRODUCTION_BATCH"), outboxRow(movementId, "STOCK_MOVEMENT")),
        )

        assertEquals(batchId, dao.observeTodaysBatches(branchId, dayStart(), dayEnd()).first().single().id)
        assertEquals(2, database.outboxDao().observePendingCount().first())
    }

    @Test
    fun `logBatch with no side-effect movements still writes the batch and its own outbox row`() = runTest {
        val dao = database.productionBatchDao()
        val batchId = Uuid.random()

        dao.logBatch(batch(batchId), emptyList(), listOf(outboxRow(batchId, "PRODUCTION_BATCH")))

        assertEquals(batchId, dao.observeTodaysBatches(branchId, dayStart(), dayEnd()).first().single().id)
        assertEquals(1, database.outboxDao().observePendingCount().first())
    }

    @Test
    fun `todaysBatches filters to the branch and the given time window`() = runTest {
        val dao = database.productionBatchDao()
        val inRangeId = Uuid.random()
        val outOfRangeId = Uuid.random()
        val otherBranchId = Uuid.random()
        dao.logBatch(batch(inRangeId, timestamp = now), emptyList(), listOf(outboxRow(inRangeId, "PRODUCTION_BATCH")))
        dao.logBatch(
            batch(outOfRangeId, timestamp = now - 48.hours),
            emptyList(),
            listOf(outboxRow(outOfRangeId, "PRODUCTION_BATCH")),
        )
        dao.logBatch(
            batch(otherBranchId, branchId = otherBranchId, timestamp = now),
            emptyList(),
            listOf(outboxRow(otherBranchId, "PRODUCTION_BATCH")),
        )

        val result = dao.observeTodaysBatches(branchId, dayStart(), dayEnd()).first()

        assertEquals(listOf(inRangeId), result.map { it.id })
    }

    private fun dayStart() = now.toEpochMilliseconds() - 1.hours.inWholeMilliseconds
    private fun dayEnd() = now.toEpochMilliseconds() + 1.hours.inWholeMilliseconds

    private fun batch(id: Uuid, branchId: Uuid = this.branchId, timestamp: Instant = now) = ProductionBatchEntity(
        id = id,
        tenantId = tenantId,
        branchId = branchId,
        bakerId = Uuid.random(),
        productId = Uuid.random(),
        productTypeName = "Marraqueta",
        mixWeightGrams = 10_000,
        estimatedUnits = 22,
        loggedAt = timestamp,
    )

    private fun movement(id: Uuid, sourceBatchId: Uuid) = StockMovementEntity(
        id = id,
        tenantId = tenantId,
        branchId = branchId,
        ingredientId = Uuid.random(),
        deltaGrams = -5_952,
        kind = "FORMULA_DECREMENT",
        sourceBatchId = sourceBatchId,
        note = null,
        recordedAt = now,
        recordedBy = null,
    )

    private fun outboxRow(mutationId: Uuid, entityType: String) = OutboxEntity(
        mutationId = mutationId,
        entityType = entityType,
        opType = "CREATE",
        payload = """{"id":"$mutationId"}""",
        createdAt = now,
        state = OutboxState.PENDING,
    )
}
