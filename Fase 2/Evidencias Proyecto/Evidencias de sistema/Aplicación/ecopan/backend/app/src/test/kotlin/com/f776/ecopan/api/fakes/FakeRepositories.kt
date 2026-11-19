package com.f776.ecopan.api.fakes

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.api.core.storage.ObjectStorage
import com.f776.ecopan.api.core.storage.StoredBlob
import com.f776.ecopan.api.core.storage.UploadGrant
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.CatalogRepository
import com.f776.ecopan.data.database.repository.OpeningCount
import com.f776.ecopan.data.database.repository.IngredientRepository
import com.f776.ecopan.data.database.repository.ProductionBatchRepository
import com.f776.ecopan.data.database.repository.RefreshTokenRepository
import com.f776.ecopan.data.database.repository.RolePermissionRepository
import com.f776.ecopan.data.database.repository.SaleRepository
import com.f776.ecopan.data.database.repository.SeedRevisionRepository
import com.f776.ecopan.data.database.repository.ProductStockRepository
import com.f776.ecopan.data.database.repository.StockMovementRepository
import com.f776.ecopan.data.database.repository.SyncPullRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.UserRepository
import com.f776.ecopan.data.database.repository.model.Branch
import com.f776.ecopan.data.database.repository.model.Formula
import com.f776.ecopan.data.database.repository.model.FormulaLine
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.Ingredient
import com.f776.ecopan.data.database.repository.model.IngredientReconciliation
import com.f776.ecopan.data.database.repository.model.IngredientStock
import com.f776.ecopan.data.database.repository.model.Product
import com.f776.ecopan.data.database.repository.model.ProductionBatch
import com.f776.ecopan.data.database.repository.model.PullRow
import com.f776.ecopan.data.database.repository.model.RefreshTokenRecord
import com.f776.ecopan.data.database.repository.model.Sale
import com.f776.ecopan.data.database.repository.model.SaleLine
import com.f776.ecopan.data.database.repository.model.SaleInput
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import com.f776.ecopan.data.database.repository.model.ProductStockMovement
import com.f776.ecopan.data.database.repository.model.ProductStockSum
import com.f776.ecopan.data.database.repository.model.StockMovement
import com.f776.ecopan.data.database.repository.model.SyncPullPage
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.database.repository.model.User
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import com.f776.ecopan.data.domain.SyncEntityType
import com.f776.ecopan.data.messagebroker.ProductionBatchBroker
import com.f776.ecopan.data.messagebroker.model.ProductionBatchMessage
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeTenantRepository : TenantRepository {
    val tenants = mutableMapOf<Uuid, Tenant>()

    fun seed(tenant: Tenant) {
        tenants[tenant.id] = tenant
    }

    override suspend fun create(id: Uuid, slug: String, name: String, currencyCode: String, enabledModules: Set<Module>): Tenant {
        val tenant = Tenant(id, slug, name, currencyCode, enabledModules, Clock.System.now())
        tenants[id] = tenant
        return tenant
    }

    override suspend fun findById(id: Uuid): Tenant? = tenants[id]

    override suspend fun findBySlug(slug: String): Tenant? = tenants.values.find { it.slug == slug }
    override suspend fun listAll(): List<Tenant> = tenants.values.toList()

    override suspend fun updateEnabledModules(id: Uuid, enabledModules: Set<Module>): Tenant? =
        tenants[id]?.copy(enabledModules = enabledModules)?.also { tenants[id] = it }

    override suspend fun enabledModules(id: Uuid): Set<Module> = tenants[id]?.enabledModules ?: emptySet()
}

class FakeUserRepository : UserRepository {
    val users = mutableMapOf<Uuid, User>()

    fun seed(user: User) {
        users[user.id] = user
    }

    override suspend fun create(
        id: Uuid, tenantId: Uuid, branchId: Uuid?, username: String, passwordHash: String,
        pinHash: String?, displayName: String, role: Role
    ): User {
        val user = User(id, tenantId, branchId, username, passwordHash, pinHash, displayName, role, true, 1, Clock.System.now())
        users[id] = user
        return user
    }

    override suspend fun findById(id: Uuid): User? = users[id]

