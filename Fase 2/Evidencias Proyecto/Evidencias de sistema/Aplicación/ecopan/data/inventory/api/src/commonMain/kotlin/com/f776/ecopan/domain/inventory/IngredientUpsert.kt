package com.f776.ecopan.domain.inventory

import kotlin.uuid.Uuid

data class IngredientUpsert(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val lowStockThresholdGrams: Long?,
    val active: Boolean,
    val serverSeq: Long,
)
