package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.BranchTable
import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.ForecastTable
import com.f776.ecopan.data.database.entity.FormulaLineTable
import com.f776.ecopan.data.database.entity.FormulaTable
import com.f776.ecopan.data.database.entity.IngredientTable
import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductTable
import com.f776.ecopan.data.database.entity.ProductionBatchTable
import com.f776.ecopan.data.database.entity.SaleLineTable
import com.f776.ecopan.data.database.entity.SaleTable
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.entity.StockMovementTable
import com.f776.ecopan.data.database.entity.UserTable
import com.f776.ecopan.data.database.repository.model.Branch
import com.f776.ecopan.data.database.repository.model.Forecast
import com.f776.ecopan.data.database.repository.model.Formula
import com.f776.ecopan.data.database.repository.model.FormulaLine
import com.f776.ecopan.data.database.repository.model.Ingredient
import com.f776.ecopan.data.database.repository.model.ProductionBatch
import com.f776.ecopan.data.database.repository.model.Product
import com.f776.ecopan.data.database.repository.model.PullRow
import com.f776.ecopan.data.database.repository.model.Sale
import com.f776.ecopan.data.database.repository.model.SaleLine
import com.f776.ecopan.data.database.repository.model.StockMovement
import com.f776.ecopan.data.database.repository.model.SyncPullPage
import com.f776.ecopan.data.database.repository.model.User
import com.f776.ecopan.data.domain.Role
import com.f776.ecopan.data.domain.SyncEntityType
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.uuid.Uuid

/**
 * Direct table access (rather than delegating to the config/fact repositories) so every source
 * table is read inside the *same* transaction (02 §4.2's merge-sort note) and to keep this the one
 * place that assembles the pull page.
 */
internal class SyncPullRepositoryImpl : SyncPullRepository {

