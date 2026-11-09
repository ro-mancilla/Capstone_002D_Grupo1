package com.f776.ecopan.data.inventory.unittest.fake

import com.f776.unigrid.core.database.dao.IngredientDao
import com.f776.unigrid.core.database.entity.IngredientEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class FakeIngredientDao : IngredientDao {
    private val state = MutableStateFlow<Map<Uuid, IngredientEntity>>(emptyMap())

    override suspend fun upsertAll(ingredients: List<IngredientEntity>) {
        state.value = state.value + ingredients.associateBy { it.id }
    }

    override fun observeActiveByTenant(tenantId: Uuid) =
        state.map { byId ->
            byId.values.filter { it.tenantId == tenantId && it.active }.sortedBy { it.name }
        }

    override suspend fun clear() {
        state.value = emptyMap()
    }
}
