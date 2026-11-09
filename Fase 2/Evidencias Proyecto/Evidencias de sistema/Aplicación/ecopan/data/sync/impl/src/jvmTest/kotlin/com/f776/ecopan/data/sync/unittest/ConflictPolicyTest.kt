package com.f776.ecopan.data.sync.unittest

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.inventory.sync.StockMovementSyncApplier
import com.f776.ecopan.data.sync.CursorStore
import com.f776.ecopan.data.sync.pull.PullWorker
import com.f776.ecopan.data.sync.push.PushOutcome
import com.f776.ecopan.data.sync.push.PushWorker
import com.f776.ecopan.data.sync.unittest.fake.FakeOutboxDao
import com.f776.ecopan.data.sync.unittest.fake.FakePreferencesDataStore
import com.f776.ecopan.data.sync.unittest.fake.FakeServer
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncAuthProvider
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncTransport
import com.f776.ecopan.data.tenant.sync.BranchSyncApplier
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import com.f776.unigrid.core.database.AppDatabase
import com.f776.unigrid.core.database.entity.BranchEntity
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The two remaining named conflict-policy proofs from 03 §5 ("keep these exact names" -- 01 §10
 * defense material). The third, `duplicatePushAfterDroppedAckIsIdempotent`, already lives in
 * [PushWorkerTest] and is exercised again cross-device by [EndToEndBatchSyncTest].
 *
 * Both tests run two *independent* clients -- each with its own in-memory [AppDatabase], its own
 * outbox, its own [CursorStore], and its own [PullWorker]/[PushWorker] -- against one shared
 * [FakeServer]. That separation is the point: a convergence claim proved against a single shared
 * database would prove nothing.
 *
 * Both use the **real** appliers ([StockMovementSyncApplier], [BranchSyncApplier]), not the generic
 * [com.f776.ecopan.data.sync.unittest.fake.FakeFactApplier]/`FakeConfigApplier` stand-ins that
 * [PullWorkerTest] uses to exercise dispatch mechanics.
 *
 * One deliberate deviation from the plan's wording, called out rather than papered over: 03 §5 test
 * 2 describes the config case as "two admin writes to the same **product** price". There is no
 * `PRODUCT` [com.f776.ecopan.domain.sync.SyncApplier] bound anywhere in the app yet -- `BRANCH`
 * (via [BranchSyncApplier]) is the only real config applier that exists, so the test uses a branch
 * config row instead. The mechanism under test is identical and entity-agnostic (families, not
 * entities, are what 03 §4.3 defines): server-assigned `serverSeq` receipt order decides, client
 * wall-clock never does. When a `PRODUCT` applier is added, this test should gain a product case
 * rather than be rewritten.
 */
class ConflictPolicyTest {

    private val server = FakeServer()
    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val clients = mutableListOf<Client>()

    @AfterTest
    fun closeDatabases() {
        clients.forEach { it.database.close() }
    }

