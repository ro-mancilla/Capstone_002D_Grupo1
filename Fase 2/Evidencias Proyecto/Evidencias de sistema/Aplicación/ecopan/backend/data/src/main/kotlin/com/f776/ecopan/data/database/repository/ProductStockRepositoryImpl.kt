package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.bumpAlmacenProductSeq
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.ProductStockMovementTable
import com.f776.ecopan.data.database.entity.SaleLineTable
import com.f776.ecopan.data.database.entity.SaleTable
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.ProductStockMovement
import com.f776.ecopan.data.database.repository.model.ProductStockSum
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.min
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ProductStockRepositoryImpl(private val clock: Clock) : ProductStockRepository {

    private val openingExpr = ProductStockMovementTable.receivedAt.min()

    override suspend fun append(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid,
        productId: Uuid,
        deltaUnits: Long,
        kind: ProductStockMovementKind,
        note: String?,
        createdBy: Uuid?,
        occurredAt: Instant
    ): IngestResult<ProductStockMovement> = suspendTransaction {
        appendMovement(id, tenantId, branchId, productId, deltaUnits, kind, note, createdBy, occurredAt, clock.now())
    }

    override suspend fun sumByProduct(tenantId: Uuid, branchId: Uuid): List<ProductStockSum> = suspendTransaction {
        val sumExpr = ProductStockMovementTable.deltaUnits.sum()
        val inBranch = (ProductStockMovementTable.tenantId eq tenantId) and (ProductStockMovementTable.branchId eq branchId)
        val openings = ProductStockMovementTable
            .select(ProductStockMovementTable.productId, openingExpr)
            .where { inBranch and (ProductStockMovementTable.kind eq ProductStockMovementKind.OPENING_COUNT) }
            .groupBy(ProductStockMovementTable.productId)
            .associate { it[ProductStockMovementTable.productId] to it[openingExpr] }
        ProductStockMovementTable
            .select(ProductStockMovementTable.productId, sumExpr)
            .where { inBranch }
            .groupBy(ProductStockMovementTable.productId)
            .map {
                val productId = it[ProductStockMovementTable.productId]
                ProductStockSum(productId, it[sumExpr] ?: 0L, openings[productId])
            }
    }

    override suspend fun soldUnitsSince(
        tenantId: Uuid,
        branchId: Uuid,
        sinceByProduct: Map<Uuid, Instant>
    ): Map<Uuid, Long> {
        if (sinceByProduct.isEmpty()) return emptyMap()
        return suspendTransaction {
            val sumExpr = SaleLineTable.quantity.sum()
            val perProduct = sinceByProduct.entries
                .map { (productId, since) -> (SaleLineTable.productId eq productId) and (SaleTable.receivedAt greaterEq since) }
                .reduce { acc, next -> acc or next }
            SaleTable
                .join(SaleLineTable, JoinType.INNER) { SaleTable.id eq SaleLineTable.saleId }
                .select(SaleLineTable.productId, sumExpr)
                .where { (SaleTable.tenantId eq tenantId) and (SaleTable.branchId eq branchId) and perProduct }
                .groupBy(SaleLineTable.productId)
                .associate { it[SaleLineTable.productId] to (it[sumExpr]?.toLong() ?: 0L) }
        }
    }

    override suspend fun listMovements(
        tenantId: Uuid,
        branchId: Uuid?,
        productId: Uuid?,
        limit: Int,
        offset: Int
    ): List<ProductStockMovement> = suspendTransaction {
        ProductStockMovementTable.selectAll()
            .where {
                var condition = (ProductStockMovementTable.tenantId eq tenantId)
                branchId?.let { condition = condition and (ProductStockMovementTable.branchId eq it) }
                productId?.let { condition = condition and (ProductStockMovementTable.productId eq it) }
                condition
            }
            .orderBy(ProductStockMovementTable.occurredAt, SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toMovement() }
    }
}

/**
 * The idempotent ledger insert, usable inside a caller's transaction (product creation appends its
 * opening count in the same one). A new row also bumps the product's `server_seq`.
 */
internal fun JdbcTransaction.appendMovement(
    id: Uuid,
    tenantId: Uuid,
    branchId: Uuid,
    productId: Uuid,
    deltaUnits: Long,
    kind: ProductStockMovementKind,
    note: String?,
    createdBy: Uuid?,
    occurredAt: Instant,
    receivedAt: Instant = Clock.System.now()
): IngestResult<ProductStockMovement> {
    val statement = ProductStockMovementTable.insertIgnore {
        it[ProductStockMovementTable.id] = id
        it[ProductStockMovementTable.tenantId] = tenantId
        it[ProductStockMovementTable.branchId] = branchId
        it[ProductStockMovementTable.productId] = productId
        it[ProductStockMovementTable.deltaUnits] = deltaUnits
        it[ProductStockMovementTable.kind] = kind
        it[ProductStockMovementTable.note] = note
        it[ProductStockMovementTable.createdBy] = createdBy
        it[ProductStockMovementTable.occurredAt] = occurredAt
        it[ProductStockMovementTable.receivedAt] = receivedAt
    }
    val existing = ProductStockMovementTable.selectAll()
        .where { ProductStockMovementTable.id eq id }
        .singleOrNull()?.toMovement()
        ?: error("product stock movement insert was ignored but no conflicting row could be located")
    return if (statement.insertedCount > 0) {
        bumpAlmacenProductSeq(listOf(productId))
        IngestResult.Applied(existing)
    } else {
        IngestResult.Duplicate(existing)
    }
}

private fun ResultRow.toMovement() = ProductStockMovement(
    id = this[ProductStockMovementTable.id],
    tenantId = this[ProductStockMovementTable.tenantId],
    branchId = this[ProductStockMovementTable.branchId],
    productId = this[ProductStockMovementTable.productId],
    deltaUnits = this[ProductStockMovementTable.deltaUnits],
    kind = this[ProductStockMovementTable.kind],
    note = this[ProductStockMovementTable.note],
    createdBy = this[ProductStockMovementTable.createdBy],
    occurredAt = this[ProductStockMovementTable.occurredAt],
    receivedAt = this[ProductStockMovementTable.receivedAt]
)
