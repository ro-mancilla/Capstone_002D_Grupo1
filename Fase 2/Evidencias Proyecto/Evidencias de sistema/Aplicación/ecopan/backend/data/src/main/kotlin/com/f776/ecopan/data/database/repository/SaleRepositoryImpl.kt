package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.SaleLineTable
import com.f776.ecopan.data.database.entity.SaleTable
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.bumpAlmacenProductSeq
import com.f776.ecopan.data.database.nextServerSeq
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.Sale
import com.f776.ecopan.data.database.repository.model.SaleLine
import com.f776.ecopan.data.database.repository.model.SaleInput
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SaleRepositoryImpl(private val clock: Clock) : SaleRepository {

    override suspend fun createWithLines(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid,
        cashierId: Uuid,
        soldAt: Instant,
        paymentMethod: PaymentMethod,
        totalMinor: Long,
        deviceId: String,
        lines: List<SaleLineInput>
    ): IngestResult<Sale> = suspendTransaction {
        val seq = nextServerSeq()
        val statement = SaleTable.insertIgnore {
            it[SaleTable.id] = id
            it[SaleTable.tenantId] = tenantId
            it[SaleTable.branchId] = branchId
            it[SaleTable.cashierId] = cashierId
            it[SaleTable.soldAt] = soldAt
            it[SaleTable.paymentMethod] = paymentMethod
            it[SaleTable.totalMinor] = totalMinor
            it[SaleTable.deviceId] = deviceId
            it[SaleTable.serverSeq] = seq
            it[SaleTable.receivedAt] = clock.now()
        }

        if (statement.insertedCount > 0) {
            lines.forEach { line ->
                SaleLineTable.insert {
                    it[SaleLineTable.id] = line.id
                    it[SaleLineTable.tenantId] = tenantId
                    it[SaleLineTable.saleId] = id
                    it[SaleLineTable.productId] = line.productId
                    it[SaleLineTable.soldBy] = line.soldBy
                    it[SaleLineTable.weightGrams] = line.weightGrams
                    it[SaleLineTable.quantity] = line.quantity
                    it[SaleLineTable.unitPriceMinor] = line.unitPriceMinor
                    it[SaleLineTable.lineTotalMinor] = line.lineTotalMinor
                }
            }
            bumpAlmacenProductSeq(lines.map { it.productId })
            IngestResult.Applied(findByIdInternal(id)!!)
        } else {
            IngestResult.Duplicate(findByIdInternal(id)!!)
        }
    }

    override suspend fun createAllWithLines(sales: List<SaleInput>): Int = suspendTransaction {
        if (sales.isEmpty()) return@suspendTransaction 0

        // One round trip to find what is already there, instead of relying on per-row insertIgnore
        // and then having to ask the database which rows actually landed.
        val existing = SaleTable
            .select(SaleTable.id)
            .where { SaleTable.id inList sales.map { it.id } }
            .map { it[SaleTable.id] }
            .toSet()

        val fresh = sales.filter { it.id !in existing }
        if (fresh.isEmpty()) return@suspendTransaction 0

        SaleTable.batchInsert(fresh, shouldReturnGeneratedValues = false) { sale ->
            this[SaleTable.id] = sale.id
            this[SaleTable.tenantId] = sale.tenantId
            this[SaleTable.branchId] = sale.branchId
            this[SaleTable.cashierId] = sale.cashierId
            this[SaleTable.soldAt] = sale.soldAt
            this[SaleTable.paymentMethod] = sale.paymentMethod
            this[SaleTable.totalMinor] = sale.totalMinor
            this[SaleTable.deviceId] = sale.deviceId
            this[SaleTable.serverSeq] = nextServerSeq()
            // Seeded history was "received" when it was sold; otherwise it would all postdate any opening count.
            this[SaleTable.receivedAt] = sale.soldAt
        }

        val lines = fresh.flatMap { sale -> sale.lines.map { sale to it } }
        SaleLineTable.batchInsert(lines, shouldReturnGeneratedValues = false) { (sale, line) ->
            this[SaleLineTable.id] = line.id
            this[SaleLineTable.tenantId] = sale.tenantId
            this[SaleLineTable.saleId] = sale.id
            this[SaleLineTable.productId] = line.productId
            this[SaleLineTable.soldBy] = line.soldBy
            this[SaleLineTable.weightGrams] = line.weightGrams
            this[SaleLineTable.quantity] = line.quantity
            this[SaleLineTable.unitPriceMinor] = line.unitPriceMinor
            this[SaleLineTable.lineTotalMinor] = line.lineTotalMinor
        }

        fresh.size
    }

    override suspend fun findById(id: Uuid): Sale? = suspendTransaction {
        findByIdInternal(id)
    }

    override suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<Sale> = suspendTransaction {
        SaleTable.selectAll()
            .where { (SaleTable.tenantId eq tenantId) and (SaleTable.serverSeq greater since) }
            .orderBy(SaleTable.serverSeq, SortOrder.ASC)
            .limit(limit)
            .map { it.toSale(linesFor(it[SaleTable.id])) }
    }

    private fun findByIdInternal(id: Uuid): Sale? {
        val row = SaleTable.selectAll().where { SaleTable.id eq id }.singleOrNull() ?: return null
        return row.toSale(linesFor(id))
    }

    private fun linesFor(saleId: Uuid): List<SaleLine> =
        SaleLineTable.selectAll()
            .where { SaleLineTable.saleId eq saleId }
            .map { it.toSaleLine() }

    private fun ResultRow.toSale(lines: List<SaleLine>) = Sale(
        id = this[SaleTable.id],
        tenantId = this[SaleTable.tenantId],
        branchId = this[SaleTable.branchId],
        cashierId = this[SaleTable.cashierId],
        soldAt = this[SaleTable.soldAt],
        paymentMethod = this[SaleTable.paymentMethod],
        totalMinor = this[SaleTable.totalMinor],
        deviceId = this[SaleTable.deviceId],
        serverSeq = this[SaleTable.serverSeq],
        receivedAt = this[SaleTable.receivedAt],
        lines = lines
    )

    private fun ResultRow.toSaleLine() = SaleLine(
        id = this[SaleLineTable.id],
        saleId = this[SaleLineTable.saleId],
        productId = this[SaleLineTable.productId],
        soldBy = this[SaleLineTable.soldBy],
        weightGrams = this[SaleLineTable.weightGrams],
        quantity = this[SaleLineTable.quantity],
        unitPriceMinor = this[SaleLineTable.unitPriceMinor],
        lineTotalMinor = this[SaleLineTable.lineTotalMinor]
    )
}
