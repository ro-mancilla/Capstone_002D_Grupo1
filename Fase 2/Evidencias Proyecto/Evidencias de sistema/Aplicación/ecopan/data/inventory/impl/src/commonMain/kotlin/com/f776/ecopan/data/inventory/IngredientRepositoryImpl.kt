@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.inventory

import com.f776.ecopan.data.inventory.local.mapper.toDomain
import com.f776.ecopan.data.inventory.local.mapper.toEntity
import com.f776.ecopan.domain.inventory.Ingredient
import com.f776.ecopan.domain.inventory.IngredientRepository
import com.f776.ecopan.domain.inventory.IngredientUpsert
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.unigrid.core.database.dao.IngredientDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

internal class IngredientRepositoryImpl(
    private val ingredientDao: IngredientDao,
    private val tenantRepository: TenantRepository,
) : IngredientRepository {

    override fun observeActiveIngredients(): Flow<List<Ingredient>> =
        tenantRepository.observeTenant().flatMapLatest { tenant ->
            if (tenant == null) {
                flowOf(emptyList())
            } else {
                ingredientDao.observeActiveByTenant(tenant.id).map { list -> list.map { it.toDomain() } }
            }
        }

    override suspend fun upsertIngredients(ingredients: List<IngredientUpsert>) {
        ingredientDao.upsertAll(ingredients.map { it.toEntity() })
    }
}
