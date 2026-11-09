package com.f776.ecopan.data.sync.unittest

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.inventory.sync.StockMovementSyncApplier
import com.f776.ecopan.data.production.BatchReadyBus
import com.f776.ecopan.data.production.sync.ProductionBatchSyncApplier
import com.f776.ecopan.domain.catalog.Product
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.catalog.ProductUpsert
import com.f776.ecopan.domain.production.BatchReadyAnnouncement
import com.f776.ecopan.data.sync.CursorStore
import com.f776.ecopan.data.sync.pull.PullWorker
import com.f776.ecopan.data.sync.push.PushWorker
import com.f776.ecopan.data.sync.unittest.fake.FakeOutboxDao
import com.f776.ecopan.data.sync.unittest.fake.FakePreferencesDataStore
import com.f776.ecopan.data.sync.unittest.fake.FakeServer
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncAuthProvider
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncTransport
import com.f776.ecopan.domain.sync.wire.MutationDto
import com.f776.ecopan.domain.sync.wire.PushRequest
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import com.f776.unigrid.core.database.AppDatabase
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Plan 06 item 10's end-to-end acceptance test: "a batch logged in airplane mode appears in POS's
 * front-of-house feed after both devices reconnect".
 *
 * `ProductionRepositoryImpl.logBatch` enqueues **two** outbox rows per batch when
 * `INVENTORY_FORMULAS` is entitled and a formula resolves: one `PRODUCTION_BATCH` mutation (the
 * batch header) and one `STOCK_MOVEMENT` mutation per derived ingredient (the formula-decrement
 * side effect, 06 §4 client-primary minting). Both halves are covered here, each against the real
 * applier rather than a stand-in:
 *
 * - `STOCK_MOVEMENT` → `StockMovementSyncApplier` (`:data:inventory:impl`): the decrement reaches
 *   device B's stock ledger exactly once, surviving both a normal offline/reconnect cycle and an
 *   app-killed-before-ack crash/retry cycle. This is what determines whether Stock Glance (§7.3)
 *   and dashboard reconciliation see the right numbers across devices.
 * - `PRODUCTION_BATCH` → `ProductionBatchSyncApplier` (`:data:production:impl`): the batch header
 *   lands in device B's `production_batches` cache **and** is published to `BatchReadyBus`, which
 *   is what `ProductionRepository.batchReadyFeed()` — and therefore POS's batch-ready banner
 *   (05 §3 item 9) — reads from.
 *
 * **Scope note:** this covers the *durable* path only — offline capture, reconnect, pull, apply.
 * 06 §6's live SSE push (`BatchReadySseListener`, covered by its own tests in
 * `:data:production:impl`) is what makes the banner appear promptly when the device is online; it
 * writes nothing, so it is deliberately absent here. This test is the guarantee that survives
 * having been offline, which is the one the acceptance criterion is actually about.
 */
class EndToEndBatchSyncTest {

    private val server = FakeServer()
    private val branchId = Uuid.random()
    private val tenantId = Uuid.random()
    private val bakerId = Uuid.random()
    private val marraquetaId = Uuid.random()

    // -- device A (offline baker phone): FakeOutboxDao, mirrors PushWorkerTest's style exactly --
    private val deviceAOutbox = FakeOutboxDao()
    private val deviceATransport = FakeSyncTransport(server)
    private val deviceAAuth = FakeSyncAuthProvider()

    private fun deviceAPushWorker() = PushWorker(
        outboxDao = deviceAOutbox,
        transport = deviceATransport,
        authProvider = deviceAAuth,
        deviceIdProvider = { "baker-phone-A" },
    )

    // -- device B (POS/other device): real in-memory Room + the real StockMovementSyncApplier --
    private val deviceBDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()
    private val deviceBTransport = FakeSyncTransport(server)
    private val deviceBCursor = CursorStore(FakePreferencesDataStore())
    private val deviceBTenantRepository = FixedTenantRepository(tenantId)
    private val deviceBApplier = StockMovementSyncApplier(deviceBDatabase.stockMovementDao(), deviceBTenantRepository)
    private val deviceBBatchReadyBus = BatchReadyBus()
    private val deviceBProductRepository = FixedProductRepository(tenantId)
    private val deviceBBatchApplier = ProductionBatchSyncApplier(
        productionBatchDao = deviceBDatabase.productionBatchDao(),
        productRepository = deviceBProductRepository,
        tenantRepository = deviceBTenantRepository,
        batchReadyBus = deviceBBatchReadyBus,
    )