    /**
     * One device: its own local Room cache, outbox, and sync cursor, sharing only [server] with
     * its peer -- i.e. exactly what "two clients" has to mean for a convergence proof to be worth
     * anything.
     */
    private inner class Client(val deviceId: String) {
        val database: AppDatabase = Room.inMemoryDatabaseBuilder<AppDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
        val outbox = FakeOutboxDao()
        private val transport = FakeSyncTransport(server)
        private val cursor = CursorStore(FakePreferencesDataStore())
        val tenantRepository = DaoBackedTenantRepository(tenantId, database)
        private val stockApplier = StockMovementSyncApplier(database.stockMovementDao(), tenantRepository)
        private val branchApplier = BranchSyncApplier(tenantRepository)

        init {
            clients += this
        }

        suspend fun push(): PushOutcome = PushWorker(
            outboxDao = outbox,
            transport = transport,
            authProvider = FakeSyncAuthProvider(),
            deviceIdProvider = { deviceId },
        ).drainAll()

        suspend fun pull(pageLimit: Int = 500) = PullWorker(
            transport = transport,
            cursorStore = cursor,
            database = database,
            appliers = listOf(stockApplier, branchApplier),
            pageLimit = pageLimit,
        ).pullAll()

        /**
         * Mints a movement the way `ProductionRepositoryImpl.logBatch` does while offline: the
         * local domain row and the outbox row land together, sharing one client-minted UUID (03
         * §3.1). Applying it through this client's own [StockMovementSyncApplier] is the same
         * insert-if-unseen write the row's later pull echo will attempt -- which is exactly why
         * the echo is a no-op and no echo-suppression logic is needed (03 §4.2/§4.3).
         */
        suspend fun mintMovementOffline(movementId: Uuid, ingredientId: Uuid, deltaGrams: Long) {
            val payload = movementPayload(movementId, ingredientId, deltaGrams, MOVEMENT_KIND_DECREMENT)
            stockApplier.apply(payload, SyncOpType.CREATE, serverSeq = 0L)
            outbox.insert(
                OutboxEntity(
                    mutationId = movementId,
                    entityType = "STOCK_MOVEMENT",
                    opType = "CREATE",
                    payload = payload.toString(),
                    createdAt = Clock.System.now(),
                    state = OutboxState.PENDING,
                ),
            )
        }

        suspend fun stockGrams(ingredientId: Uuid): Long =
            database.stockMovementDao().observeDeltaSumsByBranch(branchId).first()
                .singleOrNull { it.ingredientId == ingredientId }
                ?.totalDeltaGrams
                ?: 0L

        suspend fun branchRow(): BranchEntity =
            database.branchDao().observeByTenant(tenantId).first().single { it.id == branchId }
    }

    // -------------------------------------------------------------------------------------------
    // 03 §5 test 1
    // -------------------------------------------------------------------------------------------

    /**
     * Stock is append-only signed deltas, so two offline decrements against the same ingredient are
     * not a conflict at all -- they both apply and Σ commutes (03 §5 row 1; 01 §6.4). Proved by
     * running the *same* scenario in both sync orders and asserting an identical final Σ on both
     * clients and on the server, equal to `initial - 9500`.
     */
    @Test
    fun bothOfflineStockDecrementsCommute() = runTest {
        assertEquals(
            INITIAL_GRAMS - 9_500,
            runCommuteScenario(posFirst = true),
            "POS-then-baker sync order",
        )
        assertEquals(
            INITIAL_GRAMS - 9_500,
            runCommuteScenario(posFirst = false),
            "baker-then-POS sync order -- same result, that is what 'commutes' means",
        )
    }

    /**
     * Runs the whole offline-decrement scenario in one sync order and returns the converged stock
     * level, having already asserted that *every* participant (both clients and the server's own
     * replay feed) agrees on it.
     */
    private suspend fun runCommuteScenario(posFirst: Boolean): Long {
        val flourId = Uuid.random()
        val pos = Client("pos-till-${if (posFirst) "a" else "b"}")
        val baker = Client("baker-phone-${if (posFirst) "a" else "b"}")

        // A purchase already on the server, pulled by both clients while still online.
        server.seed(
            "STOCK_MOVEMENT",
            "CREATE",
            movementPayload(Uuid.random(), flourId, INITIAL_GRAMS, MOVEMENT_KIND_PURCHASE),
        )
        pos.pull()
        baker.pull()
        assertEquals(INITIAL_GRAMS, pos.stockGrams(flourId))
        assertEquals(INITIAL_GRAMS, baker.stockGrams(flourId))

        // -- both go offline and each decrements the same ingredient --
        pos.mintMovementOffline(Uuid.random(), flourId, -1_200)
        baker.mintMovementOffline(Uuid.random(), flourId, -8_300)

        // While offline each client only knows its own decrement. This also proves the two Client
        // fixtures really do hold separate databases -- if they shared one, both would read the same
        // number here and every convergence assertion below would be vacuous.
        assertEquals(INITIAL_GRAMS - 1_200, pos.stockGrams(flourId), "POS sees only its own decrement offline")
        assertEquals(INITIAL_GRAMS - 8_300, baker.stockGrams(flourId), "baker sees only its own decrement offline")

        // -- reconnect, in whichever order this run is exercising --
        val (first, second) = if (posFirst) pos to baker else baker to pos
        assertIs<PushOutcome.Done>(first.push())
        first.pull()
        assertIs<PushOutcome.Done>(second.push())
        second.pull()
        // The client that synced first pulls again to pick up the other's movement.
        first.pull()

        val posGrams = pos.stockGrams(flourId)
        val bakerGrams = baker.stockGrams(flourId)
        assertEquals(posGrams, bakerGrams, "both clients converge on the same Σ regardless of sync order")

        // The server agrees too: three movements accepted, no rejection, nothing deduped away.
        // Scoped to this scenario's own ingredient: both sync orders share one FakeServer, so by
        // the time the second run reaches here the feed also holds the first run's movements.
        val serverMovements = server.pull(since = 0, limit = 500, entityTypeNames = setOf("STOCK_MOVEMENT"))
            .changes
            .filter { it.payload.stringField("ingredientId") == flourId.toString() }
        assertEquals(3, serverMovements.size, "purchase + both decrements -- nothing rejected, nothing deduped away")
        val serverGrams = serverMovements.sumOf { it.payload.longField("deltaGrams") }
        assertEquals(posGrams, serverGrams, "server's replay feed sums to the same Σ as the clients")

        return posGrams
    }

