package com.f776.ecopan.data.production.local.mapper

import com.f776.ecopan.domain.production.BatchSyncState
import com.f776.ecopan.domain.production.ProductionBatch
import com.f776.unigrid.core.database.entity.ProductionBatchEntity

internal fun ProductionBatch.toEntity() = ProductionBatchEntity(
    id = id,
    tenantId = tenantId,
    branchId = branchId,
    bakerId = bakerId,
    productId = productId,
    productTypeName = productTypeName,
    mixWeightGrams = mixWeightGrams,
    estimatedUnits = estimatedUnits,
    loggedAt = loggedAt,
)

internal fun ProductionBatchEntity.toDomain(syncState: BatchSyncState) = ProductionBatch(
    id = id,
    tenantId = tenantId,
    branchId = branchId,
    bakerId = bakerId,
    productId = productId,
    productTypeName = productTypeName,
    mixWeightGrams = mixWeightGrams,
    estimatedUnits = estimatedUnits,
    loggedAt = loggedAt,
    syncState = syncState,
)
