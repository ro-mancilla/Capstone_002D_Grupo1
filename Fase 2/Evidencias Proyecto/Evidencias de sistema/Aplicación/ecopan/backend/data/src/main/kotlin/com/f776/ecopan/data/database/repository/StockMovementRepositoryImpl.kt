package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.entity.StockMovementTable
import com.f776.ecopan.data.database.nextServerSeq
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.IngredientReconciliation
import com.f776.ecopan.data.database.repository.model.IngredientStock
import com.f776.ecopan.data.database.repository.model.StockMovement
import org.jetbrains.exposed.v1.core.Case
import org.jetbrains.exposed.v1.core.Count
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class StockMovementRepositoryImpl : StockMovementRepository {

    override suspend fun append(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid,
        ingredientId: Uuid,
        deltaGrams: Long,
        kind: StockMovementKind,
        sourceBatchId: Uuid?,
        note: String?,
        createdBy: Uuid?,
        occurredAt: Instant
    ): IngestResult<StockMovement> = suspendTransaction {
        insertMovementIgnoringDuplicates(id, tenantId, branchId, ingredientId, deltaGrams, kind, sourceBatchId, note, createdBy, occurredAt)
    }

    override suspend fun findById(id: Uuid): StockMovement? = suspendTransaction {
        findMovementById(id)
    }

    override suspend fun sumByIngredient(tenantId: Uuid, branchId: Uuid): List<IngredientStock> = suspendTransaction {
        val sumExpr = StockMovementTable.deltaGrams.sum()
        StockMovementTable
            .select(StockMovementTable.ingredientId, sumExpr)
            .where { (StockMovementTable.tenantId eq tenantId) and (StockMovementTable.branchId eq branchId) }
            .groupBy(StockMovementTable.ingredientId)
            .map { IngredientStock(it[StockMovementTable.ingredientId], it[sumExpr] ?: 0L) }
    }

    override suspend fun reconcileByIngredient(
        tenantId: Uuid,
        branchId: Uuid,
        from: Instant,
        to: Instant
    ): List<IngredientReconciliation> = suspendTransaction {
        val table = StockMovementTable
        val before = table.occurredAt less from
        val inWindow = (table.occurredAt greaterEq from) and (table.occurredAt less to)
        fun sumWhere(condition: Op<Boolean>) = Case().When(condition, table.deltaGrams).Else(longLiteral(0)).sum()

        val opening = sumWhere(before)
        val decrementWindow = inWindow and (table.kind eq StockMovementKind.FORMULA_DECREMENT)
        val purchased = sumWhere(inWindow and (table.kind eq StockMovementKind.PURCHASE))
        val decremented = sumWhere(decrementWindow)
        val adjusted = sumWhere(inWindow and (table.kind eq StockMovementKind.MANUAL_ADJUSTMENT))
        val batches = Count(
            Case()
                .When(decrementWindow, table.sourceBatchId)
                .Else(Op.nullOp()),
            distinct = true
        )

        table
            .select(table.ingredientId, opening, purchased, decremented, adjusted, batches)
            .where { (table.tenantId eq tenantId) and (table.branchId eq branchId) and (table.occurredAt less to) }
            .groupBy(table.ingredientId)
            .map {
                IngredientReconciliation(
                    ingredientId = it[table.ingredientId],
                    openingGrams = it[opening] ?: 0L,
                    purchasedGrams = it[purchased] ?: 0L,
                    decrementedGrams = it[decremented] ?: 0L,
                    adjustedGrams = it[adjusted] ?: 0L,
                    batchCount = it[batches].toInt()
                )
            }
    }

    override suspend fun countDecrementBatches(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): Int =
        suspendTransaction {
            val table = StockMovementTable
            val batches = Count(table.sourceBatchId, distinct = true)
            table
                .select(batches)
                .where {
                    (table.tenantId eq tenantId) and (table.branchId eq branchId) and
                        (table.kind eq StockMovementKind.FORMULA_DECREMENT) and
                        (table.occurredAt greaterEq from) and (table.occurredAt less to)
                }
                .single()[batches]
                .toInt()
        }

    override suspend fun listMovements(
        tenantId: Uuid,
        branchId: Uuid?,
        ingredientId: Uuid?,
        from: Instant?,
        to: Instant?,
        limit: Int,
        offset: Int
    ): List<StockMovement> = suspendTransaction {
        StockMovementTable.selectAll()
            .where {
                var condition = (StockMovementTable.tenantId eq tenantId)
                branchId?.let { condition = condition and (StockMovementTable.branchId eq it) }
                ingredientId?.let { condition = condition and (StockMovementTable.ingredientId eq it) }
                from?.let { condition = condition and (StockMovementTable.occurredAt greaterEq it) }
                to?.let { condition = condition and (StockMovementTable.occurredAt lessEq it) }
                condition
            }
            .orderBy(StockMovementTable.occurredAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toStockMovement() }
    }

    override suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<StockMovement> = suspendTransaction {
        StockMovementTable.selectAll()
            .where { (StockMovementTable.tenantId eq tenantId) and (StockMovementTable.serverSeq greater since) }
            .orderBy(StockMovementTable.serverSeq, SortOrder.ASC)
            .limit(limit)
            .map { it.toStockMovement() }
    }

}

