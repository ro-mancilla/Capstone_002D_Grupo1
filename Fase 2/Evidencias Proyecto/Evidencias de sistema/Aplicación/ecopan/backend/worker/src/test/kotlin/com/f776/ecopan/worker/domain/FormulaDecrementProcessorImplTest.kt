package com.f776.ecopan.worker.domain

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.repository.model.FormulaLine
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage
import com.f776.ecopan.worker.fakes.FakeCatalogRepository
import com.f776.ecopan.worker.fakes.FakeProductionBatchRepository
import com.f776.ecopan.worker.fakes.FakeStockMovementRepository
import com.f776.ecopan.worker.fakes.FakeTenantRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import kotlin.time.Clock
import kotlin.uuid.Uuid

class FormulaDecrementProcessorImplTest {

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val bakerId = Uuid.random()
    private val productId = Uuid.random()
    private val flourId = Uuid.random()
    private val waterId = Uuid.random()

    private fun newBatch(
        batches: FakeProductionBatchRepository,
        movementsMinted: Boolean = false,
        mixWeightGrams: Long = 10_000,
        batchAt: kotlin.time.Instant = Clock.System.now(),
    ): Uuid {
        val id = Uuid.random()
        runTestBlock { batches.create(id, tenantId, branchId, bakerId, productId, mixWeightGrams, batchAt, movementsMinted, "device-1") }
        return id
    }

    private fun runTestBlock(block: suspend () -> Unit) = kotlinx.coroutines.runBlocking { block() }

    private fun processor(
        batches: FakeProductionBatchRepository = FakeProductionBatchRepository(),
        catalog: FakeCatalogRepository = FakeCatalogRepository(),
        tenants: FakeTenantRepository = FakeTenantRepository(),
        decrementEnabled: Boolean = true,
    ) = FormulaDecrementProcessorImpl(batches, catalog, tenants, decrementEnabled, retryDelay = { 10.milliseconds })

    private fun message(batchId: Uuid, mixWeightGrams: Long = 10_000, movementsMinted: Boolean = false) = ProductionBatchMessage(
        batchId = batchId.toString(), tenantId = tenantId.toString(), branchId = branchId.toString(),
        productId = productId.toString(), mixWeightGrams = mixWeightGrams, movementsMinted = movementsMinted
    )

    @Test
    fun `PENDING to APPLIED writes N movements and publishes APPLIED`() = runTest {
        val movements = FakeStockMovementRepository()
        val batches = FakeProductionBatchRepository(movements)
        val catalog = FakeCatalogRepository()
        val tenants = FakeTenantRepository()

        tenants.seed(com.f776.ecopan.data.database.repository.model.Tenant(tenantId, "ref", "Ref", "CLP", setOf(Module.INVENTORY_FORMULAS), Clock.System.now()))
        catalog.seedProduct(productId, tenantId)
        catalog.seedFormula(productId, tenantId, listOf(FormulaLine(Uuid.random(), flourId, 10_000), FormulaLine(Uuid.random(), waterId, 6_500)))

        val batchId = newBatch(batches, movementsMinted = false)
        val sut = processor(batches, catalog, tenants)

        sut.processMessage(message(batchId))

        assertEquals(DecrementStatus.APPLIED, batches.findById(batchId)!!.decrementStatus)
        assertEquals(2, movements.movements.values.count { it.sourceBatchId == batchId })
        assertTrue(movements.movements.values.all { it.deltaGrams < 0 })
        assertEquals(1, batches.notices.size)
        assertEquals("APPLIED", batches.notices.single().status)
    }

    /** #137: a batch that syncs days late is booked on the day it was mixed, not the day it arrived. */
    @Test
    fun `movements carry the batch's own batchAt, not the worker's clock`() = runTest {
        val movements = FakeStockMovementRepository()
        val batches = FakeProductionBatchRepository(movements)
        val catalog = FakeCatalogRepository()
        val tenants = FakeTenantRepository()
        tenants.seed(com.f776.ecopan.data.database.repository.model.Tenant(tenantId, "ref", "Ref", "CLP", setOf(Module.INVENTORY_FORMULAS), Clock.System.now()))
        catalog.seedProduct(productId, tenantId)
        catalog.seedFormula(productId, tenantId, listOf(FormulaLine(Uuid.random(), flourId, 10_000)))
        val mixedAt = kotlin.time.Instant.parse("2026-03-12T06:30:00Z")
        val batchId = newBatch(batches, batchAt = mixedAt)

        processor(batches, catalog, tenants).processMessage(message(batchId))

        val booked = movements.movements.values.filter { it.sourceBatchId == batchId }
        assertTrue(booked.isNotEmpty())
        assertTrue(booked.all { it.occurredAt == mixedAt }, "booked at ${booked.map { it.occurredAt }}")
    }

