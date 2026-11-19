package com.f776.ecopan.domain.catalog

import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

interface ProductRepository {
    fun observeActiveProducts(): Flow<List<Product>>
    suspend fun upsertProducts(products: List<ProductUpsert>)

    /** POS barcode-scan lookup (05); returns null once the product is inactive. */
    suspend fun findByBarcode(barcode: String): Product?

    /** Baker-mobile batch logging (06) needs a single product by id, active or not. */
    suspend fun findById(id: Uuid): Product?
}
