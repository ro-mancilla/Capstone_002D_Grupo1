package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.f776.unigrid.core.database.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface UserDao {
    @Upsert
    suspend fun upsertAll(users: List<UserEntity>)

    @Query("SELECT * FROM users WHERE tenantId = :tenantId ORDER BY displayName ASC")
    fun observeByTenant(tenantId: Uuid): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: Uuid): UserEntity?

    @Query("DELETE FROM users")
    suspend fun clear()
}
