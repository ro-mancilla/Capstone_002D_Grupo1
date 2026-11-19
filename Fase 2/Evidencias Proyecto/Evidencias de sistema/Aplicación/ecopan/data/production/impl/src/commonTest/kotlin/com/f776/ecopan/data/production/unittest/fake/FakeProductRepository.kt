package com.f776.ecopan.data.production.unittest.fake

import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.catalog.ProductUpsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

internal class FakeProductRepository : ProductRepository {
    private val state = MutableStateFlow<Map<Uuid, Product>>(emptyMap())

    fun seed(product: Product) {
        state.value = state.value + (product.id to product)
    }

    override fun observeActiveProducts() = throw NotImplementedError("unused by :data:production")
    override suspend fun upsertProducts(products: List<ProductUpsert>) = throw NotImplementedError("unused by :data:production")
    override suspend fun findByBarcode(barcode: String) = throw NotImplementedError("unused by :data:production")
    override suspend fun findById(id: Uuid) = state.value[id]
}
