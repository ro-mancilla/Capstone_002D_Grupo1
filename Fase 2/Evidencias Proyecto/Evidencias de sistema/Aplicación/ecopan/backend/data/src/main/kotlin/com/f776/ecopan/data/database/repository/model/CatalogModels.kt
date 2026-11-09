package com.f776.ecopan.data.database.repository.model

import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class Product(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val productType: String,
    val soldBy: SoldBy,
    val pricePerKgMinor: Long?,
    val priceMinor: Long?,
    val barcode: String?,
    val imageUrl: String?,
    val active: Boolean,
    val serverSeq: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val lowStockThresholdUnits: Long? = null,
    val origin: ProductOrigin = ProductOrigin.PANADERIA
)

data class Ingredient(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String,
    val lowStockThresholdGrams: Long?,
    val active: Boolean,
    val serverSeq: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class FormulaLine(
    val id: Uuid,
    val ingredientId: Uuid,
    val basisPoints: Int
)

data class Formula(
    val id: Uuid,
    val tenantId: Uuid,
    val productId: Uuid,
    val active: Boolean,
    val serverSeq: Long,
    val updatedAt: Instant,
    val lines: List<FormulaLine>
)

data class ProductType(
    val id: Uuid,
    val tenantId: Uuid,
    val name: String
)
