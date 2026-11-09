package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.timestamp

internal const val TENANT_FORECAST_PRODUCT_TYPE_FK = "fk_forecasts_tenant_product_type"

/**
 * Server-side ML cache (server-minted id); not synced to offline clients. One row per
 * (branch, Panadería product, day): the series is the product (#51), and the product's type is
 * kept only for grouping and filtering.
 */
object ForecastTable : Table("forecasts") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id)
    val productId = uuid("product_id").references(ProductTable.id)
    // Canonical name snapshot + the reference of record, copied from the product when the row is
    // written. The reference is a composite FK so it cannot name another tenant's type (#42).
    val productType = varchar("product_type", 48)
    val productTypeId = uuid("product_type_id")
    val forecastDate = date("forecast_date")
    val predictedGrams = long("predicted_grams").nullable()
    val predictedUnits = integer("predicted_units").nullable()
    val modelVersion = varchar("model_version", 64)
    val generatedAt = timestamp("generated_at")
    // Re-stamped on every upsert so devices re-pull the refreshed row (sync cursor, as on other synced tables).
    val serverSeq = long("server_seq")

    override val primaryKey = PrimaryKey(id)

    init {
        foreignKey(
            tenantId to ProductTypeTable.tenantId,
            productTypeId to ProductTypeTable.id,
            name = TENANT_FORECAST_PRODUCT_TYPE_FK
        )
        uniqueIndex(
            "uq_forecasts_tenant_branch_product_date",
            tenantId, branchId, productId, forecastDate
        )
        index("idx_forecasts_tenant_branch_date", false, tenantId, branchId, forecastDate)
        index("idx_forecasts_tenant_seq", false, tenantId, serverSeq)
    }
}
