package com.f776.unigrid.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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

/** Schema smoke tests for `stock_movements` (06 §1), added in schema v7. */
class InventorySchemaTest {

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
    fun `deltas sum per ingredient within a branch, negative sums allowed`() = runTest {
        val dao = database.stockMovementDao()
        val flourId = Uuid.random()
        val waterId = Uuid.random()
        dao.insertMovements(
            listOf(
                movement(flourId, deltaGrams = 25_000),
                movement(flourId, deltaGrams = -30_000),
                movement(waterId, deltaGrams = 10_000),
            ),
        )

        val sums = dao.observeDeltaSumsByBranch(branchId).first().associate { it.ingredientId to it.totalDeltaGrams }

        assertEquals(-5_000L, sums[flourId])
        assertEquals(10_000L, sums[waterId])
    }

    @Test
    fun `sums are scoped per branch`() = runTest {
        val dao = database.stockMovementDao()
        val ingredientId = Uuid.random()
        val otherBranch = Uuid.random()
        dao.insertMovements(
            listOf(
                movement(ingredientId, deltaGrams = 1_000, branchId = branchId),
                movement(ingredientId, deltaGrams = 9_999, branchId = otherBranch),
            ),
        )

        val sums = dao.observeDeltaSumsByBranch(branchId).first()

        assertEquals(1_000L, sums.single().totalDeltaGrams)
    }

    @Test
    fun `movements for an ingredient are ordered newest first`() = runTest {
        val dao = database.stockMovementDao()
        val ingredientId = Uuid.random()
        dao.insertMovements(
            listOf(
                movement(ingredientId, deltaGrams = 1, recordedAt = now),
                movement(ingredientId, deltaGrams = 2, recordedAt = now + 1.hours),
            ),
        )

        val rows = dao.observeByIngredient(branchId, ingredientId).first()

        assertEquals(listOf(2L, 1L), rows.map { it.deltaGrams })
    }

    private fun movement(
        ingredientId: Uuid,
        deltaGrams: Long,
        branchId: Uuid = this.branchId,
        recordedAt: Instant = now,
    ) = StockMovementEntity(
        id = Uuid.random(),
        tenantId = tenantId,
        branchId = branchId,
        ingredientId = ingredientId,
        deltaGrams = deltaGrams,
        kind = "PURCHASE",
        sourceBatchId = null,
        note = null,
        recordedAt = recordedAt,
        recordedBy = Uuid.random(),
    )
}
