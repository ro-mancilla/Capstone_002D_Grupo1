package com.f776.ecopan.feature.pos.unittest.fake

import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.catalog.ProductUpsert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.uuid.Uuid

internal class FakeProductRepository(initial: List<Product> = emptyList()) : ProductRepository {
    val products = MutableStateFlow(initial)

    override fun observeActiveProducts() = products

    override suspend fun upsertProducts(products: List<ProductUpsert>) {
        error("not needed by PosViewModelTest")
    }

    override suspend fun findByBarcode(barcode: String): Product? =
        products.value.firstOrNull { it.barcode == barcode }

    override suspend fun findById(id: Uuid): Product? =
        products.value.firstOrNull { it.id == id }
}
