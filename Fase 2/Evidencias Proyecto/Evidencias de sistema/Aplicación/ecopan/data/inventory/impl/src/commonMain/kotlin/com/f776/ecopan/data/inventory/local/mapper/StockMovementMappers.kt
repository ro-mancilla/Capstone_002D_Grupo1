package com.f776.ecopan.data.inventory.local.mapper

import com.f776.ecopan.domain.inventory.MovementKind
import com.f776.ecopan.domain.inventory.StockMovement
import com.f776.unigrid.core.database.entity.StockMovementEntity

internal fun StockMovementEntity.toDomain() = StockMovement(
    id = id,
    tenantId = tenantId,
    branchId = branchId,
    ingredientId = ingredientId,
    deltaGrams = deltaGrams,
    kind = MovementKind.valueOf(kind),
    sourceBatchId = sourceBatchId,
    note = note,
    recordedAt = recordedAt,
    recordedBy = recordedBy,
)

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
