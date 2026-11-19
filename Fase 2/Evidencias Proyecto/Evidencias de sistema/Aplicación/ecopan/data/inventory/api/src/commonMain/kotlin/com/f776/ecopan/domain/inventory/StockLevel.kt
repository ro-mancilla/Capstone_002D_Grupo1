package com.f776.ecopan.domain.inventory

import kotlin.uuid.Uuid

/** Derived — current stock = Σ signed deltas for the ingredient; may be negative (01 §6.4). */
data class StockLevel(
    val ingredientId: Uuid,
    val name: String,
    val currentGrams: Long,
)
