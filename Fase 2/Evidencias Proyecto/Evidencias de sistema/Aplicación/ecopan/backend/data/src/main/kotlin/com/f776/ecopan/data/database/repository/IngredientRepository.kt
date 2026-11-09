package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.repository.model.Ingredient
import kotlin.uuid.Uuid

interface IngredientRepository {
    suspend fun create(id: Uuid, tenantId: Uuid, name: String, lowStockThresholdGrams: Long?): Ingredient
    suspend fun findById(id: Uuid): Ingredient?
    suspend fun listByTenant(tenantId: Uuid, includeInactive: Boolean = false): List<Ingredient>
    suspend fun update(
        id: Uuid,
        name: String? = null,
        lowStockThresholdGrams: Long? = null,
        active: Boolean? = null
    ): Ingredient?
}
