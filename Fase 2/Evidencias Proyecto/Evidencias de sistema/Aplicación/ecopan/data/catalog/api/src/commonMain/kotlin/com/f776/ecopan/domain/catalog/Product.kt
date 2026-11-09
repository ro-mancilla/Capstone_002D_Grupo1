package com.f776.ecopan.domain.catalog

import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.ecopan.core.domain.catalog.SoldBy
import kotlin.uuid.Uuid

/**
 * Exactly one of [pricePerKgMinor]/[priceMinor] is set, matching [soldBy] (02 §1 `products`).
 * [mixToLoafRatioMilli]/[minMixGrams]/[maxMixGrams] are baker-mobile formula config (06 §1, §9);
 * null means "no estimate" / "use the tenant-default soft bounds", not zero.
 */
data class Product(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val productType: String,
    val soldBy: SoldBy,
    val pricePerKgMinor: Long?,
    val priceMinor: Long?,
    val barcode: String?,
    /** Absolute URL of a product photo, or null. A picture is not ledger data: a tile
     * without its photo is still a tile, so this never blocks an offline sale. */
    val imageUrl: String?,
    val active: Boolean,
    val mixToLoafRatioMilli: Int?,
    val minMixGrams: Long?,
    val maxMixGrams: Long?,
    val origin: ProductOrigin = ProductOrigin.PANADERIA,
    /** Almacén reorder level in units; null when unset (and for Panadería). */
    val lowStockThresholdUnits: Long? = null,
    /**
     * Almacén stock *as this device believes it*: the branch figure from the last pull minus units
     * sold here that have not synced yet. Null when the server never reported one -- show nothing
     * rather than guess. Negative is legal and never blocks a sale.
     */
    val stockUnits: Long? = null,
)