    @Test
    fun `redelivery after APPLIED is a no-op`() = runTest {
        val movements = FakeStockMovementRepository()
        val batches = FakeProductionBatchRepository(movements)
        val catalog = FakeCatalogRepository()
        val tenants = FakeTenantRepository()

        tenants.seed(com.f776.ecopan.data.database.repository.model.Tenant(tenantId, "ref", "Ref", "CLP", setOf(Module.INVENTORY_FORMULAS), Clock.System.now()))
        catalog.seedProduct(productId, tenantId)
        catalog.seedFormula(productId, tenantId, listOf(FormulaLine(Uuid.random(), flourId, 10_000)))

        val batchId = newBatch(batches)
        val sut = processor(batches, catalog, tenants)

        sut.processMessage(message(batchId))
        val movementsAfterFirst = movements.movements.size
        val publishedAfterFirst = batches.notices.size

        sut.processMessage(message(batchId))

        assertEquals(movementsAfterFirst, movements.movements.size, "redelivery must not write more movements")
        assertEquals(publishedAfterFirst, batches.notices.size, "redelivery must not publish again")
    }

    @Test
    fun `movementsMinted true short-circuits to APPLIED with zero movements`() = runTest {
        val movements = FakeStockMovementRepository()
        val batches = FakeProductionBatchRepository(movements)
        val catalog = FakeCatalogRepository()
        val tenants = FakeTenantRepository()

        val batchId = newBatch(batches, movementsMinted = true)
        val sut = processor(batches, catalog, tenants)

        sut.processMessage(message(batchId, movementsMinted = true))

        assertEquals(DecrementStatus.APPLIED, batches.findById(batchId)!!.decrementStatus)
        assertTrue(movements.movements.isEmpty())
        assertEquals("APPLIED", batches.notices.single().status)
    }

    @Test
    fun `no formula yields SKIPPED`() = runTest {
        val movements = FakeStockMovementRepository()
        val batches = FakeProductionBatchRepository(movements)
        val catalog = FakeCatalogRepository()
        val tenants = FakeTenantRepository()

        tenants.seed(com.f776.ecopan.data.database.repository.model.Tenant(tenantId, "ref", "Ref", "CLP", setOf(Module.INVENTORY_FORMULAS), Clock.System.now()))
        catalog.seedProduct(productId, tenantId)
        // no formula seeded

        val batchId = newBatch(batches)
        val sut = processor(batches, catalog, tenants)

        sut.processMessage(message(batchId))

        assertEquals(DecrementStatus.SKIPPED, batches.findById(batchId)!!.decrementStatus)
        assertTrue(movements.movements.isEmpty())
        assertEquals("SKIPPED", batches.notices.single().status)
    }

    @Test
    fun `module disabled yields SKIPPED`() = runTest {
        val movements = FakeStockMovementRepository()
        val batches = FakeProductionBatchRepository(movements)
        val catalog = FakeCatalogRepository()
        val tenants = FakeTenantRepository()

        tenants.seed(com.f776.ecopan.data.database.repository.model.Tenant(tenantId, "ref", "Ref", "CLP", emptySet(), Clock.System.now()))
        catalog.seedProduct(productId, tenantId)
        catalog.seedFormula(productId, tenantId, listOf(FormulaLine(Uuid.random(), flourId, 10_000)))

        val batchId = newBatch(batches)
        val sut = processor(batches, catalog, tenants)

        sut.processMessage(message(batchId))

        assertEquals(DecrementStatus.SKIPPED, batches.findById(batchId)!!.decrementStatus)
        assertTrue(movements.movements.isEmpty())
    }

    @Test
    fun `DECREMENT_ENABLED false yields SKIPPED with zero movements`() = runTest {
        val movements = FakeStockMovementRepository()
        val batches = FakeProductionBatchRepository(movements)
        val catalog = FakeCatalogRepository()
        val tenants = FakeTenantRepository()

        tenants.seed(com.f776.ecopan.data.database.repository.model.Tenant(tenantId, "ref", "Ref", "CLP", setOf(Module.INVENTORY_FORMULAS), Clock.System.now()))
        catalog.seedProduct(productId, tenantId)
        catalog.seedFormula(productId, tenantId, listOf(FormulaLine(Uuid.random(), flourId, 10_000)))

        val batchId = newBatch(batches)
        val sut = processor(batches, catalog, tenants, decrementEnabled = false)

        sut.processMessage(message(batchId))

        assertEquals(DecrementStatus.SKIPPED, batches.findById(batchId)!!.decrementStatus)
        assertTrue(movements.movements.isEmpty())
    }

