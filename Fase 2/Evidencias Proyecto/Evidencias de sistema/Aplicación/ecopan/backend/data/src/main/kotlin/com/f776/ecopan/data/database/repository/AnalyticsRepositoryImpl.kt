package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductTable
import com.f776.ecopan.data.database.entity.ProductionBatchTable
import com.f776.ecopan.data.database.entity.SaleLineTable
import com.f776.ecopan.data.database.entity.SaleTable
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.model.ProductionFact
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AnalyticsRepositoryImpl : AnalyticsRepository {

    override suspend fun saleLineFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<SaleLineFact> =
        suspendTransaction {
            val join = SaleTable
                .join(SaleLineTable, JoinType.INNER) { SaleTable.id eq SaleLineTable.saleId }
                .join(ProductTable, JoinType.INNER) { SaleLineTable.productId eq ProductTable.id }

            join.select(
                SaleTable.id, SaleTable.branchId, SaleTable.soldAt, SaleTable.paymentMethod,
                ProductTable.id, ProductTable.name, ProductTable.productType, ProductTable.origin,
                SaleLineTable.soldBy, SaleLineTable.weightGrams,
                SaleLineTable.quantity,
                SaleLineTable.lineTotalMinor
            ).where {
                var condition = (SaleTable.tenantId eq tenantId) and
                    (SaleTable.soldAt greaterEq from) and (SaleTable.soldAt lessEq to)
                branchId?.let { condition = condition and (SaleTable.branchId eq it) }
                condition
            }.map { row ->
                SaleLineFact(
                    saleId = row[SaleTable.id],
                    branchId = row[SaleTable.branchId],
                    soldAt = row[SaleTable.soldAt],
                    paymentMethod = row[SaleTable.paymentMethod],
                    productId = row[ProductTable.id],
                    productName = row[ProductTable.name],
                    productType = row[ProductTable.productType],
                    origin = row[ProductTable.origin],
                    soldBy = row[SaleLineTable.soldBy],
                    weightGrams = row[SaleLineTable.weightGrams],
                    quantity = row[SaleLineTable.quantity],
                    lineTotalMinor = row[SaleLineTable.lineTotalMinor]
                )
            }
        }

    override suspend fun productionFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductionFact> =
        suspendTransaction {
            val join = ProductionBatchTable
                .join(ProductTable, JoinType.INNER) { ProductionBatchTable.productId eq ProductTable.id }

            join.select(
                ProductionBatchTable.branchId, ProductionBatchTable.productId, ProductTable.name,
                ProductionBatchTable.batchAt, ProductTable.productType, ProductionBatchTable.mixWeightGrams
            ).where {
                var condition = (ProductionBatchTable.tenantId eq tenantId) and
                    (ProductionBatchTable.batchAt greaterEq from) and (ProductionBatchTable.batchAt lessEq to)
                branchId?.let { condition = condition and (ProductionBatchTable.branchId eq it) }
                condition
            }.map { row ->
                ProductionFact(
                    branchId = row[ProductionBatchTable.branchId],
                    productId = row[ProductionBatchTable.productId],
                    productName = row[ProductTable.name],
                    batchAt = row[ProductionBatchTable.batchAt],
                    productType = row[ProductTable.productType],
                    mixWeightGrams = row[ProductionBatchTable.mixWeightGrams]
                )
            }
        }
}