/**
 * The one insert path for `stock_movements`, in the CALLER's transaction: [StockMovementRepositoryImpl.append]
 * and [ProductionBatchRepositoryImpl.completeDecrement] both use it, so a decrement written as part
 * of a larger transaction keeps the same `server_seq` stamping and duplicate guard.
 */
internal fun JdbcTransaction.insertMovementIgnoringDuplicates(
    id: Uuid,
    tenantId: Uuid,
    branchId: Uuid,
    ingredientId: Uuid,
    deltaGrams: Long,
    kind: StockMovementKind,
    sourceBatchId: Uuid?,
    note: String?,
    createdBy: Uuid?,
    occurredAt: Instant,
): IngestResult<StockMovement> {
    val seq = nextServerSeq()
    val statement = StockMovementTable.insertIgnore {
        it[StockMovementTable.id] = id
        it[StockMovementTable.tenantId] = tenantId
        it[StockMovementTable.branchId] = branchId
        it[StockMovementTable.ingredientId] = ingredientId
        it[StockMovementTable.deltaGrams] = deltaGrams
        it[StockMovementTable.kind] = kind
        it[StockMovementTable.sourceBatchId] = sourceBatchId
        it[StockMovementTable.note] = note
        it[StockMovementTable.createdBy] = createdBy
        it[StockMovementTable.occurredAt] = occurredAt
        it[StockMovementTable.serverSeq] = seq
        it[StockMovementTable.receivedAt] = Clock.System.now()
    }

    val existing = findMovementById(id)
        ?: sourceBatchId?.let { findMovementBySourceBatchAndIngredient(it, ingredientId) }
        ?: error("stock movement insert was ignored but no conflicting row could be located")

    return if (statement.insertedCount > 0) IngestResult.Applied(existing) else IngestResult.Duplicate(existing)
}

private fun findMovementById(id: Uuid): StockMovement? =
    StockMovementTable.selectAll().where { StockMovementTable.id eq id }.singleOrNull()?.toStockMovement()

private fun findMovementBySourceBatchAndIngredient(sourceBatchId: Uuid, ingredientId: Uuid): StockMovement? =
    StockMovementTable.selectAll()
        .where { (StockMovementTable.sourceBatchId eq sourceBatchId) and (StockMovementTable.ingredientId eq ingredientId) }
        .singleOrNull()?.toStockMovement()

private fun ResultRow.toStockMovement() = StockMovement(
    id = this[StockMovementTable.id],
    tenantId = this[StockMovementTable.tenantId],
    branchId = this[StockMovementTable.branchId],
    ingredientId = this[StockMovementTable.ingredientId],
    deltaGrams = this[StockMovementTable.deltaGrams],
    kind = this[StockMovementTable.kind],
    sourceBatchId = this[StockMovementTable.sourceBatchId],
    note = this[StockMovementTable.note],
    createdBy = this[StockMovementTable.createdBy],
    occurredAt = this[StockMovementTable.occurredAt],
    serverSeq = this[StockMovementTable.serverSeq],
    receivedAt = this[StockMovementTable.receivedAt]
)