    private fun deviceBPullWorker() = PullWorker(
        transport = deviceBTransport,
        cursorStore = deviceBCursor,
        database = deviceBDatabase,
        appliers = listOf(deviceBApplier, deviceBBatchApplier),
    )

    @AfterTest
    fun closeDatabase() {
        deviceBDatabase.close()
    }

    /** Formula-decrement movement payload shape `StockMovementSyncApplier` actually decodes (its
     *  private `StockMovementPullPayload`) -- `occurredAt` is epoch millis on the wire, matching
     *  the backend's real pull shape, not the ISO string `StockMovementPushPayload` serializes on
     *  push (the backend is what translates between the two; this test's `FakeServer` deliberately
     *  echoes payloads verbatim, so it must be handed the pull-side shape directly, same as
     *  `PullWorkerTest` hands `FakeConfigApplier`/`FakeFactApplier` their own payload shape). */
    private fun stockMovementPullPayload(
        movementId: Uuid,
        ingredientId: Uuid,
        deltaGrams: Long,
        sourceBatchId: Uuid,
    ) = buildJsonObject {
        put("id", movementId.toString())
        put("branchId", branchId.toString())
        put("ingredientId", ingredientId.toString())
        put("deltaGrams", deltaGrams)
        put("kind", "FORMULA_DECREMENT")
        put("sourceBatchId", sourceBatchId.toString())
        put("occurredAt", 1_753_000_000_000L)
    }

    /** Mirrors the backend's `ProductionBatchPullPayload` (`batchAt` = epoch millis), the shape
     *  `ProductionBatchSyncApplier` decodes. Same push-vs-pull-shape note as above applies. */
    private fun productionBatchPullPayload(batchId: Uuid, mixWeightGrams: Long = 12_000L) = buildJsonObject {
        put("id", batchId.toString())
        put("branchId", branchId.toString())
        put("bakerId", bakerId.toString())
        put("productId", marraquetaId.toString())
        put("mixWeightGrams", mixWeightGrams)
        put("batchAt", 1_753_000_000_000L)
        put("movementsMinted", true)
        put("decrementStatus", "APPLIED")
    }

    @Test
    fun batchLoggedOfflineSyncsStockMovementToOtherDevice() = runTest {
        val batchId = Uuid.random()
        val movementId = Uuid.random()
        val flourId = Uuid.random()
        val deltaGrams = -5_952_000L // 5.952 kg flour consumed, worked example (06 §11)

        // Baker logs a batch offline: logBatch's atomic write enqueues both outbox rows in the
        // same local transaction. No push is attempted while "offline" -- this row just sits PENDING.
        deviceAOutbox.insert(
            OutboxEntity(
                mutationId = movementId,
                entityType = "STOCK_MOVEMENT",
                opType = "CREATE",
                payload = stockMovementPullPayload(movementId, flourId, deltaGrams, batchId).toString(),
                createdAt = Clock.System.now(),
                state = OutboxState.PENDING,
            ),
        )
        // The PRODUCTION_BATCH sibling row from the same logBatch call.
        deviceAOutbox.insert(
            OutboxEntity(
                mutationId = batchId,
                entityType = "PRODUCTION_BATCH",
                opType = "CREATE",
                payload = productionBatchPullPayload(batchId).toString(),
                createdAt = Clock.System.now(),
                state = OutboxState.PENDING,
            ),
        )
        assertEquals(2, deviceAOutbox.observePendingCount().first(), "both rows sit offline, nothing pushed yet")

        // "Reconnect": push succeeds against the shared FakeServer.
        val pushOutcome = deviceAPushWorker().drainAll()
        assertIs<com.f776.ecopan.data.sync.push.PushOutcome.Done>(pushOutcome)
        assertEquals(2, pushOutcome.pushed)
        assertTrue(deviceAOutbox.rows.isEmpty(), "device A's outbox fully drained after reconnect")
        assertEquals(setOf(movementId.toString(), batchId.toString()), server.appliedMutationIds)

        // Device B (a fresh device, same tenant/branch) pulls and applies via the REAL StockMovementSyncApplier.
        deviceBPullWorker().pullAll()

        val levels = deviceBDatabase.stockMovementDao().observeDeltaSumsByBranch(branchId).first()
        assertEquals(1, levels.size, "exactly one ingredient touched")
        assertEquals(flourId, levels.single().ingredientId)
        assertEquals(deltaGrams, levels.single().totalDeltaGrams, "decrement applied exactly once, not zero, not doubled")
    }

