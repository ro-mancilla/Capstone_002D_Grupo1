package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.f776.unigrid.core.database.entity.TenantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantDao {
    /** One device enrolls to one tenant; a fresh fetch replaces the single cached row. */
    @Upsert
    suspend fun upsert(tenant: TenantEntity)

    @Query("SELECT * FROM tenants LIMIT 1")
    fun observe(): Flow<TenantEntity?>

    @Query("DELETE FROM tenants")
    suspend fun clear()
}
