package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/** Append-only fact; `id` client-minted. `decrementStatus` is written by the worker (02 §6). */
object ProductionBatchTable : Table("production_batches") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id)
    val bakerId = uuid("baker_id").references(UserTable.id)
    val productId = uuid("product_id").references(ProductTable.id)
    val mixWeightGrams = long("mix_weight_grams")
    val batchAt = timestamp("batch_at")
    val movementsMinted = bool("movements_minted")
    val decrementStatus = enumerationByName<DecrementStatus>("decrement_status", 12)
    val deviceId = varchar("device_id", 64)
    val serverSeq = long("server_seq")
    val receivedAt = timestamp("received_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_production_batches_tenant_seq", false, tenantId, serverSeq)
        index("idx_production_batches_tenant_branch_at", false, tenantId, branchId, batchAt)
    }
}

enum class DecrementStatus { PENDING, APPLIED, SKIPPED, FAILED }