    override suspend fun findByTenantAndUsername(tenantId: Uuid, username: String): User? =
        users.values.find { it.tenantId == tenantId && it.username == username }

    override suspend fun listByTenant(tenantId: Uuid): List<User> = users.values.filter { it.tenantId == tenantId }

    override suspend fun update(
        id: Uuid, passwordHash: String?, pinHash: String?, displayName: String?, role: Role?,
        branchId: Uuid?, clearBranchId: Boolean, active: Boolean?
    ): User? {
        val current = users[id] ?: return null
        val updated = current.copy(
            passwordHash = passwordHash ?: current.passwordHash,
            pinHash = pinHash ?: current.pinHash,
            displayName = displayName ?: current.displayName,
            role = role ?: current.role,
            branchId = if (clearBranchId) null else (branchId ?: current.branchId),
            active = active ?: current.active,
            serverSeq = current.serverSeq + 1
        )
        users[id] = updated
        return updated
    }
}

class FakeBranchRepository : BranchRepository {
    val branches = mutableMapOf<Uuid, Branch>()

    fun seed(branch: Branch) {
        branches[branch.id] = branch
    }

    override suspend fun create(id: Uuid, tenantId: Uuid, name: String): Branch {
        val branch = Branch(id, tenantId, name, true, 1, Clock.System.now())
        branches[id] = branch
        return branch
    }

    override suspend fun findById(id: Uuid): Branch? = branches[id]

    override suspend fun listByTenant(tenantId: Uuid, includeInactive: Boolean): List<Branch> =
        branches.values.filter { it.tenantId == tenantId && (includeInactive || it.active) }

    override suspend fun update(id: Uuid, name: String?, active: Boolean?): Branch? {
        val current = branches[id] ?: return null
        val updated = current.copy(name = name ?: current.name, active = active ?: current.active, serverSeq = current.serverSeq + 1)
        branches[id] = updated
        return updated
    }
}

class FakeRefreshTokenRepository : RefreshTokenRepository {
    val tokens = java.util.concurrent.ConcurrentHashMap<Uuid, RefreshTokenRecord>()
    private val lock = Any()

    /** When set, [rotate] parks until [releaseRotationsWhenWaiting], so a test can line two up. */
    @Volatile var holdRotations = false
    private val waiting = java.util.concurrent.atomic.AtomicInteger(0)
    private val gate = kotlinx.coroutines.CompletableDeferred<Unit>()

    suspend fun releaseRotationsWhenWaiting(count: Int) {
        while (waiting.get() < count) kotlinx.coroutines.delay(5)
        gate.complete(Unit)
    }

    override suspend fun create(
        id: Uuid, userId: Uuid, tokenHash: String, deviceId: String?, deviceName: String?,
        branchId: Uuid?, issuedAt: Instant, expiresAt: Instant, familyId: Uuid
    ): RefreshTokenRecord {
        val record = RefreshTokenRecord(id, userId, tokenHash, deviceId, deviceName, branchId, issuedAt, expiresAt, null, familyId)
        tokens[id] = record
        return record
    }

    override suspend fun findByTokenHash(tokenHash: String): RefreshTokenRecord? =
        tokens.values.find { it.tokenHash == tokenHash }

    override suspend fun revoke(id: Uuid) {
        tokens.computeIfPresent(id) { _, it -> it.copy(revokedAt = Clock.System.now()) }
    }

    override suspend fun revokeAllForUser(userId: Uuid) {
        tokens.replaceAll { _, record -> if (record.userId == userId) record.copy(revokedAt = Clock.System.now()) else record }
    }

    override suspend fun revokeFamily(familyId: Uuid) {
        tokens.replaceAll { _, record ->
            if (record.familyId == familyId && record.revokedAt == null) record.copy(revokedAt = Clock.System.now()) else record
        }
    }

    override suspend fun rotate(oldId: Uuid, newId: Uuid, newTokenHash: String, issuedAt: Instant, expiresAt: Instant): RefreshTokenRecord? {
        if (holdRotations) {
            waiting.incrementAndGet()
            gate.await()
        }
        // The conditional update: only the caller that sees it unrevoked wins.
        synchronized(lock) {
            val old = tokens[oldId]?.takeIf { it.revokedAt == null } ?: return null
            tokens[oldId] = old.copy(revokedAt = issuedAt)
            val next = old.copy(id = newId, tokenHash = newTokenHash, issuedAt = issuedAt, expiresAt = expiresAt, revokedAt = null)
            tokens[newId] = next
            return next
        }
    }
}

