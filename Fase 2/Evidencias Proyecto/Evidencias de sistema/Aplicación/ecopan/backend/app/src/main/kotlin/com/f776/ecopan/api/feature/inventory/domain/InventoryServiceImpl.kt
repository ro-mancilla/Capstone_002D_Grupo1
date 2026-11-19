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
import com.f776.ecopan.api.feature.inventory.controller.dto.ReconciliationRowDto
import com.f776.ecopan.api.feature.inventory.controller.dto.StockLevelDto
import com.f776.ecopan.api.feature.inventory.controller.dto.StockMovementDto
import com.f776.ecopan.api.feature.inventory.controller.dto.UpdateIngredientRequest
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.IngredientRepository
import com.f776.ecopan.data.database.repository.ProductStockRepository
import com.f776.ecopan.data.database.repository.StockMovementRepository
import com.f776.ecopan.data.database.repository.model.Ingredient
import com.f776.ecopan.data.database.repository.model.ProductStockMovement
import com.f776.ecopan.data.database.repository.model.StockMovement
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.atStartOfDayIn
import org.slf4j.LoggerFactory
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val logger = LoggerFactory.getLogger(InventoryServiceImpl::class.java)

/** Widest report a request may ask for, in days (a leap year). */
private const val MAX_RECONCILIATION_DAYS = 366L
private val EARLIEST_RECONCILIATION_DAY = LocalDate(2000, 1, 1)
private val LATEST_RECONCILIATION_DAY = LocalDate(2200, 12, 31)

/** Why `[from, to]` cannot be reported on, or `null`. Bounding the years keeps the day arithmetic
 * far from the limits of the date types, so an absurd query is a 400 and never an exception. */
private fun reconciliationRangeProblem(from: LocalDate, to: LocalDate): String? = when {
    from > to -> "from must not be after to"
    from < EARLIEST_RECONCILIATION_DAY || to > LATEST_RECONCILIATION_DAY ->
        "dates must be between $EARLIEST_RECONCILIATION_DAY and $LATEST_RECONCILIATION_DAY"
    to.toEpochDays() - from.toEpochDays() + 1 > MAX_RECONCILIATION_DAYS ->
        "the range may not exceed $MAX_RECONCILIATION_DAYS days"
    else -> null
}

private val NOT_ALMACEN = ServiceOutcome.Invalid("PRODUCT_NOT_ALMACEN", "only Almacén products carry product stock")