    // -------------------------------------------------------------------------------------------
    // 03 §5 test 2
    // -------------------------------------------------------------------------------------------

    /**
     * Config is server-authoritative and resolved last-write-wins by **`serverSeq` receipt order**,
     * never by client wall-clock (03 §5 row 2; 01 §3). Proved by giving the *losing* write (`w1`,
     * lower `serverSeq`) a wall-clock timestamp strictly *later* than the winner's: if any part of
     * the pipeline sorted by timestamp, `w1` would win and this test would fail.
     *
     * Both clients converge on `w2` even though they pull it differently -- one client takes both
     * writes in a single page, the other takes them as two separate pages across two sync cycles --
     * because the ordering guarantee comes from the server's `serverSeq`, not from page batching.
     */
    @Test
    fun configLastWriteWinsByServerSeq() = runTest {
        val singlePageClient = Client("pos-till")
        val pagedClient = Client("baker-phone")

        // Admin edit 1 reaches the server first (lower serverSeq) but carries the LATER wall clock.
        val seq1 = server.seed(
            "BRANCH",
            "UPSERT",
            branchPayload(name = "Sucursal Centro (w1)", active = true, clientUpdatedAt = LATE_WALL_CLOCK),
        )
        // Admin edit 2 reaches the server second (higher serverSeq) with the EARLIER wall clock.
        val seq2 = server.seed(
            "BRANCH",
            "UPSERT",
            branchPayload(name = "Sucursal Centro (w2)", active = false, clientUpdatedAt = EARLY_WALL_CLOCK),
        )
        assertEquals(seq1 + 1, seq2, "w2 is genuinely the later receipt")

        // Client 1: reconnects once and takes both writes in a single page.
        singlePageClient.pull()

        // Client 2: takes them one page at a time, across two separate sync cycles (e.g. a flaky
        // link that only ever gets one page through per window).
        pagedClient.pull(pageLimit = 1)

        for (client in listOf(singlePageClient, pagedClient)) {
            val row = client.branchRow()
            assertEquals(
                "Sucursal Centro (w2)",
                row.name,
                "${client.deviceId} converged on the highest-serverSeq write, not the latest wall clock",
            )
            assertEquals(false, row.active, "the whole winning row wins, not a field-level merge")
            assertEquals(seq2, row.serverSeq, "${client.deviceId} recorded the winner's serverSeq")
        }

        // A late-arriving *third* write still wins on both, and re-pulling settled rows changes
        // nothing -- config appliers are idempotent overwrites, so convergence is stable.
        val seq3 = server.seed(
            "BRANCH",
            "UPSERT",
            branchPayload(name = "Sucursal Centro (w3)", active = true, clientUpdatedAt = EARLY_WALL_CLOCK),
        )
        singlePageClient.pull()
        pagedClient.pull()
        singlePageClient.pull()
        pagedClient.pull()

        for (client in listOf(singlePageClient, pagedClient)) {
            val row = client.branchRow()
            assertEquals("Sucursal Centro (w3)", row.name, "${client.deviceId} took the newest receipt")
            assertEquals(seq3, row.serverSeq)
        }
    }