class FakeRolePermissionRepository(private val grid: Map<Role, Set<Permission>>) : RolePermissionRepository {
    override suspend fun loadAll(): Map<Role, Set<Permission>> = grid
}

class FakeIngredientRepository : IngredientRepository {
    val ingredients = mutableMapOf<Uuid, Ingredient>()
    private val seq = AtomicLong(0)

    override suspend fun create(id: Uuid, tenantId: Uuid, name: String, lowStockThresholdGrams: Long?): Ingredient {
        val ingredient = Ingredient(id, tenantId, name, lowStockThresholdGrams, true, seq.incrementAndGet(), Clock.System.now(), Clock.System.now())
        ingredients[id] = ingredient
        return ingredient
    }

    override suspend fun findById(id: Uuid): Ingredient? = ingredients[id]

    override suspend fun listByTenant(tenantId: Uuid, includeInactive: Boolean): List<Ingredient> =
        ingredients.values.filter { it.tenantId == tenantId && (includeInactive || it.active) }

    override suspend fun update(id: Uuid, name: String?, lowStockThresholdGrams: Long?, active: Boolean?): Ingredient? {
        val current = ingredients[id] ?: return null
        val updated = current.copy(
            name = name ?: current.name,
            lowStockThresholdGrams = lowStockThresholdGrams ?: current.lowStockThresholdGrams,
            active = active ?: current.active,
            serverSeq = seq.incrementAndGet()
        )
        ingredients[id] = updated
        return updated
    }
}

class FakeCatalogRepository : CatalogRepository {
    /** Where an opening count lands; the real repository writes it in the product's own transaction. */
    var openingLedger: FakeProductStockRepository? = null
    val products = mutableMapOf<Uuid, Product>()
    val formulas = mutableMapOf<Uuid, Formula>()
    private val seq = AtomicLong(0)

    override suspend fun createProduct(
        id: Uuid, tenantId: Uuid, name: String, productType: String, soldBy: SoldBy,
        pricePerKgMinor: Long?, priceMinor: Long?, barcode: String?, imageUrl: String?, origin: ProductOrigin,
        opening: OpeningCount?
    ): Product {
        val product = Product(id, tenantId, name, productType, soldBy, pricePerKgMinor, priceMinor, barcode, imageUrl, true, seq.incrementAndGet(), Clock.System.now(), Clock.System.now(), origin = origin)
        // All or nothing: the product is stored only once its opening count has been.
        opening?.let {
            checkNotNull(openingLedger) { "wire catalog.openingLedger to record opening counts" }.append(
                it.movementId, tenantId, it.branchId, id, it.units, ProductStockMovementKind.OPENING_COUNT, null, it.createdBy, it.occurredAt
            )
        }
        products[id] = product
        return product
    }

    override suspend fun findProductById(id: Uuid): Product? = products[id]

    override suspend fun listProducts(tenantId: Uuid, includeInactive: Boolean): List<Product> =
        products.values.filter { it.tenantId == tenantId && (includeInactive || it.active) }

    override suspend fun updateProduct(
        id: Uuid, name: String?, productType: String?, soldBy: SoldBy?, pricePerKgMinor: Long?,
        priceMinor: Long?, barcode: String?, imageUrl: String?, active: Boolean?, origin: ProductOrigin?
    ): Product? {
        val current = products[id] ?: return null
        val updated = current.copy(
            name = name ?: current.name,
            productType = productType ?: current.productType,
            soldBy = soldBy ?: current.soldBy,
            origin = origin ?: current.origin,
            pricePerKgMinor = pricePerKgMinor ?: current.pricePerKgMinor,
            priceMinor = priceMinor ?: current.priceMinor,
            barcode = barcode ?: current.barcode,
            imageUrl = imageUrl ?: current.imageUrl,
            active = active ?: current.active,
            serverSeq = seq.incrementAndGet()
        )
        products[id] = updated
        return updated
    }

