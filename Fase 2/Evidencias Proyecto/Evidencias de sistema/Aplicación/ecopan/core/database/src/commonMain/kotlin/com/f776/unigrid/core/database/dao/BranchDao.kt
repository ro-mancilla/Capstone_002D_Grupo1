package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.f776.unigrid.core.database.entity.BranchEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface BranchDao {
    @Upsert
    suspend fun upsertAll(branches: List<BranchEntity>)

    @Query("SELECT * FROM branches WHERE tenantId = :tenantId ORDER BY name ASC")
    fun observeByTenant(tenantId: Uuid): Flow<List<BranchEntity>>

    @Query("DELETE FROM branches")
    suspend fun clear()
}
