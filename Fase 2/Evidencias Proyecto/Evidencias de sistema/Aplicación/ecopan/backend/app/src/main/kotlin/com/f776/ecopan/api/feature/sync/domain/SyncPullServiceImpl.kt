package com.f776.ecopan.api.feature.sync.domain

import com.f776.ecopan.api.feature.inventory.domain.ProductStockCalculator
import com.f776.ecopan.api.feature.sync.controller.dto.BranchPullPayload
import com.f776.ecopan.api.feature.sync.controller.dto.ChangeDto
import com.f776.ecopan.api.feature.sync.controller.dto.ForecastPullPayload
import com.f776.ecopan.api.feature.sync.controller.dto.FormulaLinePullPayload
import com.f776.ecopan.api.feature.sync.controller.dto.FormulaPullPayload
import com.f776.ecopan.api.feature.sync.controller.dto.IngredientPullPayload
import com.f776.ecopan.api.feature.sync.controller.dto.ProductPullPayload
import com.f776.ecopan.api.feature.sync.controller.dto.ProductionBatchPullPayload
import com.f776.ecopan.api.feature.sync.controller.dto.PullResponse
import com.f776.ecopan.api.feature.sync.controller.dto.SaleLinePayload
import com.f776.ecopan.api.feature.sync.controller.dto.SalePayload
import com.f776.ecopan.api.feature.sync.controller.dto.StockMovementPayload
import com.f776.ecopan.api.feature.sync.controller.dto.UserPullPayload
import com.f776.ecopan.data.database.repository.SyncPullRepository
import com.f776.ecopan.data.database.repository.model.Branch
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.model.Forecast
import com.f776.ecopan.data.database.repository.model.Formula
import com.f776.ecopan.data.database.repository.model.Ingredient
import com.f776.ecopan.data.database.repository.model.Product
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.repository.model.ProductionBatch
import com.f776.ecopan.data.database.repository.model.Sale
import com.f776.ecopan.data.database.repository.model.StockMovement
import com.f776.ecopan.data.database.repository.model.User
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.SyncEntityType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.uuid.Uuid

internal class SyncPullServiceImpl(
    private val syncPullRepository: SyncPullRepository,
    private val tenantRepository: TenantRepository,
    private val stockCalculator: ProductStockCalculator
) : SyncPullService {

    private val json = Json

    override suspend fun pull(
        tenantId: Uuid,
        principalBranchId: Uuid?,
        since: Long,
        limit: Int,
        entityTypes: Set<SyncEntityType>?
    ): PullResponse {
        // Forecasts ride the pull only for a tenant with DEMAND_FORECASTING, whether or not the
        // client names the type: a disabled module must not leak rows an earlier refresh left behind.
        val forecasting = tenantRepository.findById(tenantId)?.enabledModules?.contains(Module.DEMAND_FORECASTING) == true
        val wanted = (entityTypes ?: SyncEntityType.entries.toSet()).let { if (forecasting) it else it - SyncEntityType.FORECAST }
        val page = syncPullRepository.pull(tenantId, since, limit, wanted, principalBranchId)
        // Two reads per page (ledger sum + grouped sales), and only when an Almacén product is on it
        // and the caller has a branch.
        val stock = if (principalBranchId != null) {
            stockCalculator.figures(tenantId, principalBranchId, page.rows.mapNotNull { it.data as? Product })
        } else {
            emptyMap()
        }
        val changes = page.rows.map { row ->
            ChangeDto(
                entityType = row.entityType,
                opType = "UPSERT",
                serverSeq = row.serverSeq,
                payload = toPayload(row.data) { product -> stock[product.id]?.currentUnits }
            )
        }
        val nextCursor = changes.lastOrNull()?.serverSeq ?: since
        return PullResponse(changes = changes, nextCursor = nextCursor, hasMore = page.hasMore)
    }

    private fun toPayload(data: Any, stockOf: (Product) -> Long?) = when (data) {
        is Branch -> json.encodeToJsonElement(BranchPullPayload(data.id.toString(), data.name, data.active))
        is User -> json.encodeToJsonElement(
            UserPullPayload(
                id = data.id.toString(),
                branchId = data.branchId?.toString(),
                username = data.username,
                displayName = data.displayName,
                role = data.role.name,
                active = data.active,
                pinHash = data.pinHash
            )
        )
        is Product -> json.encodeToJsonElement(
            ProductPullPayload(
                id = data.id.toString(),
                name = data.name,
                productType = data.productType,
                soldBy = data.soldBy.name,
                pricePerKgMinor = data.pricePerKgMinor,
                priceMinor = data.priceMinor,
                barcode = data.barcode,
                imageUrl = data.imageUrl,
                active = data.active,
                origin = data.origin.name,
                lowStockThresholdUnits = data.lowStockThresholdUnits.takeIf { data.origin == ProductOrigin.ALMACEN },
                stockUnits = stockOf(data)
            )
        )
        is Forecast -> json.encodeToJsonElement(
            ForecastPullPayload(
                id = data.id.toString(),
                branchId = data.branchId.toString(),
                productId = data.productId.toString(),
                productName = data.productName,
                productType = data.productType,
                forecastDate = data.forecastDate.toString(),
                predictedGrams = data.predictedGrams,
                predictedUnits = data.predictedUnits,
                modelVersion = data.modelVersion,
                generatedAt = data.generatedAt.toEpochMilliseconds()
            )
        )
        is Ingredient -> json.encodeToJsonElement(
            IngredientPullPayload(data.id.toString(), data.name, data.lowStockThresholdGrams, data.active)
        )
        is Formula -> json.encodeToJsonElement(
            FormulaPullPayload(
                id = data.id.toString(),
                productId = data.productId.toString(),
                active = data.active,
                lines = data.lines.map { FormulaLinePullPayload(it.id.toString(), it.ingredientId.toString(), it.basisPoints) }
            )
        )
        is Sale -> json.encodeToJsonElement(
            SalePayload(
                id = data.id.toString(),
                branchId = data.branchId.toString(),
                cashierId = data.cashierId.toString(),
                soldAt = data.soldAt.toEpochMilliseconds(),
                paymentMethod = data.paymentMethod.name,
                totalMinor = data.totalMinor,
                lines = data.lines.map {
                    SaleLinePayload(
                        id = it.id.toString(),
                        productId = it.productId.toString(),
                        soldBy = it.soldBy.name,
                        weightGrams = it.weightGrams,
                        quantity = it.quantity,
                        unitPriceMinor = it.unitPriceMinor,
                        lineTotalMinor = it.lineTotalMinor
                    )
                }
            )
        )
        is ProductionBatch -> json.encodeToJsonElement(
            ProductionBatchPullPayload(
                id = data.id.toString(),
                branchId = data.branchId.toString(),
                bakerId = data.bakerId.toString(),
                productId = data.productId.toString(),
                mixWeightGrams = data.mixWeightGrams,
                batchAt = data.batchAt.toEpochMilliseconds(),
                movementsMinted = data.movementsMinted,
                decrementStatus = data.decrementStatus.name
            )
        )
        is StockMovement -> json.encodeToJsonElement(
            StockMovementPayload(
                id = data.id.toString(),
                branchId = data.branchId.toString(),
                ingredientId = data.ingredientId.toString(),
                deltaGrams = data.deltaGrams,
                kind = data.kind.name,
                note = data.note,
                occurredAt = data.occurredAt.toEpochMilliseconds(),
                sourceBatchId = data.sourceBatchId?.toString()
            )
        )
        else -> error("unmapped pull row type: ${data::class}")
    }
}
