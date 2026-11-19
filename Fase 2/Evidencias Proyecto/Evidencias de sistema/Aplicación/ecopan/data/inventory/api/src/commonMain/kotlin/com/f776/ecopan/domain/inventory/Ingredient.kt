package com.f776.ecopan.domain.inventory

import kotlin.uuid.Uuid

/** Raw material; unit is always grams (01 §5). */
data class Ingredient(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val lowStockThresholdGrams: Long?,
    val active: Boolean,
)
