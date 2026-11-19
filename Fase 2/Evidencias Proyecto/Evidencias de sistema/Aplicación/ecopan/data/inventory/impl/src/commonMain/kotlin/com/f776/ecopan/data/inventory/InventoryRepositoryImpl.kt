@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.inventory

import com.f776.ecopan.data.inventory.local.mapper.toDomain
import com.f776.ecopan.data.inventory.local.mapper.toEntity
import com.f776.ecopan.domain.inventory.InventoryRepository
import com.f776.ecopan.domain.inventory.StockLevel
import com.f776.ecopan.domain.inventory.StockMovement
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.unigrid.core.database.dao.IngredientDao
import com.f776.unigrid.core.database.dao.StockMovementDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/** [stockLevels] starts from every active ingredient (not just ones with movements) so an
 * untouched ingredient still shows up at 0 g rather than silently disappearing from the list. */
internal class InventoryRepositoryImpl(
    private val ingredientDao: IngredientDao,
    private val stockMovementDao: StockMovementDao,
    private val tenantRepository: TenantRepository,
) : InventoryRepository {

    override fun stockLevels(branchId: Uuid): Flow<List<StockLevel>> =
        tenantRepository.observeTenant().flatMapLatest { tenant ->
            if (tenant == null) {
                flowOf(emptyList())
            } else {
                combine(
                    ingredientDao.observeActiveByTenant(tenant.id),
                    stockMovementDao.observeDeltaSumsByBranch(branchId),
                ) { ingredients, sums ->
                    val totalsByIngredient = sums.associate { it.ingredientId to it.totalDeltaGrams }
                    ingredients.map { ingredient ->
                        StockLevel(
                            ingredientId = ingredient.id,
                            name = ingredient.name,
                            currentGrams = totalsByIngredient[ingredient.id] ?: 0L,
                        )
                    }
                }
            }
        }

    override fun movements(branchId: Uuid, ingredientId: Uuid): Flow<List<StockMovement>> =
        stockMovementDao.observeByIngredient(branchId, ingredientId).map { list -> list.map { it.toDomain() } }

    override suspend fun appendMovements(movements: List<StockMovement>) {
        stockMovementDao.insertMovements(movements.map { it.toEntity() })
    }
}
