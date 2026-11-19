package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.f776.unigrid.core.database.entity.IngredientEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface IngredientDao {
    @Upsert
    suspend fun upsertAll(ingredients: List<IngredientEntity>)

    @Query("SELECT * FROM ingredients WHERE tenantId = :tenantId AND active = 1 ORDER BY name ASC")
    fun observeActiveByTenant(tenantId: Uuid): Flow<List<IngredientEntity>>

    @Query("DELETE FROM ingredients")
    suspend fun clear()
}
