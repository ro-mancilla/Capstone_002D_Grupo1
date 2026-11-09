package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.f776.unigrid.core.database.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface ProductDao {
    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("SELECT * FROM products WHERE tenantId = :tenantId AND active = 1 ORDER BY name ASC")
    fun observeActiveByTenant(tenantId: Uuid): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE tenantId = :tenantId AND barcode = :barcode AND active = 1 LIMIT 1")
    suspend fun findByBarcode(tenantId: Uuid, barcode: String): ProductEntity?

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: Uuid): ProductEntity?

    @Query("DELETE FROM products")
    suspend fun clear()
}
