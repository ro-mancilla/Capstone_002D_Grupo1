package com.f776.ecopan.domain.production

import kotlin.uuid.Uuid

/** Preview + result display row — always positive grams (06 §2, §3). */
data class DerivedIngredient(
    val ingredientId: Uuid,
    val ingredientName: String,
    val grams: Long,
)