    @Test
    fun idempotentRePushAfterAppKillProducesNoDuplicateMovement() = runTest {
        val batchId = Uuid.random()
        val movementId = Uuid.random()
        val flourId = Uuid.random()
        val deltaGrams = -5_952_000L
        val payloadJson = stockMovementPullPayload(movementId, flourId, deltaGrams, batchId).toString()

        deviceAOutbox.insert(
            OutboxEntity(
                mutationId = movementId,
                entityType = "STOCK_MOVEMENT",
                opType = "CREATE",
                payload = payloadJson,
                createdAt = Clock.System.now(),
                state = OutboxState.PENDING,
            ),
        )

        // First attempt: the row is marked IN_FLIGHT and the request really reaches the server
        // (matches crashRecoveryRevertsInFlightToPending's premise in PushWorkerTest) -- then the
        // app is killed before the ack is ever processed by PushWorker.applyResults.
        deviceAOutbox.markInFlight(listOf(movementId))
        server.push(
            PushRequest(
                deviceId = "baker-phone-A",
                mutations = listOf(
                    MutationDto(
                        mutationId = movementId.toString(),
                        entityType = "STOCK_MOVEMENT",
                        opType = "CREATE",
                        clientTimestamp = 0L,
                        payload = Json.parseToJsonElement(payloadJson),
                    ),
                ),
            ),
        )
        // -- app killed here --

        // Relaunch: crash recovery reverts IN_FLIGHT rows to PENDING (03 T7, already covered by
        // PushWorkerTest.crashRecoveryRevertsInFlightToPending) before resuming the drain.
        deviceAOutbox.revertAllInFlightToPending()
        assertEquals(OutboxState.PENDING, deviceAOutbox.stateOf(movementId))

        // Re-push with a brand-new PushWorker instance (fresh app process) -- server must answer
        // DUPLICATE, not apply it a second time.
        val outcome = deviceAPushWorker().drainAll()
        assertIs<com.f776.ecopan.data.sync.push.PushOutcome.Done>(outcome)
        assertTrue(deviceAOutbox.rows.isEmpty(), "resumed push acks (DUPLICATE) and clears the row")
        assertEquals(setOf(movementId.toString()), server.appliedMutationIds, "server only ever counted it once")

        // Device B pulls once -- the movement is present exactly once, no double-counted delta.
        deviceBPullWorker().pullAll()
        val levels = deviceBDatabase.stockMovementDao().observeDeltaSumsByBranch(branchId).first()
        assertEquals(1, levels.size)
        assertEquals(deltaGrams, levels.single().totalDeltaGrams, "no duplicate movement despite the re-push")

        // A second pull (e.g. a later sync cycle) must not re-apply or double the delta either --
        // insert-if-unseen on the fact applier, exercised again from the pull side.
        deviceBPullWorker().pullAll()
        val levelsAfterSecondPull = deviceBDatabase.stockMovementDao().observeDeltaSumsByBranch(branchId).first()
        assertEquals(deltaGrams, levelsAfterSecondPull.single().totalDeltaGrams, "still exactly once after a repeat pull")
    }

