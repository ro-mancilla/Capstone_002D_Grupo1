package com.f776.ecopan.data.inventory.local.mapper

import com.f776.ecopan.domain.inventory.Ingredient
import com.f776.ecopan.domain.inventory.IngredientUpsert
import com.f776.unigrid.core.database.entity.IngredientEntity
import kotlin.time.Clock

internal fun IngredientEntity.toDomain() = Ingredient(
    id = id,
    tenantId = tenantId,
    name = name,
    lowStockThresholdGrams = lowStockThresholdGrams,
    active = active,
)

internal fun IngredientUpsert.toEntity() = IngredientEntity(
    id = id,
    tenantId = tenantId,
    name = name,
    lowStockThresholdGrams = lowStockThresholdGrams,
    active = active,
    serverSeq = serverSeq,
    cachedAt = Clock.System.now(),
)
