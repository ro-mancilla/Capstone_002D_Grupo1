package com.f776.ecopan.domain.catalog

import com.f776.ecopan.core.domain.catalog.ProductOrigin
import com.f776.ecopan.core.domain.catalog.SoldBy
import kotlin.uuid.Uuid

/** Write-side counterpart to [Product]: carries the wire `serverSeq` in for local bookkeeping. */
data class ProductUpsert(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val productType: String,
    val soldBy: SoldBy,
    val pricePerKgMinor: Long?,
    val priceMinor: Long?,
    val barcode: String?,
    val imageUrl: String?,
    val active: Boolean,
    val serverSeq: Long,
    val mixToLoafRatioMilli: Int?,
    val minMixGrams: Long?,
    val maxMixGrams: Long?,
    val origin: ProductOrigin = ProductOrigin.PANADERIA,
    val lowStockThresholdUnits: Long? = null,
    /** The pulled branch stock, before any local unsynced-sale adjustment. */
    val stockUnits: Long? = null,
)
