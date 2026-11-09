package com.f776.ecopan.domain.inventory

import kotlinx.coroutines.flow.Flow

interface IngredientRepository {
    fun observeActiveIngredients(): Flow<List<Ingredient>>
    suspend fun upsertIngredients(ingredients: List<IngredientUpsert>)
}
