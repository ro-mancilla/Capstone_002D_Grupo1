package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.ProductStockMovement
import com.f776.ecopan.data.database.repository.model.ProductStockSum
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Unit ledger for resold products (drinks, packaged goods). Same append-only discipline as
 * [StockMovementRepository] -- **no update/delete member exists** -- but counted in whole units
 * and with the sale side derived from `sale_lines` rather than written as movements.
 */
interface ProductStockRepository {
    /** Idempotent by [id] (`INSERT ... ON CONFLICT DO NOTHING`). */
    suspend fun append(
        id: Uuid,
        tenantId: Uuid,
        branchId: Uuid,
        productId: Uuid,
        deltaUnits: Long,
        kind: ProductStockMovementKind,
        note: String?,
        createdBy: Uuid?,
        occurredAt: Instant
    ): IngestResult<ProductStockMovement>

    /**
     * `SUM(delta_units)` and the server receipt time of the earliest `OPENING_COUNT`, per product,
     * for one branch.
     */
    suspend fun sumByProduct(tenantId: Uuid, branchId: Uuid): List<ProductStockSum>

    /**
     * `SUM(sale_lines.quantity)` per product in [branchId], each over sales with
     * `received_at >=` (server receipt time, not `sold_at`) that product's instant in
     * [sinceByProduct] (its opening count's receipt time, or its creation, so the number is "sold
     * since tracking began"). One grouped query for the whole map; a product with no sales is
     * absent from the result.
     */
    suspend fun soldUnitsSince(tenantId: Uuid, branchId: Uuid, sinceByProduct: Map<Uuid, Instant>): Map<Uuid, Long>

    suspend fun listMovements(
        tenantId: Uuid,
        branchId: Uuid? = null,
        productId: Uuid? = null,
        limit: Int = 100,
        offset: Int = 0
    ): List<ProductStockMovement>
}
