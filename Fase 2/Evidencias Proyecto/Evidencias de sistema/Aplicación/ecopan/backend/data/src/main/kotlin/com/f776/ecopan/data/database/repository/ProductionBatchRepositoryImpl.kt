package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.ProductionBatchTable
import com.f776.ecopan.data.database.nextServerSeq
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.repository.model.DecrementMovement
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.messagebroker.model.BatchProcessed
import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage
import com.f776.ecopan.data.outbox.OutboxTopic
import kotlinx.serialization.json.Json
import com.f776.ecopan.data.database.repository.model.ProductionBatch
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ProductionBatchRepositoryImpl(
    private val outbox: OutboxRepository = OutboxRepositoryImpl(kotlin.time.Clock.System),
) : ProductionBatchRepository {

    override suspend fun create(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid,
        bakerId: Uuid,
        productId: Uuid,
        mixWeightGrams: Long,
        batchAt: Instant,
        movementsMinted: Boolean,
        deviceId: String
    ): IngestResult<ProductionBatch> = suspendTransaction {
        val seq = nextServerSeq()
        val statement = ProductionBatchTable.insertIgnore {
            it[ProductionBatchTable.id] = id
            it[ProductionBatchTable.tenantId] = tenantId
            it[ProductionBatchTable.branchId] = branchId
            it[ProductionBatchTable.bakerId] = bakerId
            it[ProductionBatchTable.productId] = productId
            it[ProductionBatchTable.mixWeightGrams] = mixWeightGrams
            it[ProductionBatchTable.batchAt] = batchAt
            it[ProductionBatchTable.movementsMinted] = movementsMinted
            it[ProductionBatchTable.decrementStatus] = DecrementStatus.PENDING
            it[ProductionBatchTable.deviceId] = deviceId
            it[ProductionBatchTable.serverSeq] = seq
            it[ProductionBatchTable.receivedAt] = kotlin.time.Clock.System.now()
        }

        val row = findByIdInternal(id)!!
        if (statement.insertedCount == 0) return@suspendTransaction IngestResult.Duplicate(row)
        // #142: announced in the batch's own transaction, so a committed batch is always decremented
        // and announced (by the app's outbox relay) even when the broker is down right now, and a
        // re-push -- a Duplicate -- never announces it twice.
        outbox.enqueue(
            OutboxTopic.PRODUCTION_BATCH,
            Json.encodeToString(
                ProductionBatchMessage(
                    batchId = row.id.toString(),
                    tenantId = row.tenantId.toString(),
                    branchId = row.branchId.toString(),
                    productId = row.productId.toString(),
                    mixWeightGrams = row.mixWeightGrams,
                    movementsMinted = row.movementsMinted,
                )
            ),
        )
        IngestResult.Applied(row)
    }

    override suspend fun findById(id: Uuid): ProductionBatch? = suspendTransaction {
        findByIdInternal(id)
    }

    override suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<ProductionBatch> = suspendTransaction {
        ProductionBatchTable.selectAll()
            .where { (ProductionBatchTable.tenantId eq tenantId) and (ProductionBatchTable.serverSeq greater since) }
            .orderBy(ProductionBatchTable.serverSeq, SortOrder.ASC)
            .limit(limit)
            .map { it.toProductionBatch() }
    }

    override suspend fun listReceivedSince(tenantId: Uuid, since: Instant): List<ProductionBatch> = suspendTransaction {
        ProductionBatchTable.selectAll()
            .where { (ProductionBatchTable.tenantId eq tenantId) and (ProductionBatchTable.receivedAt greaterEq since) }
            .orderBy(ProductionBatchTable.serverSeq, SortOrder.ASC)
            .map { it.toProductionBatch() }
    }

    override suspend fun completeDecrement(id: Uuid, status: DecrementStatus, movements: List<DecrementMovement>): Boolean =
        suspendTransaction {
            require(status != DecrementStatus.PENDING) { "a decrement completes to a terminal status" }
            val batch = findByIdInternal(id) ?: return@suspendTransaction false
            val claimed = ProductionBatchTable.update({
                (ProductionBatchTable.id eq id) and (ProductionBatchTable.decrementStatus eq DecrementStatus.PENDING)
            }) {
                it[ProductionBatchTable.decrementStatus] = status
            } == 1
            if (!claimed) return@suspendTransaction false
            movements.forEach { line ->
                insertMovementIgnoringDuplicates(
                    id = Uuid.random(),
                    tenantId = batch.tenantId,
                    branchId = batch.branchId,
                    ingredientId = line.ingredientId,
                    deltaGrams = line.deltaGrams,
                    kind = StockMovementKind.FORMULA_DECREMENT,
                    sourceBatchId = batch.id,
                    note = null,
                    createdBy = null,
                    occurredAt = batch.batchAt,
                )
            }
            outbox.enqueue(
                OutboxTopic.BATCH_PROCESSED,
                Json.encodeToString(BatchProcessed(batch.id.toString(), batch.tenantId.toString(), batch.branchId.toString(), status.name)),
            )
            true
        }

    override suspend fun updateDecrementStatus(id: Uuid, status: DecrementStatus): ProductionBatch? = suspendTransaction {
        ProductionBatchTable.update({ ProductionBatchTable.id eq id }) {
            it[ProductionBatchTable.decrementStatus] = status
        }
        findByIdInternal(id)
    }

    private fun findByIdInternal(id: Uuid): ProductionBatch? =
        ProductionBatchTable.selectAll().where { ProductionBatchTable.id eq id }.singleOrNull()?.toProductionBatch()

    private fun ResultRow.toProductionBatch() = ProductionBatch(
        id = this[ProductionBatchTable.id],
        tenantId = this[ProductionBatchTable.tenantId],
        branchId = this[ProductionBatchTable.branchId],
        bakerId = this[ProductionBatchTable.bakerId],
        productId = this[ProductionBatchTable.productId],
        mixWeightGrams = this[ProductionBatchTable.mixWeightGrams],
        batchAt = this[ProductionBatchTable.batchAt],
        movementsMinted = this[ProductionBatchTable.movementsMinted],
        decrementStatus = this[ProductionBatchTable.decrementStatus],
        deviceId = this[ProductionBatchTable.deviceId],
        serverSeq = this[ProductionBatchTable.serverSeq],
        receivedAt = this[ProductionBatchTable.receivedAt]
    )
}