    /**
     * 06 §12 item 10's acceptance criterion, in its own words: the batch itself — not just its
     * stock side effect — reaches the other device, lands in its `production_batches` cache, and
     * is published on the feed POS's batch-ready banner reads.
     */
    @Test
    fun batchLoggedOfflineAppearsInAnotherDevicesBatchFeed() = runTest {
        val batchId = Uuid.random()
        val mixWeightGrams = 12_000L

        // Device B is watching its batch-ready feed (as POS's ViewModel does) before anything syncs.
        val banners = mutableListOf<BatchReadyAnnouncement>()
        val collector = launch { deviceBBatchReadyBus.feed.collect { banners += it } }
        runCurrent()

        deviceAOutbox.insert(
            OutboxEntity(
                mutationId = batchId,
                entityType = "PRODUCTION_BATCH",
                opType = "CREATE",
                payload = productionBatchPullPayload(batchId, mixWeightGrams).toString(),
                createdAt = Clock.System.now(),
                state = OutboxState.PENDING,
            ),
        )
        assertTrue(banners.isEmpty(), "nothing crosses devices while the baker is offline")

        assertIs<com.f776.ecopan.data.sync.push.PushOutcome.Done>(deviceAPushWorker().drainAll())
        deviceBPullWorker().pullAll()
        runCurrent()

        // Persisted: device B's own history/read-model sees it, no baker input required.
        val today = 1_753_000_000_000L
        val cached = deviceBDatabase.productionBatchDao()
            .observeTodaysBatches(branchId, today - 1, today + 1)
            .first()
        assertEquals(1, cached.size)
        assertEquals(batchId, cached.single().id)
        assertEquals(mixWeightGrams, cached.single().mixWeightGrams)
        assertEquals(
            "Pan",
            cached.single().productTypeName,
            "productTypeName isn't on the wire -- resolved from the local product cache",
        )
        assertEquals(26, cached.single().estimatedUnits, "12 kg * 2200/1e6 ratio, recomputed locally")

        // Announced: this is the flow ProductionRepository.batchReadyFeed() -- and therefore POS's
        // batch-ready banner -- reads from.
        assertEquals(listOf(batchId), banners.map { it.batchId })
        assertEquals(mixWeightGrams, banners.single().mixWeightGrams)
        assertEquals("Pan", banners.single().productTypeName)

        // A repeat pull must not re-announce it -- the banner would flash a second time for a
        // batch the cashier already dismissed.
        deviceBPullWorker().pullAll()
        runCurrent()
        assertEquals(1, banners.size, "insert-if-unseen also gates the announcement, not just the row")
        assertEquals(1, deviceBDatabase.productionBatchDao().observeTodaysBatches(branchId, today - 1, today + 1).first().size)

        collector.cancel()
    }

    /** Minimal [com.f776.ecopan.domain.catalog.ProductRepository] stand-in -- only [findById] is
     *  exercised by [ProductionBatchSyncApplier], to resolve the two fields the wire omits. */
    private inner class FixedProductRepository(private val tenantId: Uuid) : ProductRepository {
        override fun observeActiveProducts(): Flow<List<Product>> = MutableStateFlow(emptyList())

        override suspend fun upsertProducts(products: List<ProductUpsert>) = Unit

        override suspend fun findByBarcode(barcode: String): Product? = null

        override suspend fun findById(id: Uuid): Product? = if (id != marraquetaId) {
            null
        } else {
            Product(
                id = marraquetaId,
                tenantId = tenantId,
                name = "Marraqueta",
                productType = "Pan",
                soldBy = SoldBy.WEIGHT,
                pricePerKgMinor = 2_000,
                priceMinor = null,
                barcode = null,
                imageUrl = null,
                active = true,
                mixToLoafRatioMilli = 2_200,
                minMixGrams = 500,
                maxMixGrams = 100_000,
            )
        }
    }

    /** Minimal [TenantRepository] stand-in -- only [observeTenant] is exercised by
     *  [StockMovementSyncApplier], the rest of the interface is unused by this test. */
    private class FixedTenantRepository(private val tenantId: Uuid) : TenantRepository {
        private val profile = MutableStateFlow<TenantProfile?>(
            TenantProfile(
                id = tenantId,
                slug = "el-sol",
                name = "Panadería El Sol",
                currencyCode = "CLP",
                enabledModules = setOf(EntitlementModule.INVENTORY_FORMULAS),
            ),
        )

        override fun observeTenant(): Flow<TenantProfile?> = profile

        override suspend fun upsertTenant(profile: TenantProfile) {
            this.profile.value = profile
        }

        override fun observeBranches(): Flow<List<Branch>> = MutableStateFlow(emptyList())

        override suspend fun upsertBranches(branches: List<BranchUpsert>) = Unit

        override fun observeUsers(): Flow<List<UserAccount>> = MutableStateFlow(emptyList())

        override suspend fun upsertUsers(users: List<UserAccountUpsert>) = Unit

        override suspend fun verifyPin(userId: Uuid, pin: String): Boolean = false

        override suspend fun clear() {
            profile.value = null
        }
    }
}