    // -------------------------------------------------------------------------------------------
    // Wire payloads -- pull-side shapes, exactly as the real appliers decode them
    // -------------------------------------------------------------------------------------------

    /** Mirrors `StockMovementSyncApplier`'s private `StockMovementPullPayload` (`occurredAt` = epoch millis). */
    private fun movementPayload(
        movementId: Uuid,
        ingredientId: Uuid,
        deltaGrams: Long,
        kind: String,
    ) = buildJsonObject {
        put("id", movementId.toString())
        put("branchId", branchId.toString())
        put("ingredientId", ingredientId.toString())
        put("deltaGrams", deltaGrams)
        put("kind", kind)
        put("occurredAt", 1_753_000_000_000L)
    }

    /**
     * Mirrors `BranchSyncApplier`'s private `BranchPullPayload` (`id`, `name`, `active`), plus a
     * `clientUpdatedAt` the applier is *expected* to ignore -- it decodes with `ignoreUnknownKeys`,
     * and 01 §3 says client wall clocks never order anything. Carrying it explicitly is what makes
     * "wall-clock is ignored" an assertion rather than an assumption.
     */
    private fun branchPayload(name: String, active: Boolean, clientUpdatedAt: Long) = buildJsonObject {
        put("id", branchId.toString())
        put("name", name)
        put("active", active)
        put("clientUpdatedAt", clientUpdatedAt)
    }

    private fun JsonElement.stringField(name: String): String = jsonObject.getValue(name).jsonPrimitive.content

    private fun JsonElement.longField(name: String): Long = jsonObject.getValue(name).jsonPrimitive.content.toLong()

    /**
     * [TenantRepository] stand-in wired to the *real* [com.f776.unigrid.core.database.dao.BranchDao],
     * so [BranchSyncApplier]'s writes really hit a database and can be read back. Only the members
     * both appliers touch are implemented; the rest of the interface is out of scope for these tests.
     */
    private class DaoBackedTenantRepository(tenantId: Uuid, private val database: AppDatabase) : TenantRepository {
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

        override suspend fun upsertBranches(branches: List<BranchUpsert>) {
            database.branchDao().upsertAll(
                branches.map {
                    BranchEntity(
                        id = it.id,
                        tenantId = it.tenantId,
                        name = it.name,
                        active = it.active,
                        serverSeq = it.serverSeq,
                        cachedAt = FIXED_CACHED_AT,
                    )
                },
            )
        }

        override fun observeUsers(): Flow<List<UserAccount>> = MutableStateFlow(emptyList())

        override suspend fun upsertUsers(users: List<UserAccountUpsert>) = Unit

        override suspend fun verifyPin(userId: Uuid, pin: String): Boolean = false

        override suspend fun clear() {
            profile.value = null
        }
    }

    private companion object {
        const val INITIAL_GRAMS = 20_000L
        const val MOVEMENT_KIND_PURCHASE = "PURCHASE"
        const val MOVEMENT_KIND_DECREMENT = "FORMULA_DECREMENT"

        /** w1's wall clock, deliberately *after* [EARLY_WALL_CLOCK] -- see `configLastWriteWinsByServerSeq`. */
        const val LATE_WALL_CLOCK = 1_800_000_000_000L
        const val EARLY_WALL_CLOCK = 1_700_000_000_000L

        val FIXED_CACHED_AT: Instant = Instant.fromEpochMilliseconds(1_753_000_000_000L)
    }
}
