package com.f776.ecopan.data

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.IngredientRepositoryImpl
import com.f776.ecopan.data.database.repository.OutboxMessage
import com.f776.ecopan.data.database.repository.OutboxRepositoryImpl
import com.f776.ecopan.data.database.repository.ProductionBatchRepositoryImpl
import com.f776.ecopan.data.database.repository.StockMovementRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.database.repository.model.DecrementMovement
import com.f776.ecopan.data.domain.Role
import com.f776.ecopan.data.messagebroker.model.BatchProcessed
import com.f776.ecopan.data.outbox.OutboxTopic
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** #143 (and #137's booking day): one batch's decrement commits whole or not at all. */
class CompleteDecrementTest : RepositoryTestBase() {

    private val outbox = OutboxRepositoryImpl(Clock.System)
    private val batches = ProductionBatchRepositoryImpl(outbox)
    private val movements = StockMovementRepositoryImpl()
    private val tenantId = Uuid.random()
    private lateinit var branchId: Uuid
    private lateinit var flourId: Uuid
    private lateinit var waterId: Uuid
    private lateinit var batchId: Uuid
    private val mixedAt = Instant.parse("2026-03-12T06:30:00Z")

    @BeforeEach
    fun setUp(): Unit = kotlinx.coroutines.runBlocking {
        truncateAll()
        TenantRepositoryImpl().create(tenantId, "reference", "Reference Bakery", "CLP")
        branchId = BranchRepositoryImpl().create(Uuid.random(), tenantId, "Main").id
        val baker = UserRepositoryImpl().create(Uuid.random(), tenantId, branchId, "baker1", "hash", null, "Baker", Role.BAKER)
        val product = CatalogRepositoryImpl().createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)
        flourId = IngredientRepositoryImpl().create(Uuid.random(), tenantId, "Flour", null).id
        waterId = IngredientRepositoryImpl().create(Uuid.random(), tenantId, "Water", null).id
        // Mixed on the 12th, synced (received) today.
        batchId = batches.create(Uuid.random(), tenantId, branchId, baker.id, product.id, 10_000, mixedAt, false, "phone").value.id
        // Drain the PRODUCTION_BATCH row the create enqueued; these tests look only at what the decrement adds.
        outbox.relayDue(10) {}
        Unit
    }

    private suspend fun notices(): List<OutboxMessage> = mutableListOf<OutboxMessage>().also { out -> outbox.relayDue(10) { out += it } }

    @Test
    fun `movements, status and notice commit together, booked on the mix day`() = runTest {
        val done = batches.completeDecrement(batchId, DecrementStatus.APPLIED, listOf(DecrementMovement(flourId, -6_000), DecrementMovement(waterId, -4_000)))

        assertTrue(done)
        assertEquals(DecrementStatus.APPLIED, batches.findById(batchId)!!.decrementStatus)
        val notice = notices().single()
        assertEquals(OutboxTopic.BATCH_PROCESSED, notice.topic)
        assertEquals(BatchProcessed(batchId.toString(), tenantId.toString(), branchId.toString(), "APPLIED"), Json.decodeFromString(notice.payload))

        // #137: the reconciled report for the day it was mixed includes the late batch.
        val day = movements.reconcileByIngredient(tenantId, branchId, Instant.parse("2026-03-12T03:00:00Z"), Instant.parse("2026-03-13T03:00:00Z"))
            .associateBy { it.ingredientId }
        assertEquals(-6_000, day.getValue(flourId).decrementedGrams)
        assertEquals(1, day.getValue(flourId).batchCount)
    }

    @Test
    fun `a failure midway leaves no movements, no notice and the batch retryable`() = runTest {
        val noSuchIngredient = Uuid.random()
        runCatching {
            batches.completeDecrement(batchId, DecrementStatus.APPLIED, listOf(DecrementMovement(flourId, -6_000), DecrementMovement(noSuchIngredient, -4_000)))
        }.also { assertTrue(it.isFailure, "the dangling ingredient should fail the insert") }

        assertEquals(DecrementStatus.PENDING, batches.findById(batchId)!!.decrementStatus)
        assertEquals(0, movements.countDecrementBatches(tenantId, branchId, Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2100-01-01T00:00:00Z")))
        assertEquals(emptyList(), notices())

        // ... and a retry applies it.
        assertTrue(batches.completeDecrement(batchId, DecrementStatus.APPLIED, listOf(DecrementMovement(flourId, -6_000))))
        assertEquals(DecrementStatus.APPLIED, batches.findById(batchId)!!.decrementStatus)
    }

    @Test
    fun `an applied batch is never overwritten by a later failure`() = runTest {
        batches.completeDecrement(batchId, DecrementStatus.APPLIED, listOf(DecrementMovement(flourId, -6_000)))
        notices()

        assertFalse(batches.completeDecrement(batchId, DecrementStatus.FAILED, emptyList()))

        assertEquals(DecrementStatus.APPLIED, batches.findById(batchId)!!.decrementStatus)
        assertEquals(emptyList(), notices(), "a no-op completion announces nothing")
    }
}