    private fun formulaTenant(batches: FakeProductionBatchRepository): Triple<FakeCatalogRepository, FakeTenantRepository, Uuid> {
        val catalog = FakeCatalogRepository()
        val tenants = FakeTenantRepository()
        tenants.seed(com.f776.ecopan.data.database.repository.model.Tenant(tenantId, "ref", "Ref", "CLP", setOf(Module.INVENTORY_FORMULAS), Clock.System.now()))
        catalog.seedProduct(productId, tenantId)
        catalog.seedFormula(productId, tenantId, listOf(FormulaLine(Uuid.random(), flourId, 10_000)))
        return Triple(catalog, tenants, newBatch(batches))
    }

    /** #143: a dropped connection is retried in place, and the retry applies the batch once. */
    @Test
    fun `a transient failure is retried and then applies`() = runTest {
        val movements = FakeStockMovementRepository()
        val batches = FakeProductionBatchRepository(movements)
        val (catalog, tenants, batchId) = formulaTenant(batches)
        repeat(2) { batches.completeFailures += SQLTransientConnectionException("connection reset") }

        processor(batches, catalog, tenants).processMessage(message(batchId))

        assertEquals(3, batches.completeCalls)
        assertEquals(DecrementStatus.APPLIED, batches.findById(batchId)!!.decrementStatus)
        assertEquals(1, movements.movements.size)
        assertEquals("APPLIED", batches.notices.single().status)
    }

    @Test
    fun `a transient failure is retried up to five times, then dead-lettered with the batch still PENDING`() = runTest {
        val batches = FakeProductionBatchRepository()
        val (catalog, tenants, batchId) = formulaTenant(batches)
        repeat(10) { batches.completeFailures += RuntimeException("wrapped by the ORM", SQLException("terminating connection", "57P01")) }

        assertFailsWith<RuntimeException> { processor(batches, catalog, tenants).processMessage(message(batchId)) }

        assertEquals(5, batches.completeCalls)
        assertEquals(DecrementStatus.PENDING, batches.findById(batchId)!!.decrementStatus, "left retryable, not FAILED")
        assertTrue(batches.notices.isEmpty())
    }

    @Test
    fun `a deterministic failure is dead-lettered at once and the batch marked FAILED`() = runTest {
        val batches = FakeProductionBatchRepository()
        val (catalog, tenants, batchId) = formulaTenant(batches)
        batches.completeFailures += IllegalStateException("formula line references a deleted ingredient")

        assertFailsWith<IllegalStateException> { processor(batches, catalog, tenants).processMessage(message(batchId)) }

        assertEquals(2, batches.completeCalls, "one attempt, then the FAILED mark -- no retries")
        assertEquals(DecrementStatus.FAILED, batches.findById(batchId)!!.decrementStatus)
        assertEquals("FAILED", batches.notices.single().status)
    }

    @Test
    fun `an applied batch is never overwritten by a later failure`() = runTest {
        val batches = FakeProductionBatchRepository()
        val (catalog, tenants, batchId) = formulaTenant(batches)
        processor(batches, catalog, tenants).processMessage(message(batchId))
        assertEquals(DecrementStatus.APPLIED, batches.findById(batchId)!!.decrementStatus)

        // A stale copy of the message whose processing blows up: the FAILED mark must not land.
        val stale = FormulaDecrementProcessorImpl(
            object : com.f776.ecopan.data.database.repository.ProductionBatchRepository by batches {
                override suspend fun findById(id: Uuid) = batches.findById(id)!!.copy(decrementStatus = DecrementStatus.PENDING)
                override suspend fun completeDecrement(id: Uuid, status: DecrementStatus, movements: List<com.f776.ecopan.data.database.repository.model.DecrementMovement>) =
                    if (status == DecrementStatus.FAILED) batches.completeDecrement(id, status, movements) else error("boom")
            },
            catalog, tenants, decrementEnabled = true,
        )
        assertFailsWith<IllegalStateException> { stale.processMessage(message(batchId)) }

        assertEquals(DecrementStatus.APPLIED, batches.findById(batchId)!!.decrementStatus)
        assertEquals(listOf("APPLIED"), batches.notices.map { it.status })
    }

    @Test
    fun `transient means the database or network, not the batch`() {
        assertTrue(SQLTransientConnectionException("x").isTransient())
        assertTrue(RuntimeException(SQLException("x", "40001")).isTransient())
        assertTrue(RuntimeException(java.net.SocketTimeoutException("read timed out")).isTransient())
        assertFalse(RuntimeException(SQLException("x", "23503")).isTransient())
        assertFalse(IllegalStateException("x").isTransient())
    }
}
