package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.FormulaLineTable
import com.f776.ecopan.data.database.entity.FormulaTable
import com.f776.ecopan.data.database.entity.ProductTable
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.nextServerSeq
import com.f776.ecopan.data.database.repository.model.Formula
import com.f776.ecopan.data.database.repository.model.FormulaLine
import com.f776.ecopan.data.database.repository.model.Product
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class CatalogRepositoryImpl : CatalogRepository {

    override suspend fun createProduct(
        id: Uuid,
        tenantId: Uuid,
        name: String,
        productType: String,
        soldBy: SoldBy,
        pricePerKgMinor: Long?,
        priceMinor: Long?,
        barcode: String?,
        imageUrl: String?,
        origin: ProductOrigin,
        opening: OpeningCount?
    ): Product = suspendTransaction {
        val seq = nextServerSeq()
        // Resolved to the tenant's managed type (created if new): no free-text variant can reach the table.
        val type = ensureProductType(tenantId, productType).first
        ProductTable.insert {
            it[ProductTable.id] = id
            it[ProductTable.tenantId] = tenantId
            it[ProductTable.name] = name
            it[ProductTable.productType] = type.name
            it[ProductTable.productTypeId] = type.id
            it[ProductTable.soldBy] = soldBy
            it[ProductTable.origin] = origin
            it[ProductTable.pricePerKgMinor] = pricePerKgMinor
            it[ProductTable.priceMinor] = priceMinor
            it[ProductTable.barcode] = barcode
            it[ProductTable.imageUrl] = imageUrl
            it[ProductTable.serverSeq] = seq
        }
        opening?.let {
            appendMovement(
                it.movementId, tenantId, it.branchId, id, it.units,
                ProductStockMovementKind.OPENING_COUNT, null, it.createdBy, it.occurredAt
            )
        }
        findProductByIdInternal(id)!!
    }

    override suspend fun findProductById(id: Uuid): Product? = suspendTransaction {
        findProductByIdInternal(id)
    }

    override suspend fun listProducts(tenantId: Uuid, includeInactive: Boolean): List<Product> = suspendTransaction {
        ProductTable.selectAll()
            .where {
                if (includeInactive) {
                    ProductTable.tenantId eq tenantId
                } else {
                    (ProductTable.tenantId eq tenantId) and (ProductTable.active eq true)
                }
            }
            .map { it.toProduct() }
    }

    override suspend fun updateProduct(
        id: Uuid,
        name: String?,
        productType: String?,
        soldBy: SoldBy?,
        pricePerKgMinor: Long?,
        priceMinor: Long?,
        barcode: String?,
        imageUrl: String?,
        active: Boolean?,
        origin: ProductOrigin?
    ): Product? = suspendTransaction {
        val seq = nextServerSeq()
        val type = productType?.let { raw ->
            val tenantId = ProductTable.selectAll().where { ProductTable.id eq id }
                .singleOrNull()?.get(ProductTable.tenantId)
            tenantId?.let { ensureProductType(it, raw).first }
        }
        ProductTable.update({ ProductTable.id eq id }) { stmt ->
            name?.let { stmt[ProductTable.name] = it }
            type?.let {
                stmt[ProductTable.productType] = it.name
                stmt[ProductTable.productTypeId] = it.id
            }
            soldBy?.let { stmt[ProductTable.soldBy] = it }
            origin?.let { stmt[ProductTable.origin] = it }
            pricePerKgMinor?.let { stmt[ProductTable.pricePerKgMinor] = it }
            priceMinor?.let { stmt[ProductTable.priceMinor] = it }
            barcode?.let { stmt[ProductTable.barcode] = it }
            imageUrl?.let { stmt[ProductTable.imageUrl] = it }
            active?.let { stmt[ProductTable.active] = it }
            stmt[ProductTable.updatedAt] = Clock.System.now()
            stmt[ProductTable.serverSeq] = seq
        }
        findProductByIdInternal(id)
    }

    override suspend fun setProductImageUrl(id: Uuid, imageUrl: String?): Product? = suspendTransaction {
        val seq = nextServerSeq()
        ProductTable.update({ ProductTable.id eq id }) { stmt ->
            stmt[ProductTable.imageUrl] = imageUrl
            stmt[ProductTable.updatedAt] = Clock.System.now()
            stmt[ProductTable.serverSeq] = seq
        }
        findProductByIdInternal(id)
    }

    override suspend fun setProductLowStockThreshold(id: Uuid, thresholdUnits: Long?): Product? = suspendTransaction {
        val seq = nextServerSeq()
        ProductTable.update({ ProductTable.id eq id }) { stmt ->
            stmt[ProductTable.lowStockThresholdUnits] = thresholdUnits
            stmt[ProductTable.updatedAt] = Clock.System.now()
            stmt[ProductTable.serverSeq] = seq
        }
        findProductByIdInternal(id)
    }

    override suspend fun findFormulaByProduct(tenantId: Uuid, productId: Uuid): Formula? = suspendTransaction {
        findFormulaInternal(tenantId, productId)
    }

    override suspend fun putFormula(
        id: Uuid,
        tenantId: Uuid,
        productId: Uuid,
        lines: List<FormulaLine>
    ): Formula = suspendTransaction {
        val seq = nextServerSeq()
        val existing = FormulaTable.selectAll()
            .where { (FormulaTable.tenantId eq tenantId) and (FormulaTable.productId eq productId) }
            .singleOrNull()

        val formulaId = if (existing != null) {
            FormulaTable.update({ FormulaTable.id eq existing[FormulaTable.id] }) {
                it[FormulaTable.serverSeq] = seq
                it[FormulaTable.updatedAt] = Clock.System.now()
            }
            existing[FormulaTable.id]
        } else {
            FormulaTable.insert {
                it[FormulaTable.id] = id
                it[FormulaTable.tenantId] = tenantId
                it[FormulaTable.productId] = productId
                it[FormulaTable.serverSeq] = seq
                it[FormulaTable.updatedAt] = Clock.System.now()
            }
            id
        }

        FormulaLineTable.deleteWhere { FormulaLineTable.formulaId eq formulaId }
        lines.forEach { line ->
            FormulaLineTable.insert {
                it[FormulaLineTable.id] = line.id
                it[FormulaLineTable.tenantId] = tenantId
                it[FormulaLineTable.formulaId] = formulaId
                it[FormulaLineTable.ingredientId] = line.ingredientId
                it[FormulaLineTable.basisPoints] = line.basisPoints
            }
        }

        findFormulaInternal(tenantId, productId)!!
    }

    override suspend fun listFormulas(tenantId: Uuid, includeInactive: Boolean): List<Formula> = suspendTransaction {
        FormulaTable.selectAll()
            .where {
                if (includeInactive) {
                    FormulaTable.tenantId eq tenantId
                } else {
                    (FormulaTable.tenantId eq tenantId) and (FormulaTable.active eq true)
                }
            }
            .map { row ->
                val formulaId = row[FormulaTable.id]
                val lines = FormulaLineTable.selectAll()
                    .where { FormulaLineTable.formulaId eq formulaId }
                    .map {
                        FormulaLine(
                            id = it[FormulaLineTable.id],
                            ingredientId = it[FormulaLineTable.ingredientId],
                            basisPoints = it[FormulaLineTable.basisPoints]
                        )
                    }
                Formula(
                    id = formulaId,
                    tenantId = row[FormulaTable.tenantId],
                    productId = row[FormulaTable.productId],
                    active = row[FormulaTable.active],
                    serverSeq = row[FormulaTable.serverSeq],
                    updatedAt = row[FormulaTable.updatedAt],
                    lines = lines
                )
            }
    }

    private fun findProductByIdInternal(id: Uuid): Product? =
        ProductTable.selectAll().where { ProductTable.id eq id }.singleOrNull()?.toProduct()

    private fun findFormulaInternal(tenantId: Uuid, productId: Uuid): Formula? {
        val formulaRow = FormulaTable.selectAll()
            .where { (FormulaTable.tenantId eq tenantId) and (FormulaTable.productId eq productId) }
            .singleOrNull() ?: return null

        val formulaId = formulaRow[FormulaTable.id]
        val lines = FormulaLineTable.selectAll()
            .where { FormulaLineTable.formulaId eq formulaId }
            .map {
                FormulaLine(
                    id = it[FormulaLineTable.id],
                    ingredientId = it[FormulaLineTable.ingredientId],
                    basisPoints = it[FormulaLineTable.basisPoints]
                )
            }

        return Formula(
            id = formulaId,
            tenantId = formulaRow[FormulaTable.tenantId],
            productId = formulaRow[FormulaTable.productId],
            active = formulaRow[FormulaTable.active],
            serverSeq = formulaRow[FormulaTable.serverSeq],
            updatedAt = formulaRow[FormulaTable.updatedAt],
            lines = lines
        )
    }

    private fun ResultRow.toProduct() = Product(
        id = this[ProductTable.id],
        tenantId = this[ProductTable.tenantId],
        name = this[ProductTable.name],
        productType = this[ProductTable.productType],
        soldBy = this[ProductTable.soldBy],
        pricePerKgMinor = this[ProductTable.pricePerKgMinor],
        priceMinor = this[ProductTable.priceMinor],
        barcode = this[ProductTable.barcode],
        imageUrl = this[ProductTable.imageUrl],
        active = this[ProductTable.active],
        serverSeq = this[ProductTable.serverSeq],
        createdAt = this[ProductTable.createdAt],
        updatedAt = this[ProductTable.updatedAt],
        lowStockThresholdUnits = this[ProductTable.lowStockThresholdUnits],
        origin = this[ProductTable.origin]
    )

}
