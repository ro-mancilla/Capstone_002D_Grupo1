package com.f776.ecopan.data.catalog.unittest.fake

import com.f776.unigrid.core.database.dao.ProductDao
import com.f776.unigrid.core.database.entity.ProductEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class FakeProductDao : ProductDao {
    private val state = MutableStateFlow<Map<Uuid, ProductEntity>>(emptyMap())

    override suspend fun upsertAll(products: List<ProductEntity>) {
        state.value = state.value + products.associateBy { it.id }
    }

    override fun observeActiveByTenant(tenantId: Uuid) =
        state.map { byId ->
            byId.values.filter { it.tenantId == tenantId && it.active }.sortedBy { it.name }
        }

    override suspend fun findByBarcode(tenantId: Uuid, barcode: String) =
        state.value.values.firstOrNull { it.tenantId == tenantId && it.barcode == barcode && it.active }

    override suspend fun findById(id: Uuid) = state.value[id]

    override suspend fun clear() {
        state.value = emptyMap()
    }
}