    override suspend fun pull(
        tenantId: Uuid,
        since: Long,
        limit: Int,
        entityTypes: Set<SyncEntityType>?,
        branchId: Uuid?
    ): SyncPullPage = suspendTransaction {
        // Over-fetch by one per table so that, once everything is merged and trimmed to `limit`,
        // we can tell "there might be more beyond this page" apart from "every table is exhausted".
        val fetchLimit = limit + 1
        fun wanted(type: SyncEntityType) = entityTypes == null || type in entityTypes

        val rows = mutableListOf<PullRow>()

        if (wanted(SyncEntityType.BRANCH)) {
            BranchTable.selectAll()
                .where { (BranchTable.tenantId eq tenantId) and (BranchTable.serverSeq greater since) }
                .orderBy(BranchTable.serverSeq, SortOrder.ASC)
                .limit(fetchLimit)
                .forEach { row ->
                    val branch = row.toBranch()
                    rows += PullRow(SyncEntityType.BRANCH.name, branch.serverSeq, branch)
                }
        }

        if (wanted(SyncEntityType.USER)) {
            UserTable.selectAll()
                .where { (UserTable.tenantId eq tenantId) and (UserTable.serverSeq greater since) }
                .orderBy(UserTable.serverSeq, SortOrder.ASC)
                .limit(fetchLimit)
                .forEach { row ->
                    val user = row.toUser()
                    rows += PullRow(SyncEntityType.USER.name, user.serverSeq, user)
                }
        }

        if (wanted(SyncEntityType.PRODUCT)) {
            ProductTable.selectAll()
                .where { (ProductTable.tenantId eq tenantId) and (ProductTable.serverSeq greater since) }
                .orderBy(ProductTable.serverSeq, SortOrder.ASC)
                .limit(fetchLimit)
                .forEach { row ->
                    val product = row.toProduct()
                    rows += PullRow(SyncEntityType.PRODUCT.name, product.serverSeq, product)
                }
        }

        if (wanted(SyncEntityType.INGREDIENT)) {
            IngredientTable.selectAll()
                .where { (IngredientTable.tenantId eq tenantId) and (IngredientTable.serverSeq greater since) }
                .orderBy(IngredientTable.serverSeq, SortOrder.ASC)
                .limit(fetchLimit)
                .forEach { row ->
                    val ingredient = row.toIngredient()
                    rows += PullRow(SyncEntityType.INGREDIENT.name, ingredient.serverSeq, ingredient)
                }
        }

        if (wanted(SyncEntityType.FORMULA)) {
            FormulaTable.selectAll()
                .where { (FormulaTable.tenantId eq tenantId) and (FormulaTable.serverSeq greater since) }
                .orderBy(FormulaTable.serverSeq, SortOrder.ASC)
                .limit(fetchLimit)
                .toList()
                .forEach { row ->
                    val formulaId = row[FormulaTable.id]
                    val lines = FormulaLineTable.selectAll()
                        .where { FormulaLineTable.formulaId eq formulaId }
                        .map { FormulaLine(it[FormulaLineTable.id], it[FormulaLineTable.ingredientId], it[FormulaLineTable.basisPoints]) }
                    val formula = Formula(
                        id = formulaId,
                        tenantId = row[FormulaTable.tenantId],
                        productId = row[FormulaTable.productId],
                        active = row[FormulaTable.active],
                        serverSeq = row[FormulaTable.serverSeq],
                        updatedAt = row[FormulaTable.updatedAt],
                        lines = lines
                    )
                    rows += PullRow(SyncEntityType.FORMULA.name, formula.serverSeq, formula)
                }
        }

        if (wanted(SyncEntityType.SALE)) {
            SaleTable.selectAll()
                .where { (SaleTable.tenantId eq tenantId) and (SaleTable.serverSeq greater since) }
                .orderBy(SaleTable.serverSeq, SortOrder.ASC)
                .limit(fetchLimit)
                .toList()
                .forEach { row ->
                    val saleId = row[SaleTable.id]
                    val lines = SaleLineTable.selectAll()
                        .where { SaleLineTable.saleId eq saleId }
                        .map { it.toSaleLine() }
                    val sale = row.toSale(lines)
                    rows += PullRow(SyncEntityType.SALE.name, sale.serverSeq, sale)
                }
        }

        if (wanted(SyncEntityType.PRODUCTION_BATCH)) {
            ProductionBatchTable.selectAll()
                .where { (ProductionBatchTable.tenantId eq tenantId) and (ProductionBatchTable.serverSeq greater since) }
                .orderBy(ProductionBatchTable.serverSeq, SortOrder.ASC)
                .limit(fetchLimit)
                .forEach { row ->
                    val batch = row.toProductionBatch()
                    rows += PullRow(SyncEntityType.PRODUCTION_BATCH.name, batch.serverSeq, batch)
                }
        }

        if (wanted(SyncEntityType.STOCK_MOVEMENT)) {
            StockMovementTable.selectAll()
                .where { (StockMovementTable.tenantId eq tenantId) and (StockMovementTable.serverSeq greater since) }
                .orderBy(StockMovementTable.serverSeq, SortOrder.ASC)
                .limit(fetchLimit)
                .forEach { row ->
                    val movement = row.toStockMovement()
                    rows += PullRow(SyncEntityType.STOCK_MOVEMENT.name, movement.serverSeq, movement)
                }
        }

        if (wanted(SyncEntityType.FORECAST)) {
            ForecastTable.join(ProductTable, JoinType.INNER) { ForecastTable.productId eq ProductTable.id }
                .selectAll()
                .where {
                    var condition = (ForecastTable.tenantId eq tenantId) and (ForecastTable.serverSeq greater since)
                    branchId?.let { condition = condition and (ForecastTable.branchId eq it) }
                    condition
                }
                .orderBy(ForecastTable.serverSeq, SortOrder.ASC)
                .limit(fetchLimit)
                .forEach { row ->
                    val forecast = Forecast(
                        id = row[ForecastTable.id],
                        tenantId = row[ForecastTable.tenantId],
                        branchId = row[ForecastTable.branchId],
                        productId = row[ForecastTable.productId],
                        productName = row[ProductTable.name],
                        productType = row[ProductTable.productType],
                        forecastDate = row[ForecastTable.forecastDate],
                        predictedGrams = row[ForecastTable.predictedGrams],
                        predictedUnits = row[ForecastTable.predictedUnits],
                        modelVersion = row[ForecastTable.modelVersion],
                        generatedAt = row[ForecastTable.generatedAt],
                        serverSeq = row[ForecastTable.serverSeq]
                    )
                    rows += PullRow(SyncEntityType.FORECAST.name, forecast.serverSeq, forecast)
                }
        }

        val sorted = rows.sortedBy { it.serverSeq }
        SyncPullPage(rows = sorted.take(limit), hasMore = sorted.size > limit)
    }

