package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.ForecastTable
import com.f776.ecopan.data.database.entity.ProductTable
import com.f776.ecopan.data.database.nextServerSeq
import com.f776.ecopan.data.database.repository.model.Forecast
import com.f776.ecopan.data.domain.ProductTypeKey
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class ForecastRepositoryImpl : ForecastRepository {

    override suspend fun upsertAll(
        tenantId: Uuid,
        branchId: Uuid,
        rows: List<ForecastUpsert>,
        notBefore: LocalDate
    ): ForecastUpsertResult {
        if (rows.isEmpty()) return ForecastUpsertResult(0, 0, 0)
        val (current, past) = rows.partition { it.forecastDate >= notBefore }
        if (current.isEmpty()) return ForecastUpsertResult(0, past.size, 0)

        return suspendTransaction {
            // Serialise forecast writers *before* taking sequence numbers, so they commit in
            // sequence order (see the interface KDoc). Held to the end of this transaction.
            exec("SELECT pg_advisory_xact_lock(hashtext('forecasts_server_seq'))")

            val products = ProductTable.select(ProductTable.id, ProductTable.productType, ProductTable.productTypeId)
                .where { (ProductTable.tenantId eq tenantId) and (ProductTable.id inList current.map { it.productId }.distinct()) }
                .associateBy { it[ProductTable.id] }
            val now = Clock.System.now()
            var written = 0
            var unknown = 0
            for (row in current) {
                val product = products[row.productId]
                if (product == null) {
                    unknown++
                    continue
                }
                val seq = nextServerSeq()
                ForecastTable.upsert(
                    ForecastTable.tenantId, ForecastTable.branchId, ForecastTable.productId, ForecastTable.forecastDate,
                    // On conflict, keep the existing row's id -- only the prediction/model fields are
                    // refreshed. Without this, the ON CONFLICT UPDATE would overwrite `id` with the
                    // fresh one too, breaking the "upsert = update in place" contract.
                    onUpdateExclude = listOf(
                        ForecastTable.id, ForecastTable.tenantId, ForecastTable.branchId,
                        ForecastTable.productId, ForecastTable.forecastDate
                    )
                ) {
                    it[ForecastTable.serverSeq] = seq
                    it[ForecastTable.id] = Uuid.random()
                    it[ForecastTable.tenantId] = tenantId
                    it[ForecastTable.branchId] = branchId
                    it[ForecastTable.productId] = row.productId
                    it[ForecastTable.productType] = product[ProductTable.productType]
                    it[ForecastTable.productTypeId] = product[ProductTable.productTypeId]
                    it[ForecastTable.forecastDate] = row.forecastDate
                    it[ForecastTable.predictedGrams] = row.predictedGrams
                    it[ForecastTable.predictedUnits] = row.predictedUnits
                    it[ForecastTable.modelVersion] = row.modelVersion
                    it[ForecastTable.generatedAt] = now
                }
                written++
            }
            ForecastUpsertResult(written, past.size, unknown)
        }
    }

    override suspend fun listByBranch(
        tenantId: Uuid,
        branchId: Uuid,
        from: LocalDate,
        to: LocalDate,
        productType: String?,
        productId: Uuid?
    ): List<Forecast> = suspendTransaction {
        withProduct()
            .where {
                var condition = (ForecastTable.tenantId eq tenantId) and (ForecastTable.branchId eq branchId) and
                    (ForecastTable.forecastDate greaterEq from) and (ForecastTable.forecastDate lessEq to)
                productId?.let { condition = condition and (ForecastTable.productId eq it) }
                condition
            }
            .map { it.toForecast() }
            // The product's *current* type (what actuals use), by normalised key: "Pan" = "PAN".
            .filter { productType == null || ProductTypeKey.of(it.productType) == ProductTypeKey.of(productType) }
    }

    private fun withProduct() = ForecastTable
        .join(ProductTable, JoinType.INNER) { ForecastTable.productId eq ProductTable.id }
        .selectAll()

    private fun ResultRow.toForecast() = Forecast(
        id = this[ForecastTable.id],
        tenantId = this[ForecastTable.tenantId],
        branchId = this[ForecastTable.branchId],
        productId = this[ForecastTable.productId],
        productName = this[ProductTable.name],
        // Current, not the snapshot stored on the row: a product re-typed after its forecast was
        // made must group with its actuals under the new type.
        productType = this[ProductTable.productType],
        forecastDate = this[ForecastTable.forecastDate],
        predictedGrams = this[ForecastTable.predictedGrams],
        predictedUnits = this[ForecastTable.predictedUnits],
        modelVersion = this[ForecastTable.modelVersion],
        generatedAt = this[ForecastTable.generatedAt],
        serverSeq = this[ForecastTable.serverSeq]
    )
}
