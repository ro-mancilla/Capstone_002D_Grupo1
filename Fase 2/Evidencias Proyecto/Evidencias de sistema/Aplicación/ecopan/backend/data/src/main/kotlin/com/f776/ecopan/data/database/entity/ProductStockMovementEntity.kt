package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * Append-only unit ledger for *resold* products -- drinks, packaged groceries, anything the
 * bakery buys finished and sells by the unit. The ingredient ledger ([StockMovementTable]) is
 * grams of raw material consumed by formulas; this one is whole units received and counted, and
 * the two are deliberately separate tables (and separate dashboard pages) because they answer
 * different questions to different people.
 *
 * The kinds are `OPENING_COUNT`, `PURCHASE` and `MANUAL_ADJUSTMENT`: the *sale* side is never
 * written as a movement, it is read straight from `sale_lines.quantity` at query time, so a
 * product's current stock = Σ delta_units (opening count included) − units sold since the
 * branch's opening count (or the product's creation when the branch has none). No `server_seq`:
 * the web dashboard is the only reader and nothing syncs this to a device.
 */
object ProductStockMovementTable : Table("product_stock_movements") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id)
    val productId = uuid("product_id").references(ProductTable.id)
    val deltaUnits = long("delta_units")
    val kind = enumerationByName<ProductStockMovementKind>("kind", 20)
    val note = text("note").nullable()
    val createdBy = uuid("created_by").nullable()
    val occurredAt = timestamp("occurred_at")
    val receivedAt = timestamp("received_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_product_stock_movements_tenant_branch_product", false, tenantId, branchId, productId)
    }
}

/**
 * `OPENING_COUNT` is the starting count of an Almacén product in one branch. It is written when
 * the product is created (or by the baseline boot step for products that predate it), never through
 * the manual-movement endpoint, and its `occurred_at` is where sale counting starts for that branch.
 */
enum class ProductStockMovementKind { OPENING_COUNT, PURCHASE, MANUAL_ADJUSTMENT }
