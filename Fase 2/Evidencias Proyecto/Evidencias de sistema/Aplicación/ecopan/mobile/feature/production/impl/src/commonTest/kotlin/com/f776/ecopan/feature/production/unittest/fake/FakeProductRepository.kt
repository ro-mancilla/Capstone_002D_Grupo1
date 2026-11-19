package com.f776.ecopan.feature.production.unittest.fake

import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.catalog.ProductUpsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

class FakeProductRepository : ProductRepository {
    val products = MutableStateFlow<List<Product>>(emptyList())

    override fun observeActiveProducts() = products

    override suspend fun upsertProducts(products: List<ProductUpsert>) = Unit

    override suspend fun findByBarcode(barcode: String): Product? =
        products.value.firstOrNull { it.barcode == barcode }

    override suspend fun findById(id: Uuid): Product? =
        products.value.firstOrNull { it.id == id }
}
