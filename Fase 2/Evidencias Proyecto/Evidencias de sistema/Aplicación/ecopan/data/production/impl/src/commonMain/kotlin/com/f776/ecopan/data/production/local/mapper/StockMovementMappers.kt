package com.f776.ecopan.data.production.local.mapper

import com.f776.ecopan.domain.inventory.StockMovement
import com.f776.unigrid.core.database.entity.StockMovementEntity

/**
 * Duplicated from `:data:inventory:impl`'s own mapper (which is `internal` there and so invisible
 * here, by design — module encapsulation) rather than making that mapper public just for this one
 * caller.
 */
internal fun StockMovement.toEntity() = StockMovementEntity(
    id = id,
    tenantId = tenantId,
    branchId = branchId,
    ingredientId = ingredientId,
    deltaGrams = deltaGrams,
    kind = kind.name,
    sourceBatchId = sourceBatchId,
    note = note,
    recordedAt = recordedAt,
    recordedBy = recordedBy,
)
