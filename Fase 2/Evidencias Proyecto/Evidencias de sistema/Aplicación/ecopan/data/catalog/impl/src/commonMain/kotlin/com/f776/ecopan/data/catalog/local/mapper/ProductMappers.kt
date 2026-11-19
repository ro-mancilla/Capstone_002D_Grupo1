package com.f776.ecopan.data.catalog.local.mapper

import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.catalog.ProductUpsert
import com.f776.unigrid.core.database.entity.ProductEntity
import kotlin.time.Clock

internal fun ProductEntity.toDomain(unsyncedSoldUnits: Long = 0) = Product(
    id = id,
    tenantId = tenantId,
    name = name,
    productType = productType,
    soldBy = soldBy,
    pricePerKgMinor = pricePerKgMinor,
    priceMinor = priceMinor,
    barcode = barcode,
    imageUrl = imageUrl,
    active = active,
    mixToLoafRatioMilli = mixToLoafRatioMilli,
    minMixGrams = minMixGrams,
    maxMixGrams = maxMixGrams,
    origin = origin,
    lowStockThresholdUnits = lowStockThresholdUnits,
    stockUnits = stockUnits?.let { it - unsyncedSoldUnits },
)

internal fun ProductUpsert.toEntity() = ProductEntity(
    id = id,
    tenantId = tenantId,
    name = name,
    productType = productType,
    soldBy = soldBy,
    pricePerKgMinor = pricePerKgMinor,
    priceMinor = priceMinor,
    barcode = barcode,
    imageUrl = imageUrl,
    active = active,
    serverSeq = serverSeq,
    cachedAt = Clock.System.now(),
    mixToLoafRatioMilli = mixToLoafRatioMilli,
    minMixGrams = minMixGrams,
    maxMixGrams = maxMixGrams,
    origin = origin,
    lowStockThresholdUnits = lowStockThresholdUnits,
    stockUnits = stockUnits,
)
