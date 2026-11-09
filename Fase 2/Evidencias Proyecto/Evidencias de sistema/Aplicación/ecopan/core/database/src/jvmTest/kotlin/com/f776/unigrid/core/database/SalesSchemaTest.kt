package com.f776.unigrid.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import com.f776.unigrid.core.database.entity.SaleEntity
import com.f776.unigrid.core.database.entity.SaleLineEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Schema + atomicity smoke tests for `sales`/`sale_lines` (05 §2, §9), added in schema v6. */
class SalesSchemaTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    private val branchId = Uuid.random()
    private val now: Instant = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun `recordSale persists the sale, its lines, and an outbox row in one call`() = runTest {
        val dao = database.saleDao()
        val saleId = Uuid.random()
        val sale = sale(saleId)
        val lines = listOf(line(saleId))
        val outboxRow = outboxRow(saleId)

        dao.recordSale(sale, lines, outboxRow)

        assertEquals(sale.copy(receiptNumber = 1), dao.observeSalesInRange(branchId, dayStart(), dayEnd()).first().single())
        assertEquals(lines, dao.observeLinesInRange(branchId, dayStart(), dayEnd()).first())
        assertEquals(1, database.outboxDao().observePendingCount().first())
    }

    @Test
    fun `each recorded sale gets the next per-till receipt number, gapless and never reused`() = runTest {
        val dao = database.saleDao()
        val ids = List(3) { Uuid.random() }

        val numbers = ids.map { id -> dao.recordSale(sale(id), listOf(line(id)), outboxRow(id)) }

        assertEquals(listOf(1, 2, 3), numbers)
        val stored = dao.observeSalesInRange(branchId, dayStart(), dayEnd()).first().associate { it.id to it.receiptNumber }
        assertEquals(ids.zip(numbers).toMap(), stored)
    }

    @Test
    fun `the outbox row carries the sale's own client-minted id as its mutationId`() = runTest {
        val dao = database.saleDao()
        val saleId = Uuid.random()

        dao.recordSale(sale(saleId), listOf(line(saleId)), outboxRow(saleId))

        assertEquals(OutboxState.PENDING, database.outboxDao().stateOf(saleId))
    }

    @Test
    fun `observeSalesInRange filters to the branch and the given time window`() = runTest {
        val dao = database.saleDao()
        val otherBranch = Uuid.random()
        val inRangeId = Uuid.random()
        val outOfRangeId = Uuid.random()
        val otherBranchId = Uuid.random()
        dao.recordSale(sale(inRangeId, timestamp = now), listOf(line(inRangeId)), outboxRow(inRangeId))
        dao.recordSale(
            sale(outOfRangeId, timestamp = now - 48.hours),
            listOf(line(outOfRangeId)),
            outboxRow(outOfRangeId),
        )
        dao.recordSale(
            sale(otherBranchId, branchId = otherBranch, timestamp = now),
            listOf(line(otherBranchId)),
            outboxRow(otherBranchId),
        )

        val result = dao.observeSalesInRange(branchId, dayStart(), dayEnd()).first()

        assertEquals(listOf(inRangeId), result.map { it.id })
    }

    @Test
    fun `sale_lines cascade-delete when their parent sale is deleted`() = runTest {
        val dao = database.saleDao()
        val saleId = Uuid.random()
        dao.recordSale(sale(saleId), listOf(line(saleId)), outboxRow(saleId))

        dao.clearSales()

        assertTrue(dao.observeLinesInRange(branchId, dayStart(), dayEnd()).first().isEmpty())
    }

    private fun dayStart() = now.toEpochMilliseconds() - 1.hours.inWholeMilliseconds
    private fun dayEnd() = now.toEpochMilliseconds() + 1.hours.inWholeMilliseconds

    @Test
    fun `unsynced units per product count only sales the server has not acknowledged`() = runTest {
        val dao = database.saleDao()
        val cola = Uuid.random()
        val water = Uuid.random()

        fun unitLine(saleId: Uuid, productId: Uuid, quantity: Int) = line(saleId).copy(
            productId = productId, soldBy = SoldBy.UNIT, weightGrams = null, quantity = quantity,
        )

        val pendingA = Uuid.random()
        val pendingB = Uuid.random()
        val inFlight = Uuid.random()
        val acked = Uuid.random()
        dao.recordSale(sale(pendingA), listOf(unitLine(pendingA, cola, 2), unitLine(pendingA, water, 1)), outboxRow(pendingA))
        dao.recordSale(sale(pendingB), listOf(unitLine(pendingB, cola, 3)), outboxRow(pendingB))
        dao.recordSale(sale(inFlight), listOf(unitLine(inFlight, cola, 1)), outboxRow(inFlight))
        database.outboxDao().markInFlight(listOf(inFlight))
        dao.recordSale(sale(acked), listOf(unitLine(acked, cola, 50)), outboxRow(acked))
        database.outboxDao().deleteAcked(listOf(acked))
        // A weighed line has no quantity and must not poison the sum.
        val weighed = Uuid.random()
        dao.recordSale(sale(weighed), listOf(line(weighed).copy(productId = cola)), outboxRow(weighed))

        val units = dao.observeUnsyncedUnitsByProduct().first().associate { it.productId to it.units }

        assertEquals(mapOf(cola to 6L, water to 1L), units)
    }

    private fun sale(id: Uuid, branchId: Uuid = this.branchId, timestamp: Instant = now) = SaleEntity(
        id = id,
        branchId = branchId,
        cashierId = Uuid.random(),
        timestamp = timestamp,
        paymentMethod = PaymentMethod.CASH,
        totalMinor = 1010,
        cashTenderedMinor = 2000,
        changeMinor = 990,
    )

    private fun line(saleId: Uuid) = SaleLineEntity(
        id = Uuid.random(),
        saleId = saleId,
        productId = Uuid.random(),
        productTypeSnapshot = "Marraqueta",
        soldBy = SoldBy.WEIGHT,
        weightGrams = 842,
        quantity = null,
        unitPriceMinorSnapshot = 1200,
        lineTotalMinor = 1010,
    )

    private fun outboxRow(mutationId: Uuid) = OutboxEntity(
        mutationId = mutationId,
        entityType = "SALE",
        opType = "CREATE",
        payload = """{"id":"$mutationId"}""",
        createdAt = now,
        state = OutboxState.PENDING,
    )
}
