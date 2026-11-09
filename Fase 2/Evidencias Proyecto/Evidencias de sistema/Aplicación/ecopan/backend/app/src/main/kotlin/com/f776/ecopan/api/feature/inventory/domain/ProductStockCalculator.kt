package com.f776.ecopan.api.feature.inventory.domain

import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.repository.ProductStockRepository
import com.f776.ecopan.data.database.repository.model.Product
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** One Almacén product's derived stock in one branch. */
internal data class ProductStockFigures(
    val receivedUnits: Long,
    val soldUnits: Long,
    val trackedSince: Instant
) {
    val currentUnits: Long get() = receivedUnits - soldUnits
}

/**
 * The single definition of Almacén stock: opening count + ledger movements - units sold since
 * tracking began. The web stock list and the till's pull both go through here, so they cannot
 * disagree. Two reads however many products: one ledger sum and one grouped sales query.
 *
 * Semantics, all on the **server clock**: tracking begins at the server receipt time of the
 * branch's earliest opening count (its `received_at`, i.e. when it was entered, however far back it
 * was dated), else at the product's creation. A sale counts iff the server received it at or after
 * that instant, whatever its `soldAt`. So a sale rung up offline before the count but synced after
 * it IS subtracted (the shelf count did not include it); a sale received before the count is NOT
 * (the count already reflects it). A backdated count therefore never double-subtracts sales that
 * were received before it was entered.
 */
internal class ProductStockCalculator(private val productStockRepository: ProductStockRepository) {

    /** Figures for every Almacén product in [products]; other origins are skipped. */
    suspend fun figures(tenantId: Uuid, branchId: Uuid, products: Collection<Product>): Map<Uuid, ProductStockFigures> {
        val almacen = products.filter { it.origin == ProductOrigin.ALMACEN }
        if (almacen.isEmpty()) return emptyMap()
        val sums = productStockRepository.sumByProduct(tenantId, branchId).associateBy { it.productId }
        val trackedSince = almacen.associate { it.id to (sums[it.id]?.openingReceivedAt ?: it.createdAt) }
        val sold = productStockRepository.soldUnitsSince(tenantId, branchId, trackedSince)
        return almacen.associate {
            it.id to ProductStockFigures(
                receivedUnits = sums[it.id]?.receivedUnits ?: 0L,
                soldUnits = sold[it.id] ?: 0L,
                trackedSince = trackedSince.getValue(it.id)
            )
        }
    }
}
