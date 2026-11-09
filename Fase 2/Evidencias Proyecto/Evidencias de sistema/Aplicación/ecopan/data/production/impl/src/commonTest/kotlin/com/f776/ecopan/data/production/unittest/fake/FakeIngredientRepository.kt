package com.f776.ecopan.data.production.unittest.fake

import com.f776.ecopan.domain.inventory.Ingredient
import com.f776.ecopan.domain.inventory.IngredientRepository
import com.f776.ecopan.domain.inventory.IngredientUpsert
import kotlinx.coroutines.flow.MutableStateFlow

internal class FakeIngredientRepository : IngredientRepository {
    private val state = MutableStateFlow<List<Ingredient>>(emptyList())

    fun seed(ingredient: Ingredient) {
        state.value = state.value + ingredient
    }

    override fun observeActiveIngredients() = state
    override suspend fun upsertIngredients(ingredients: List<IngredientUpsert>) = throw NotImplementedError("unused by :data:production")
}