    private fun ResultRow.toBranch() = Branch(
        id = this[BranchTable.id],
        tenantId = this[BranchTable.tenantId],
        name = this[BranchTable.name],
        active = this[BranchTable.active],
        serverSeq = this[BranchTable.serverSeq],
        createdAt = this[BranchTable.createdAt]
    )

    private fun ResultRow.toUser() = User(
        id = this[UserTable.id],
        tenantId = this[UserTable.tenantId],
        branchId = this[UserTable.branchId],
        username = this[UserTable.username],
        passwordHash = this[UserTable.passwordHash],
        pinHash = this[UserTable.pinHash],
        displayName = this[UserTable.displayName],
        role = this[UserTable.role],
        active = this[UserTable.active],
        serverSeq = this[UserTable.serverSeq],
        createdAt = this[UserTable.createdAt]
    )

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

    private fun ResultRow.toIngredient() = Ingredient(
        id = this[IngredientTable.id],
        tenantId = this[IngredientTable.tenantId],
        name = this[IngredientTable.name],
        lowStockThresholdGrams = this[IngredientTable.lowStockThresholdGrams],
        active = this[IngredientTable.active],
        serverSeq = this[IngredientTable.serverSeq],
        createdAt = this[IngredientTable.createdAt],
        updatedAt = this[IngredientTable.updatedAt]
    )

    private fun ResultRow.toSale(lines: List<SaleLine>) = Sale(
        id = this[SaleTable.id],
        tenantId = this[SaleTable.tenantId],
        branchId = this[SaleTable.branchId],
        cashierId = this[SaleTable.cashierId],
        soldAt = this[SaleTable.soldAt],
        paymentMethod = this[SaleTable.paymentMethod],
        totalMinor = this[SaleTable.totalMinor],
        deviceId = this[SaleTable.deviceId],
        serverSeq = this[SaleTable.serverSeq],
        receivedAt = this[SaleTable.receivedAt],
        lines = lines
    )

    private fun ResultRow.toSaleLine() = SaleLine(
        id = this[SaleLineTable.id],
        saleId = this[SaleLineTable.saleId],
        productId = this[SaleLineTable.productId],
        soldBy = this[SaleLineTable.soldBy],
        weightGrams = this[SaleLineTable.weightGrams],
        quantity = this[SaleLineTable.quantity],
        unitPriceMinor = this[SaleLineTable.unitPriceMinor],
        lineTotalMinor = this[SaleLineTable.lineTotalMinor]
    )

    private fun ResultRow.toProductionBatch() = ProductionBatch(
        id = this[ProductionBatchTable.id],
        tenantId = this[ProductionBatchTable.tenantId],
        branchId = this[ProductionBatchTable.branchId],
        bakerId = this[ProductionBatchTable.bakerId],
        productId = this[ProductionBatchTable.productId],
        mixWeightGrams = this[ProductionBatchTable.mixWeightGrams],
        batchAt = this[ProductionBatchTable.batchAt],
        movementsMinted = this[ProductionBatchTable.movementsMinted],
        decrementStatus = this[ProductionBatchTable.decrementStatus],
        deviceId = this[ProductionBatchTable.deviceId],
        serverSeq = this[ProductionBatchTable.serverSeq],
        receivedAt = this[ProductionBatchTable.receivedAt]
    )

    private fun ResultRow.toStockMovement() = StockMovement(
        id = this[StockMovementTable.id],
        tenantId = this[StockMovementTable.tenantId],
        branchId = this[StockMovementTable.branchId],
        ingredientId = this[StockMovementTable.ingredientId],
        deltaGrams = this[StockMovementTable.deltaGrams],
        kind = this[StockMovementTable.kind],
        sourceBatchId = this[StockMovementTable.sourceBatchId],
        note = this[StockMovementTable.note],
        createdBy = this[StockMovementTable.createdBy],
        occurredAt = this[StockMovementTable.occurredAt],
        serverSeq = this[StockMovementTable.serverSeq],
        receivedAt = this[StockMovementTable.receivedAt]
    )
}
