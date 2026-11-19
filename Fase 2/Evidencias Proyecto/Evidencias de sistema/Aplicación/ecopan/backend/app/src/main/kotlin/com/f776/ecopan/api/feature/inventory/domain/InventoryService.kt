package com.f776.ecopan.api.feature.inventory.domain

import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.feature.inventory.controller.dto.CreateIngredientRequest
import com.f776.ecopan.api.feature.inventory.controller.dto.CreateProductStockMovementRequest
import com.f776.ecopan.api.feature.inventory.controller.dto.CreateStockMovementRequest
import com.f776.ecopan.api.feature.inventory.controller.dto.CreateStockMovementResponse
import com.f776.ecopan.api.feature.inventory.controller.dto.IngredientDto
import com.f776.ecopan.api.feature.inventory.controller.dto.ProductLowStockThresholdDto
import com.f776.ecopan.api.feature.inventory.controller.dto.ProductStockLevelDto
import com.f776.ecopan.api.feature.inventory.controller.dto.ProductStockMovementDto
import com.f776.ecopan.api.feature.inventory.controller.dto.ReconciliationReportDto
import com.f776.ecopan.api.feature.inventory.controller.dto.StockLevelDto
import com.f776.ecopan.api.feature.inventory.controller.dto.StockMovementDto
import com.f776.ecopan.api.feature.inventory.controller.dto.UpdateIngredientRequest
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface InventoryService {
    suspend fun listIngredients(tenantId: Uuid, includeInactive: Boolean): List<IngredientDto>
    suspend fun createIngredient(tenantId: Uuid, request: CreateIngredientRequest): ServiceOutcome<IngredientDto>
    suspend fun updateIngredient(tenantId: Uuid, ingredientId: Uuid, request: UpdateIngredientRequest): ServiceOutcome<IngredientDto>

    /** [principalBranchId] non-null means a branch-scoped role -- must match [branchId] or `BRANCH_FORBIDDEN`. */
    suspend fun stockLevels(tenantId: Uuid, branchId: Uuid, principalBranchId: Uuid?): ServiceOutcome<List<StockLevelDto>>

    /**
     * Ledger balance per ingredient over the closed day range [[from], [to]] (both inclusive), with
     * day boundaries taken in the reporting timezone. `INVALID_DATE_RANGE` when [from] is after [to].
     */
    suspend fun reconcile(
        tenantId: Uuid,
        branchId: Uuid,
        from: LocalDate,
        to: LocalDate,
        principalBranchId: Uuid?
    ): ServiceOutcome<ReconciliationReportDto>

    suspend fun listMovements(
        tenantId: Uuid,
        branchId: Uuid?,
        ingredientId: Uuid?,
        from: Instant?,
        to: Instant?,
        limit: Int,
        offset: Int,
        principalBranchId: Uuid?
    ): ServiceOutcome<List<StockMovementDto>>

    suspend fun appendMovement(
        tenantId: Uuid,
        createdBy: Uuid,
        request: CreateStockMovementRequest,
        principalBranchId: Uuid?
    ): ServiceOutcome<CreateStockMovementResponse>

    // -- Resold-product unit stock (drinks, packaged goods) -- separate ledger from ingredients.

    /** Every product with at least one movement in [branchId], with sales netted from its first movement on. */
    suspend fun productStockLevels(tenantId: Uuid, branchId: Uuid, principalBranchId: Uuid?): ServiceOutcome<List<ProductStockLevelDto>>

    /** Sets or clears a unit product's reorder level; a `WEIGHT` product is `PRODUCT_NOT_UNIT`, a negative level `INVALID_THRESHOLD`. */
    suspend fun setProductLowStockThreshold(tenantId: Uuid, productId: Uuid, thresholdUnits: Long?): ServiceOutcome<ProductLowStockThresholdDto>

    suspend fun listProductMovements(
        tenantId: Uuid,
        branchId: Uuid?,
        productId: Uuid?,
        limit: Int,
        offset: Int,
        principalBranchId: Uuid?
    ): ServiceOutcome<List<ProductStockMovementDto>>

    /** Only `UNIT` products can be stocked by the unit; a `WEIGHT` product is `PRODUCT_NOT_UNIT`. */
    suspend fun appendProductMovement(
        tenantId: Uuid,
        createdBy: Uuid,
        request: CreateProductStockMovementRequest,
        principalBranchId: Uuid?
    ): ServiceOutcome<ProductStockMovementDto>
}
