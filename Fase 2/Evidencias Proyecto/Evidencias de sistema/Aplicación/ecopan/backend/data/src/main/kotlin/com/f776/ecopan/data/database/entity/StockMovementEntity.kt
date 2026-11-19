package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * Append-only delta ledger (01 §6.1) -- current stock = Σ deltas, never a stored mutable qty.
 * `sourceBatchId` + [uq_stock_movements_source_batch_ingredient] is the worker's idempotency key
 * for `FORMULA_DECREMENT` rows; also doubles as the sync-push idempotency guard.
 */
object StockMovementTable : Table("stock_movements") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id)
    val ingredientId = uuid("ingredient_id").references(IngredientTable.id)
    val deltaGrams = long("delta_grams")
    val kind = enumerationByName<StockMovementKind>("kind", 20)
    val sourceBatchId = uuid("source_batch_id").references(ProductionBatchTable.id).nullable()
    val note = text("note").nullable()
    val createdBy = uuid("created_by").nullable()
    val occurredAt = timestamp("occurred_at")
    val serverSeq = long("server_seq")
    val receivedAt = timestamp("received_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_stock_movements_tenant_ingredient", false, tenantId, ingredientId)
        index("idx_stock_movements_tenant_seq", false, tenantId, serverSeq)
        // The reconciliation report scans one branch's ledger up to a date (opening balance).
        index("idx_stock_movements_tenant_branch_occurred", false, tenantId, branchId, occurredAt)
        uniqueIndex("uq_stock_movements_source_batch_ingredient", sourceBatchId, ingredientId)
    }
}

enum class StockMovementKind { PURCHASE, FORMULA_DECREMENT, MANUAL_ADJUSTMENT }
