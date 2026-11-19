package com.f776.ecopan.data.sync.unittest

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.data.sync.CursorStore
import com.f776.ecopan.data.sync.pull.PullWorker
import com.f776.ecopan.data.sync.transport.SyncAuthException
import com.f776.ecopan.data.sync.transport.SyncAuthProvider
import com.f776.ecopan.data.sync.transport.SyncRefreshOutcome
import com.f776.ecopan.data.sync.transport.SyncTransientException
import com.f776.ecopan.data.sync.unittest.fake.FakeConfigApplier
import com.f776.ecopan.data.sync.unittest.fake.FakeFactApplier
import com.f776.ecopan.data.sync.unittest.fake.FakePreferencesDataStore
import com.f776.ecopan.data.sync.unittest.fake.FakeRowPayload
import com.f776.ecopan.data.sync.unittest.fake.FakeServer
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncTransport
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.unigrid.core.database.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class PullWorkerTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    private val server = FakeServer()
    private val transport = FakeSyncTransport(server)
    private val cursorStore = CursorStore(FakePreferencesDataStore())
    private val configApplier = FakeConfigApplier(database.noteDao())
    private val factApplier = FakeFactApplier(database.noteDao())

    private fun worker(pageLimit: Int = 500) = PullWorker(
        transport = transport,
        cursorStore = cursorStore,
        database = database,
        appliers = listOf(configApplier, factApplier),
        pageLimit = pageLimit,
    )

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun aCompletedPullStampsWhenTheServerWasLastHeardFrom() = runTest {
        val at = Instant.parse("2026-10-07T04:50:00Z")
        PullWorker(
            transport = transport,
            cursorStore = cursorStore,
            database = database,
            appliers = listOf(configApplier, factApplier),
            clock = object : Clock { override fun now() = at },
        ).pullAll()

        assertEquals(at, cursorStore.observeLastPulledAt().first())
    }

    private fun payload(id: Uuid, title: String) = Json.encodeToJsonElement<FakeRowPayload>(FakeRowPayload(id.toString(), title))

    /**
     * The regression that emptied a real device's catalog. A client with no PRODUCT applier pulls
     * the config feed, skips every row, and advances the cursor to the end. Shipping the applier
     * later is not enough on its own -- the server has nothing new to offer past that cursor, so
     * the catalog would stay empty forever.
     */
    @Test
    fun gainingAnApplierRePullsHistoryThatWasPreviouslySkipped() = runTest {
        val productId = Uuid.random()
        server.seed("PRODUCT", "UPSERT", payload(productId, "Marraqueta"))

        // v1 of the app: no PRODUCT applier bound at all. The row is skipped, but the cursor still
        // walks past it -- exactly what PullWorker must do to stay forward-compatible.
        val skipped = mutableListOf<String>()
        PullWorker(
            transport = transport,
            cursorStore = cursorStore,
            database = database,
            appliers = listOf(factApplier),
            onUnrecognizedEntityType = { skipped += it },
        ).pullAll()

        assertEquals(listOf("PRODUCT"), skipped)
        assertEquals(1L, cursorStore.observe().first(), "cursor stepped over the row it could not apply")
        assertTrue(database.noteDao().getAll().first().isEmpty())

        // v2: the app update adds the applier. Nothing new exists server-side, so resuming from the
        // stored cursor would find nothing -- the client must notice its applier set grew and refetch.
        PullWorker(
            transport = transport,
            cursorStore = cursorStore,
            database = database,
            appliers = listOf(factApplier, configApplier),
        ).pullAll()

        assertEquals(
            listOf("Marraqueta"),
            database.noteDao().getAll().first().map { it.title },
            "the previously-skipped row was re-pulled and applied",
        )
    }

    /** The reset must fire once, not on every sync: re-pulling the entire feed each cycle would be
     *  a self-inflicted denial of service against the backend. */
    @Test
    fun anUnchangedApplierSetResumesFromTheCursorInsteadOfRefetching() = runTest {
        server.seed("PRODUCT", "UPSERT", payload(Uuid.random(), "v1"))
        worker().pullAll()
        val cursorAfterFirst = cursorStore.observe().first()

        var refetched = false
        PullWorker(
            transport = transport,
            cursorStore = cursorStore,
            database = database,
            appliers = listOf(configApplier, factApplier),
            onApplierSetChanged = { _, _ -> refetched = true },
        ).pullAll()

        assertEquals(false, refetched, "same appliers as last run -- nothing to re-pull")
        assertEquals(cursorAfterFirst, cursorStore.observe().first())
    }

    /**
     * The state a real device was actually left in, which the applier-set check alone cannot detect.
     *
     * The first build to ship `writeAppliedTypes` recorded the *post-fix* applier set against a
     * cursor that had already walked past the whole catalog. From then on the stored set matched the
     * live one on every launch, so no re-pull was ever triggered and the device stayed empty --
     * indistinguishable, by that signal alone, from a device that had applied everything correctly.
     * Only the heal version can tell them apart.
     */
    @Test
    fun aDeviceWhoseCursorWasPoisonedBeforeTheHealVersionExistedStillRePulls() = runTest {
        val productId = Uuid.random()
        val seq = server.seed("PRODUCT", "UPSERT", payload(productId, "Marraqueta"))

        // Reconstruct the poisoned state exactly: cursor past every row, applier set already
        // recording both appliers, and no heal version (the key did not exist in that build).
        cursorStore.advanceTo(seq)
        cursorStore.writeAppliedTypes(setOf(configApplier.entityType.name, factApplier.entityType.name))
        assertEquals(0, cursorStore.readHealVersion())
        assertTrue(database.noteDao().getAll().first().isEmpty())

        worker().pullAll()

        assertEquals(
            listOf("Marraqueta"),
            database.noteDao().getAll().first().map { it.title },
            "heal version forced the re-pull the applier-set check could not see was needed",
        )
        assertEquals(PullWorker.PULL_HEAL_VERSION, cursorStore.readHealVersion())
    }

    /** The repair is one-time, not every launch: it must not become a per-sync full refetch. */
    @Test
    fun theHealRePullFiresOnlyOnce() = runTest {
        server.seed("PRODUCT", "UPSERT", payload(Uuid.random(), "Marraqueta"))
        worker().pullAll()
        val cursorAfterHeal = cursorStore.observe().first()

        var repaired = false
        PullWorker(
            transport = transport,
            cursorStore = cursorStore,
            database = database,
            appliers = listOf(configApplier, factApplier),
            onApplierSetChanged = { _, _ -> repaired = true },
        ).pullAll()

        assertEquals(false, repaired, "heal version already current -- nothing to repair")
        assertEquals(cursorAfterHeal, cursorStore.observe().first())
    }

    /**
     * A device whose access token expired got a 401 on pull and simply stopped: nothing on the pull
     * path refreshed, so every later attempt 401'd identically. Push had this policy from the start;
     * pull did not, and the asymmetry meant a till could keep uploading sales while never again
     * receiving a catalog change.
     */
    @Test
    fun anExpiredTokenIsRefreshedAndThePullRetried() = runTest {
        server.seed("PRODUCT", "UPSERT", payload(Uuid.random(), "Marraqueta"))
        transport.pullUnauthorizedOnce = true

        var refreshes = 0
        val authProvider = object : SyncAuthProvider {
            override suspend fun currentAccessToken() = "token"
            override suspend fun refresh(): SyncRefreshOutcome { refreshes += 1; return SyncRefreshOutcome.REFRESHED }
        }

        PullWorker(
            transport = transport,
            cursorStore = cursorStore,
            database = database,
            appliers = listOf(configApplier, factApplier),
            authProvider = authProvider,
        ).pullAll()

        assertEquals(1, refreshes)
        assertEquals(
            listOf("Marraqueta"),
            database.noteDao().getAll().first().map { it.title },
            "the pull retried after refreshing instead of giving up on the 401",
        )
    }

    /** A refresh that fails means the device session is dead -- surface it, don't retry blindly. */
    @Test
    fun aFailedRefreshPropagatesTheAuthFailure() = runTest {
        server.seed("PRODUCT", "UPSERT", payload(Uuid.random(), "Marraqueta"))
        transport.pullUnauthorizedOnce = true

        val authProvider = object : SyncAuthProvider {
            override suspend fun currentAccessToken() = "token"
            override suspend fun refresh() = SyncRefreshOutcome.REJECTED
        }

        assertFailsWith<SyncAuthException> {
            PullWorker(
                transport = transport,
                cursorStore = cursorStore,
                database = database,
                appliers = listOf(configApplier, factApplier),
                authProvider = authProvider,
            ).pullAll()
        }
    }

    /** #133: a throttled refresh is not a dead session -- report it as transient, not as auth. */
    @Test
    fun aTransientRefreshIsReportedAsTransientNotAsAuth() = runTest {
        server.seed("PRODUCT", "UPSERT", payload(Uuid.random(), "Marraqueta"))
        transport.pullUnauthorizedOnce = true

        val authProvider = object : SyncAuthProvider {
            override suspend fun currentAccessToken() = "token"
            override suspend fun refresh() = SyncRefreshOutcome.TRANSIENT
        }

        assertFailsWith<SyncTransientException> {
            PullWorker(
                transport = transport,
                cursorStore = cursorStore,
                database = database,
                appliers = listOf(configApplier, factApplier),
                authProvider = authProvider,
            ).pullAll()
        }
    }

    @Test
    fun pullPageAppliesAtomicallyAndAdvancesCursor() = runTest {
        val rowA = Uuid.random()
        val rowB = Uuid.random()
        val seqA = server.seed("PRODUCT", "UPSERT", payload(rowA, "a"))
        val seqB = server.seed("PRODUCT", "UPSERT", payload(rowB, "b"))

        // "Crash" mid multi-page pull: the 2nd underlying pull() call (fetching page 2) throws
        // before anything about page 2 is applied -- page 1 must already be committed by then.
        transport.failOnPullCallNumber = 2
        assertFailsWith<Exception> { worker(pageLimit = 1).pullAll() }

        // Cursor never moved past the last *fully applied* page -- nothing lost, nothing skipped.
        assertEquals(seqA, cursorStore.observe().first())
        assertEquals(listOf("a"), database.noteDao().getAll().first().map { it.title })

        // Restart: resumes from the persisted cursor, re-requests (and this time applies) page 2.
        transport.failOnPullCallNumber = null
        worker(pageLimit = 1).pullAll()
        assertEquals(seqB, cursorStore.observe().first())
        assertEquals(setOf("a", "b"), database.noteDao().getAll().first().map { it.title }.toSet())
        assertEquals(2, database.noteDao().getAll().first().size, "no duplicates from the re-pulled page")
    }

    /**
     * Narrowed to the two applier families this work item actually wires end to end
     * ([FakeConfigApplier]/[FakeFactApplier], standing in for the real `BranchSyncApplier`/
     * `StockMovementSyncApplier` -- `:data:sync:impl` can't depend on `:data:tenant:impl`/
     * `:data:inventory:impl` to use those directly in a test here, see `SyncApplier`'s KDoc).
     */
    @Test
    fun pulledConfigOverwritesCacheAndFactsInsertIfUnseen() = runTest {
        val configId = Uuid.random()
        val factId = Uuid.random()

        // The fact was already applied once in an earlier pull (this client's own echo, or a
        // teammate's) -- apply it directly, out of band, the way an earlier page already would have.
        factApplier.apply(payload(factId, "original"), SyncOpType.CREATE, serverSeq = 0)

        // One page mixing a config update (must overwrite) and a re-delivery of the *same* fact
        // id with different content (must be ignored -- insert-if-unseen, 03 §4.3).
        server.seed("PRODUCT", "UPSERT", payload(configId, "v1"))
        server.seed("PRODUCT", "UPSERT", payload(configId, "v2"))
        server.seed("STOCK_MOVEMENT", "CREATE", payload(factId, "corrupted-echo"))

        worker().pullAll()

        val rows = database.noteDao().getAll().first().associateBy { it.id }
        assertEquals("v2", rows.getValue(configId).title, "config always overwrites by id (LWW)")
        assertEquals("original", rows.getValue(factId).title, "facts never overwrite once seen (insert-if-unseen)")
    }
}
