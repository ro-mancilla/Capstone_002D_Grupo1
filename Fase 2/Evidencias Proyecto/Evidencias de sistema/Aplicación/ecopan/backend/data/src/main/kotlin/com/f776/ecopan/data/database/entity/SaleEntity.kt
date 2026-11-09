package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

internal const val RECEIVED_AT_INDEX = "idx_sales_tenant_branch_receivedat"

/** Append-only fact (01 §6.1): no UPDATE/DELETE endpoints. `id` is client-minted. */
object SaleTable : Table("sales") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id)
    val cashierId = uuid("cashier_id").references(UserTable.id)
    val soldAt = timestamp("sold_at")
    val paymentMethod = enumerationByName<PaymentMethod>("payment_method", 16)
    val totalMinor = long("total_minor")
    val deviceId = varchar("device_id", 64)
    val serverSeq = long("server_seq")
    // When the server applied the sale (not when it was rung up: that is `soldAt`). Almacén stock
    // counts by this, so an offline sale synced after an opening count is still subtracted.
    val receivedAt = timestamp("received_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_sales_tenant_seq", false, tenantId, serverSeq)
        index("idx_sales_tenant_branch_soldat", false, tenantId, branchId, soldAt)
        // Almacén stock sums sales by receipt time per branch (`soldUnitsSince`).
        index(RECEIVED_AT_INDEX, false, tenantId, branchId, receivedAt)
    }
}

/** Append-only, ingested atomically with its parent [SaleTable] row. */
object SaleLineTable : Table("sale_lines") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val saleId = uuid("sale_id").references(SaleTable.id)
    val productId = uuid("product_id").references(ProductTable.id)
    val soldBy = enumerationByName<SoldBy>("sold_by", 8)
    val weightGrams = long("weight_grams").nullable()
    val quantity = integer("quantity").nullable()
    val unitPriceMinor = long("unit_price_minor")
    val lineTotalMinor = long("line_total_minor")

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_sale_lines_tenant_sale", false, tenantId, saleId)
        index("idx_sale_lines_tenant_product", false, tenantId, productId)
    }
}

enum class PaymentMethod { CASH, CARD, TRANSFER, OTHER }
