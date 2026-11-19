@file:OptIn(ExperimentalCoroutinesApi::class)

package com.f776.ecopan.data.catalog

import com.f776.ecopan.data.catalog.local.mapper.toDomain
import com.f776.ecopan.data.catalog.local.mapper.toEntity
import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.catalog.ProductUpsert
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.unigrid.core.database.dao.ProductDao
import com.f776.unigrid.core.database.dao.SaleDao
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

/** Catalog config, like `:data:tenant`, is scoped to whichever tenant is currently cached (01 §4). */
internal class ProductRepositoryImpl(
    private val productDao: ProductDao,
    private val saleDao: SaleDao,
    private val tenantRepository: TenantRepository,
) : ProductRepository {

    override fun observeActiveProducts(): Flow<List<Product>> =
        tenantRepository.observeTenant().flatMapLatest { tenant ->
            if (tenant == null) {
                flowOf(emptyList())
            } else {
                // Stock the server reported, less what this till sold since and has not pushed yet.
                combine(
                    productDao.observeActiveByTenant(tenant.id),
                    saleDao.observeUnsyncedUnitsByProduct(),
                ) { products, unsynced ->
                    val soldByProduct = unsynced.associate { it.productId to it.units }
                    products.map { it.toDomain(unsyncedSoldUnits = soldByProduct[it.id] ?: 0L) }
                }
            }
        }

    override suspend fun upsertProducts(products: List<ProductUpsert>) {
        productDao.upsertAll(products.map { it.toEntity() })
    }

    override suspend fun findByBarcode(barcode: String): Product? {
        val tenant = tenantRepository.observeTenant().first() ?: return null
        return productDao.findByBarcode(tenant.id, barcode)?.toDomain()
    }

    override suspend fun findById(id: Uuid): Product? = productDao.findById(id)?.toDomain()
}
