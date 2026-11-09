package com.f776.ecopan.feature.production.unittest.fake

import com.f776.ecopan.domain.inventory.Ingredient
import com.f776.ecopan.domain.inventory.IngredientRepository
import com.f776.ecopan.domain.inventory.IngredientUpsert
import kotlinx.coroutines.flow.MutableStateFlow

class FakeIngredientRepository : IngredientRepository {
    val ingredients = MutableStateFlow<List<Ingredient>>(emptyList())

    override fun observeActiveIngredients() = ingredients

    override suspend fun upsertIngredients(ingredients: List<IngredientUpsert>) =
        error("not needed by StockGlanceViewModelTest")
}