    override suspend fun setProductImageUrl(id: Uuid, imageUrl: String?): Product? {
        val current = products[id] ?: return null
        val updated = current.copy(imageUrl = imageUrl, serverSeq = seq.incrementAndGet())
        products[id] = updated
        return updated
    }

    override suspend fun setProductLowStockThreshold(id: Uuid, thresholdUnits: Long?): Product? {
        val current = products[id] ?: return null
        val updated = current.copy(lowStockThresholdUnits = thresholdUnits, serverSeq = seq.incrementAndGet())
        products[id] = updated
        return updated
    }

    override suspend fun findFormulaByProduct(tenantId: Uuid, productId: Uuid): Formula? =
        formulas.values.find { it.tenantId == tenantId && it.productId == productId }

    override suspend fun listFormulas(tenantId: Uuid, includeInactive: Boolean): List<Formula> =
        formulas.values.filter { it.tenantId == tenantId && (includeInactive || it.active) }

    override suspend fun putFormula(id: Uuid, tenantId: Uuid, productId: Uuid, lines: List<FormulaLine>): Formula {
        val existing = formulas.values.find { it.tenantId == tenantId && it.productId == productId }
        val formula = Formula(existing?.id ?: id, tenantId, productId, true, seq.incrementAndGet(), Clock.System.now(), lines)
        formulas[formula.id] = formula
        return formula
    }
}

class FakeSaleRepository : SaleRepository {
    val sales = mutableMapOf<Uuid, Sale>()
    private val seq = AtomicLong(0)

    /** Thrown by the next write, standing in for a database that is down. */
    var failWith: Exception? = null

    override suspend fun createWithLines(
        id: Uuid, tenantId: Uuid, branchId: Uuid, cashierId: Uuid, soldAt: Instant,
        paymentMethod: PaymentMethod, totalMinor: Long, deviceId: String, lines: List<SaleLineInput>
    ): IngestResult<Sale> {
        failWith?.let { throw it }
        sales[id]?.let { return IngestResult.Duplicate(it) }
        val saleLines = lines.map {
            SaleLine(it.id, id, it.productId, it.soldBy, it.weightGrams, it.quantity, it.unitPriceMinor, it.lineTotalMinor)
        }
        val sale = Sale(id, tenantId, branchId, cashierId, soldAt, paymentMethod, totalMinor, deviceId, seq.incrementAndGet(), Clock.System.now(), saleLines)
        sales[id] = sale
        return IngestResult.Applied(sale)
    }

    override suspend fun createAllWithLines(sales: List<SaleInput>): Int {
        var inserted = 0
        for (input in sales) {
            if (this.sales.containsKey(input.id)) continue
            createWithLines(
                input.id, input.tenantId, input.branchId, input.cashierId, input.soldAt,
                input.paymentMethod, input.totalMinor, input.deviceId, input.lines
            )
            inserted++
        }
        return inserted
    }

    override suspend fun findById(id: Uuid): Sale? = sales[id]

    override suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<Sale> =
        sales.values.filter { it.tenantId == tenantId && it.serverSeq > since }.sortedBy { it.serverSeq }.take(limit)
}

class FakeProductionBatchRepository : ProductionBatchRepository {
    val batches = mutableMapOf<Uuid, ProductionBatch>()
    private val seq = AtomicLong(0)

    override suspend fun create(
        id: Uuid, tenantId: Uuid, branchId: Uuid, bakerId: Uuid, productId: Uuid, mixWeightGrams: Long,
        batchAt: Instant, movementsMinted: Boolean, deviceId: String
    ): IngestResult<ProductionBatch> {
        batches[id]?.let { return IngestResult.Duplicate(it) }
        val batch = ProductionBatch(id, tenantId, branchId, bakerId, productId, mixWeightGrams, batchAt, movementsMinted, DecrementStatus.PENDING, deviceId, seq.incrementAndGet(), Clock.System.now())
        batches[id] = batch
        return IngestResult.Applied(batch)
    }

    override suspend fun findById(id: Uuid): ProductionBatch? = batches[id]