internal class InventoryServiceImpl(
    private val ingredientRepository: IngredientRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val catalogRepository: CatalogRepository,
    private val productStockRepository: ProductStockRepository,
    private val stockCalculator: ProductStockCalculator,
    private val reportingTz: String
) : InventoryService {

    override suspend fun listIngredients(tenantId: Uuid, includeInactive: Boolean): List<IngredientDto> =
        ingredientRepository.listByTenant(tenantId, includeInactive).map { it.toDto() }

    override suspend fun createIngredient(tenantId: Uuid, request: CreateIngredientRequest): ServiceOutcome<IngredientDto> {
        val id = runCatching { Uuid.parse(request.id) }.getOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_ID", "id must be a valid uuid")
        val ingredient = ingredientRepository.create(id, tenantId, request.name, request.lowStockThresholdGrams)
        return ServiceOutcome.Success(ingredient.toDto())
    }

    override suspend fun updateIngredient(
        tenantId: Uuid,
        ingredientId: Uuid,
        request: UpdateIngredientRequest
    ): ServiceOutcome<IngredientDto> {
        val existing = ingredientRepository.findById(ingredientId)
        if (existing == null || existing.tenantId != tenantId) return ServiceOutcome.NotFound
        val updated = ingredientRepository.update(
            id = ingredientId,
            name = request.name,
            lowStockThresholdGrams = request.lowStockThresholdGrams,
            active = request.active
        ) ?: return ServiceOutcome.NotFound
        return ServiceOutcome.Success(updated.toDto())
    }

    override suspend fun stockLevels(tenantId: Uuid, branchId: Uuid, principalBranchId: Uuid?): ServiceOutcome<List<StockLevelDto>> {
        branchForbidden(branchId, principalBranchId)?.let { return it }

        val sums = stockMovementRepository.sumByIngredient(tenantId, branchId).associateBy { it.ingredientId }
        val ingredients = ingredientRepository.listByTenant(tenantId, includeInactive = true)
        val levels = ingredients.map { ingredient ->
            val currentGrams = sums[ingredient.id]?.currentGrams ?: 0L
            // At or under the reorder level (CONTEXT.md, Stock bajo). Ingredients have no Agotado
            // state, so 0 stays flagged low: it is the one that needs reordering most.
            val lowStock = ingredient.lowStockThresholdGrams?.let { currentGrams <= it } ?: false
            StockLevelDto(ingredient.id.toString(), ingredient.name, currentGrams, lowStock)
        }
        return ServiceOutcome.Success(levels)
    }

    override suspend fun reconcile(
        tenantId: Uuid,
        branchId: Uuid,
        from: LocalDate,
        to: LocalDate,
        principalBranchId: Uuid?
    ): ServiceOutcome<ReconciliationReportDto> {
        branchForbidden(branchId, principalBranchId)?.let { return it }
        reconciliationRangeProblem(from, to)?.let { return ServiceOutcome.Invalid("INVALID_DATE_RANGE", it) }

        val zone = TimeZone.of(reportingTz)
        val start = from.atStartOfDayIn(zone)
        // `to` is a whole day, so the window ends (exclusively) at the start of the day after it.
        val end = to.plus(1, DateTimeUnit.DAY).atStartOfDayIn(zone)

        val namesById = ingredientRepository.listByTenant(tenantId, includeInactive = true).associate { it.id to it.name }
        val rows = stockMovementRepository.reconcileByIngredient(tenantId, branchId, start, end).map { row ->
            // A ledger row is never dropped: that would make the report's totals disagree with the
            // ledger without saying so. An ingredient missing from the catalog gets an empty name
            // (the client shows a localized "unnamed" label): an id is never text for a person.
            val name = namesById[row.ingredientId] ?: "".also {
                logger.warn("stock ledger references ingredient {} which is not in tenant {}'s catalog", row.ingredientId, tenantId)
            }
            ReconciliationRowDto(
                ingredientId = row.ingredientId.toString(),
                name = name,
                openingGrams = row.openingGrams,
                purchasedGrams = row.purchasedGrams,
                decrementedGrams = row.decrementedGrams,
                adjustedGrams = row.adjustedGrams,
                closingGrams = row.closingGrams,
                batchCount = row.batchCount
            )
        }
        val batchCount = stockMovementRepository.countDecrementBatches(tenantId, branchId, start, end)
        return ServiceOutcome.Success(ReconciliationReportDto(batchCount = batchCount, rows = rows.sortedBy { it.name }))
    }

    override suspend fun listMovements(
        tenantId: Uuid,
        branchId: Uuid?,
        ingredientId: Uuid?,
        from: Instant?,
        to: Instant?,
        limit: Int,
        offset: Int,
        principalBranchId: Uuid?
    ): ServiceOutcome<List<StockMovementDto>> {
        if (branchId != null) {
            branchForbidden(branchId, principalBranchId)?.let { return it }
        } else if (principalBranchId != null) {
            // Branch-scoped principal omitting `branchId` still only sees their own branch.
            return listMovements(tenantId, principalBranchId, ingredientId, from, to, limit, offset, principalBranchId)
        }

        val movements = stockMovementRepository.listMovements(tenantId, branchId, ingredientId, from, to, limit, offset)
        return ServiceOutcome.Success(movements.map { it.toDto() })
    }

    override suspend fun appendMovement(
        tenantId: Uuid,
        createdBy: Uuid,
        request: CreateStockMovementRequest,
        principalBranchId: Uuid?
    ): ServiceOutcome<CreateStockMovementResponse> {
        val id = runCatching { Uuid.parse(request.id) }.getOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_ID", "id must be a valid uuid")
        val branchId = runCatching { Uuid.parse(request.branchId) }.getOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_BRANCH", "branchId must be a valid uuid")
        val ingredientId = runCatching { Uuid.parse(request.ingredientId) }.getOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_INGREDIENT", "ingredientId must be a valid uuid")

        branchForbidden(branchId, principalBranchId)?.let { return it }

        // I5 is the manual/purchase append path only -- FORMULA_DECREMENT only arrives via sync
        // push with a `sourceBatchId` (02 §3 I5, §4.1).
        val kind = when (request.kind) {
            "PURCHASE" -> StockMovementKind.PURCHASE
            "MANUAL_ADJUSTMENT" -> StockMovementKind.MANUAL_ADJUSTMENT
            else -> return ServiceOutcome.Invalid("INVALID_KIND", "kind must be PURCHASE or MANUAL_ADJUSTMENT")
        }

        val result = stockMovementRepository.append(
            id = id,
            tenantId = tenantId,
            branchId = branchId,
            ingredientId = ingredientId,
            deltaGrams = request.deltaGrams,
            kind = kind,
            sourceBatchId = null,
            note = request.note,
            createdBy = createdBy,
            occurredAt = Instant.fromEpochMilliseconds(request.occurredAt)
        )
        return ServiceOutcome.Success(CreateStockMovementResponse(result.value.serverSeq))
    }

    override suspend fun productStockLevels(
        tenantId: Uuid,
        branchId: Uuid,
        principalBranchId: Uuid?
    ): ServiceOutcome<List<ProductStockLevelDto>> {
        branchForbidden(branchId, principalBranchId)?.let { return it }

        // Every Almacén product has stock from the moment it exists, whether or not it has a movement.
        val products = catalogRepository.listProducts(tenantId, includeInactive = true)
            .filter { it.origin == ProductOrigin.ALMACEN }
        val figures = stockCalculator.figures(tenantId, branchId, products)
        val levels = products.map { product ->
            val stock = figures.getValue(product.id)
            ProductStockLevelDto(
                productId = product.id.toString(),
                name = product.name,
                receivedUnits = stock.receivedUnits,
                soldUnits = stock.soldUnits,
                currentUnits = stock.currentUnits,
                trackedSince = stock.trackedSince.toEpochMilliseconds(),
                lowStockThresholdUnits = product.lowStockThresholdUnits,
                lowStock = product.lowStockThresholdUnits?.let { stock.currentUnits <= it } ?: false,
                active = product.active
            )
        }
        return ServiceOutcome.Success(levels.sortedBy { it.name })
    }

    override suspend fun setProductLowStockThreshold(
        tenantId: Uuid,
        productId: Uuid,
        thresholdUnits: Long?
    ): ServiceOutcome<ProductLowStockThresholdDto> {
        val product = catalogRepository.findProductById(productId)
        if (product == null || product.tenantId != tenantId) return ServiceOutcome.NotFound
        if (product.origin != ProductOrigin.ALMACEN) return NOT_ALMACEN
        if (thresholdUnits != null && thresholdUnits < 0) {
            return ServiceOutcome.Invalid("INVALID_THRESHOLD", "thresholdUnits must not be negative")
        }
        val updated = catalogRepository.setProductLowStockThreshold(productId, thresholdUnits) ?: return ServiceOutcome.NotFound
        return ServiceOutcome.Success(ProductLowStockThresholdDto(updated.id.toString(), updated.lowStockThresholdUnits))
    }

    override suspend fun listProductMovements(
        tenantId: Uuid,
        branchId: Uuid?,
        productId: Uuid?,
        limit: Int,
        offset: Int,
        principalBranchId: Uuid?
    ): ServiceOutcome<List<ProductStockMovementDto>> {
        if (branchId != null) {
            branchForbidden(branchId, principalBranchId)?.let { return it }
        } else if (principalBranchId != null) {
            return listProductMovements(tenantId, principalBranchId, productId, limit, offset, principalBranchId)
        }

        val movements = productStockRepository.listMovements(tenantId, branchId, productId, limit, offset)
        return ServiceOutcome.Success(movements.map { it.toDto() })
    }

    override suspend fun appendProductMovement(
        tenantId: Uuid,
        createdBy: Uuid,
        request: CreateProductStockMovementRequest,
        principalBranchId: Uuid?
    ): ServiceOutcome<ProductStockMovementDto> {
        val id = runCatching { Uuid.parse(request.id) }.getOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_ID", "id must be a valid uuid")
        val branchId = runCatching { Uuid.parse(request.branchId) }.getOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_BRANCH", "branchId must be a valid uuid")
        val productId = runCatching { Uuid.parse(request.productId) }.getOrNull()
            ?: return ServiceOutcome.Invalid("INVALID_PRODUCT", "productId must be a valid uuid")

        branchForbidden(branchId, principalBranchId)?.let { return it }

        val product = catalogRepository.findProductById(productId)
        if (product == null || product.tenantId != tenantId) return ServiceOutcome.NotFound
        if (product.origin != ProductOrigin.ALMACEN) return NOT_ALMACEN
        if (request.deltaUnits == 0L) return ServiceOutcome.Invalid("INVALID_DELTA", "deltaUnits must not be zero")

        val kind = when (request.kind) {
            "PURCHASE" -> ProductStockMovementKind.PURCHASE
            "MANUAL_ADJUSTMENT" -> ProductStockMovementKind.MANUAL_ADJUSTMENT
            else -> return ServiceOutcome.Invalid("INVALID_KIND", "kind must be PURCHASE or MANUAL_ADJUSTMENT")
        }
        if (kind == ProductStockMovementKind.PURCHASE && request.deltaUnits < 0) {
            return ServiceOutcome.Invalid("INVALID_DELTA", "a purchase can only add units")
        }

        val result = productStockRepository.append(
            id = id,
            tenantId = tenantId,
            branchId = branchId,
            productId = productId,
            deltaUnits = request.deltaUnits,
            kind = kind,
            note = request.note,
            createdBy = createdBy,
            occurredAt = Instant.fromEpochMilliseconds(request.occurredAt)
        )
        return ServiceOutcome.Success(result.value.toDto())
    }

    private fun branchForbidden(branchId: Uuid, principalBranchId: Uuid?): ServiceOutcome.Invalid? =
        if (principalBranchId != null && principalBranchId != branchId) {
            ServiceOutcome.Invalid("BRANCH_FORBIDDEN", "principal is scoped to a different branch")
        } else {
            null
        }

    private fun Ingredient.toDto() = IngredientDto(id.toString(), name, lowStockThresholdGrams, active)

    private fun StockMovement.toDto() = StockMovementDto(
        id = id.toString(),
        branchId = branchId.toString(),
        ingredientId = ingredientId.toString(),
        deltaGrams = deltaGrams,
        kind = kind.name,
        sourceBatchId = sourceBatchId?.toString(),
        note = note,
        createdBy = createdBy?.toString(),
        occurredAt = occurredAt.toEpochMilliseconds(),
        serverSeq = serverSeq
    )

    private fun ProductStockMovement.toDto() = ProductStockMovementDto(
        id = id.toString(),
        branchId = branchId.toString(),
        productId = productId.toString(),
        deltaUnits = deltaUnits,
        kind = kind.name,
        note = note,
        createdBy = createdBy?.toString(),
        occurredAt = occurredAt.toEpochMilliseconds()
    )
}
