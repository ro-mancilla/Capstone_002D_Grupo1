package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.model.Formula
import com.f776.ecopan.data.database.repository.model.FormulaLine
import com.f776.ecopan.data.database.repository.model.Product
import kotlin.uuid.Uuid

/**
 * An Almacén product's first count, written in the same transaction as the product row so a failure
 * leaves neither behind. [movementId] makes a retried create idempotent: a repeat finds the same
 * ledger row instead of counting the shelf twice.
 */
data class OpeningCount(
    val movementId: Uuid,
    val branchId: Uuid,
    val units: Long,
    val createdBy: Uuid?,
    val occurredAt: kotlin.time.Instant
)

/** Products + formulas (02 §1 groups these under one repo). */
interface CatalogRepository {
    suspend fun createProduct(
        id: Uuid,
        tenantId: Uuid,
        name: String,
        productType: String,
        soldBy: SoldBy,
        pricePerKgMinor: Long?,
        priceMinor: Long?,
        barcode: String?,
        imageUrl: String? = null,
        origin: ProductOrigin = ProductOrigin.PANADERIA,
        opening: OpeningCount? = null
    ): Product

    suspend fun findProductById(id: Uuid): Product?
    suspend fun listProducts(tenantId: Uuid, includeInactive: Boolean = false): List<Product>

    suspend fun updateProduct(
        id: Uuid,
        name: String? = null,
        productType: String? = null,
        soldBy: SoldBy? = null,
        pricePerKgMinor: Long? = null,
        priceMinor: Long? = null,
        barcode: String? = null,
        imageUrl: String? = null,
        active: Boolean? = null,
        origin: ProductOrigin? = null
    ): Product?

    /** Sets or clears (`null`) the photo URL; separate from [updateProduct], whose `null` means "leave as is". */
    suspend fun setProductImageUrl(id: Uuid, imageUrl: String?): Product?

    /** Sets or clears (`null`) a unit product's reorder level; bumps `serverSeq` like any product write. */
    suspend fun setProductLowStockThreshold(id: Uuid, thresholdUnits: Long?): Product?

    suspend fun findFormulaByProduct(tenantId: Uuid, productId: Uuid): Formula?

    /** Tenant-wide listing for C5 (02 §3), lines embedded. */
    suspend fun listFormulas(tenantId: Uuid, includeInactive: Boolean = false): List<Formula>

    /** Replace-all: deletes existing lines and inserts [lines] in one transaction (02 §1). */
    suspend fun putFormula(id: Uuid, tenantId: Uuid, productId: Uuid, lines: List<FormulaLine>): Formula
}