    override suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<ProductionBatch> =
        batches.values.filter { it.tenantId == tenantId && it.serverSeq > since }.sortedBy { it.serverSeq }.take(limit)

    override suspend fun listReceivedSince(tenantId: Uuid, since: Instant): List<ProductionBatch> =
        batches.values.filter { it.tenantId == tenantId && it.receivedAt >= since }.sortedBy { it.serverSeq }

    override suspend fun updateDecrementStatus(id: Uuid, status: DecrementStatus): ProductionBatch? {
        val current = batches[id] ?: return null
        val updated = current.copy(decrementStatus = status)
        batches[id] = updated
        return updated
    }

    override suspend fun completeDecrement(
        id: Uuid,
        status: DecrementStatus,
        movements: List<com.f776.ecopan.data.database.repository.model.DecrementMovement>,
    ): Boolean {
        val current = batches[id]?.takeIf { it.decrementStatus == DecrementStatus.PENDING } ?: return false
        batches[id] = current.copy(decrementStatus = status)
        return true
    }
}

class FakeStockMovementRepository : StockMovementRepository {
    val movements = mutableMapOf<Uuid, StockMovement>()
    private val seq = AtomicLong(0)

    override suspend fun append(
        id: Uuid, tenantId: Uuid, branchId: Uuid, ingredientId: Uuid, deltaGrams: Long, kind: StockMovementKind,
        sourceBatchId: Uuid?, note: String?, createdBy: Uuid?, occurredAt: Instant
    ): IngestResult<StockMovement> {
        movements[id]?.let { return IngestResult.Duplicate(it) }
        if (sourceBatchId != null) {
            movements.values.find { it.sourceBatchId == sourceBatchId && it.ingredientId == ingredientId }?.let {
                return IngestResult.Duplicate(it)
            }
        }
        val movement = StockMovement(id, tenantId, branchId, ingredientId, deltaGrams, kind, sourceBatchId, note, createdBy, occurredAt, seq.incrementAndGet(), Clock.System.now())
        movements[id] = movement
        return IngestResult.Applied(movement)
    }

    override suspend fun findById(id: Uuid): StockMovement? = movements[id]

    override suspend fun sumByIngredient(tenantId: Uuid, branchId: Uuid): List<IngredientStock> =
        movements.values.filter { it.tenantId == tenantId && it.branchId == branchId }
            .groupBy { it.ingredientId }
            .map { (ingredientId, rows) -> IngredientStock(ingredientId, rows.sumOf { it.deltaGrams }) }

    override suspend fun reconcileByIngredient(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): List<IngredientReconciliation> =
        movements.values.filter { it.tenantId == tenantId && it.branchId == branchId && it.occurredAt < to }
            .groupBy { it.ingredientId }
            .map { (ingredientId, rows) ->
                val inWindow = rows.filter { it.occurredAt >= from }
                fun sum(kind: StockMovementKind) = inWindow.filter { it.kind == kind }.sumOf { it.deltaGrams }
                IngredientReconciliation(
                    ingredientId = ingredientId,
                    openingGrams = rows.filter { it.occurredAt < from }.sumOf { it.deltaGrams },
                    purchasedGrams = sum(StockMovementKind.PURCHASE),
                    decrementedGrams = sum(StockMovementKind.FORMULA_DECREMENT),
                    adjustedGrams = sum(StockMovementKind.MANUAL_ADJUSTMENT),
                    batchCount = inWindow.filter { it.kind == StockMovementKind.FORMULA_DECREMENT }.mapNotNull { it.sourceBatchId }.distinct().size
                )
            }

    override suspend fun countDecrementBatches(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): Int =
        movements.values.filter {
            it.tenantId == tenantId && it.branchId == branchId && it.kind == StockMovementKind.FORMULA_DECREMENT &&
                it.occurredAt >= from && it.occurredAt < to
        }.mapNotNull { it.sourceBatchId }.distinct().size

    override suspend fun listMovements(
        tenantId: Uuid, branchId: Uuid?, ingredientId: Uuid?, from: Instant?, to: Instant?, limit: Int, offset: Int
    ): List<StockMovement> = movements.values
        .filter { it.tenantId == tenantId }
        .filter { branchId == null || it.branchId == branchId }
        .filter { ingredientId == null || it.ingredientId == ingredientId }
        .filter { from == null || it.occurredAt >= from }
        .filter { to == null || it.occurredAt <= to }
        .sortedByDescending { it.occurredAt }
        .drop(offset)
        .take(limit)

    override suspend fun listSince(tenantId: Uuid, since: Long, limit: Int): List<StockMovement> =
        movements.values.filter { it.tenantId == tenantId && it.serverSeq > since }.sortedBy { it.serverSeq }.take(limit)
}

/** Stands in for Azure Blob / Azurite: [uploaded] is what "the client" has PUT so far. */
class InMemoryObjectStorage : ObjectStorage {
    /** Keys a client has uploaded to. One added directly reads as a small, valid PNG. */
    val uploaded = mutableSetOf<String>()
    val grants = mutableListOf<String>()
    private val blobs = mutableMapOf<String, StoredBlob>()

    /** A client's direct upload of [size] bytes beginning with [head]. */
    fun put(key: String, size: Long, head: ByteArray) {
        uploaded += key
        blobs[key] = StoredBlob(size, head)
    }

    override fun publicUrl(key: String): String = "http://storage.test/product-images/$key"

    override suspend fun grantUpload(key: String, contentType: String, ttl: Duration): UploadGrant {
        grants += key
        return UploadGrant("${publicUrl(key)}?sig=test", Clock.System.now() + ttl)
    }

    override suspend fun inspect(key: String, headBytes: Int): StoredBlob? {
        if (key !in uploaded) return null
        val blob = blobs[key] ?: StoredBlob(1_000, byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        return StoredBlob(blob.size, blob.head.copyOf(minOf(headBytes, blob.head.size)))
    }

    override suspend fun delete(key: String) {
        uploaded -= key
        blobs -= key
    }

    override suspend fun provisionForDevelopment(corsAllowedOrigins: List<String>) = Unit
}

/** Unit ledger fake; [soldUnits] stands in for the `sale_lines` sum the real impl reads. */
class FakeProductStockRepository : ProductStockRepository {
    val movements = mutableMapOf<Uuid, ProductStockMovement>()
    val soldUnits = mutableMapOf<Uuid, Long>()

    /** Makes every [append] throw, to exercise the all-or-nothing paths. */
    var failAppend: Throwable? = null

    override suspend fun append(
        id: Uuid, tenantId: Uuid, branchId: Uuid, productId: Uuid, deltaUnits: Long, kind: ProductStockMovementKind,
        note: String?, createdBy: Uuid?, occurredAt: Instant
    ): IngestResult<ProductStockMovement> {
        failAppend?.let { throw it }
        movements[id]?.let { return IngestResult.Duplicate(it) }
        val movement = ProductStockMovement(id, tenantId, branchId, productId, deltaUnits, kind, note, createdBy, occurredAt, Clock.System.now())
        movements[id] = movement
        return IngestResult.Applied(movement)
    }

    override suspend fun sumByProduct(tenantId: Uuid, branchId: Uuid): List<ProductStockSum> =
        movements.values.filter { it.tenantId == tenantId && it.branchId == branchId }
            .groupBy { it.productId }
            .map { (productId, rows) ->
                ProductStockSum(
                    productId,
                    rows.sumOf { it.deltaUnits },
                    rows.filter { it.kind == ProductStockMovementKind.OPENING_COUNT }.minOfOrNull { it.receivedAt }
                )
            }

    override suspend fun soldUnitsSince(tenantId: Uuid, branchId: Uuid, sinceByProduct: Map<Uuid, Instant>): Map<Uuid, Long> =
        sinceByProduct.keys.mapNotNull { id -> soldUnits[id]?.let { id to it } }.toMap()

    override suspend fun listMovements(
        tenantId: Uuid, branchId: Uuid?, productId: Uuid?, limit: Int, offset: Int
    ): List<ProductStockMovement> = movements.values
        .filter { it.tenantId == tenantId }
        .filter { branchId == null || it.branchId == branchId }
        .filter { productId == null || it.productId == productId }
        .sortedByDescending { it.occurredAt }
        .drop(offset)
        .take(limit)
}

/** Records every published message instead of touching a real broker (02 §8). */
class FakeProductionBatchBroker : ProductionBatchBroker {
    val sent = mutableListOf<ProductionBatchMessage>()

    override suspend fun publish(message: ProductionBatchMessage) {
        sent += message
    }

    override suspend fun consume(block: suspend (ProductionBatchMessage) -> Unit) {
        // Router tests never consume; this fake is push-only.
    }
}

class FakeSyncPullRepository : SyncPullRepository {
    val rows = mutableListOf<PullRow>()

    override suspend fun pull(
        tenantId: Uuid, since: Long, limit: Int, entityTypes: Set<SyncEntityType>?, branchId: Uuid?
    ): SyncPullPage {
        val fetchLimit = limit + 1
        val candidates = rows
            .filter { branchId == null || (it.data as? com.f776.ecopan.data.database.repository.model.Forecast)?.let { f -> f.branchId == branchId } ?: true }
            .filter { entityTypes == null || SyncEntityType.valueOf(it.entityType) in entityTypes }
            .filter { it.serverSeq > since }
            .sortedBy { it.serverSeq }
            .take(fetchLimit)
        return SyncPullPage(rows = candidates.take(limit), hasMore = candidates.size > limit)
    }
}

class FakeSeedRevisionRepository : SeedRevisionRepository {
    val applied = mutableMapOf<String, Int>()

    override suspend fun appliedRevision(profile: String): Int? = applied[profile]

    override suspend fun recordApplied(profile: String, revision: Int) {
        applied[profile] = revision
    }
}

/**
 * The one complete in-memory [ForecastRepository] for app tests (also reusable by other branches'
 * tests that need a `ForecastRepository` stand-in): [rows] is what `listByBranch` serves, filtered
 * the way the real one does, and `upsertAll` stores like the real one (skipping past days).
 */
class FakeForecastRepository(initial: List<com.f776.ecopan.data.database.repository.model.Forecast> = emptyList()) :
    com.f776.ecopan.data.database.repository.ForecastRepository {
    val rows = initial.toMutableList()

    override suspend fun upsertAll(
        tenantId: Uuid,
        branchId: Uuid,
        rows: List<com.f776.ecopan.data.database.repository.ForecastUpsert>,
        notBefore: kotlinx.datetime.LocalDate
    ): com.f776.ecopan.data.database.repository.ForecastUpsertResult {
        val current = rows.filter { it.forecastDate >= notBefore }
        current.forEach { row ->
            this.rows.removeAll { it.branchId == branchId && it.productId == row.productId && it.forecastDate == row.forecastDate }
            this.rows += com.f776.ecopan.data.database.repository.model.Forecast(
                Uuid.random(), tenantId, branchId, row.productId, "P-${row.productId}", "Pan", row.forecastDate,
                row.predictedGrams, row.predictedUnits, row.modelVersion, Clock.System.now(), this.rows.size.toLong() + 1
            )
        }
        return com.f776.ecopan.data.database.repository.ForecastUpsertResult(current.size, rows.size - current.size, 0)
    }

    override suspend fun listByBranch(
        tenantId: Uuid,
        branchId: Uuid,
        from: kotlinx.datetime.LocalDate,
        to: kotlinx.datetime.LocalDate,
        productType: String?,
        productId: Uuid?
    ) = rows.filter {
        it.tenantId == tenantId && it.branchId == branchId && it.forecastDate in from..to &&
            (productId == null || it.productId == productId) &&
            (productType == null || com.f776.ecopan.data.domain.ProductTypeKey.of(it.productType) ==
                com.f776.ecopan.data.domain.ProductTypeKey.of(productType))
    }
}

/** Records forecast-refresh requests; [onPublish] lets a test observe the world at publish time. */
class RecordingForecastRefreshBroker(
    private val onPublish: suspend (com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested) -> Unit = {}
) : com.f776.ecopan.data.messagebroker.ForecastRefreshBroker {
    val published = mutableListOf<com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested>()
    override suspend fun publish(message: com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested) {
        onPublish(message)
        published += message
    }
    override suspend fun consume(block: suspend (com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested) -> Unit) = Unit
}
