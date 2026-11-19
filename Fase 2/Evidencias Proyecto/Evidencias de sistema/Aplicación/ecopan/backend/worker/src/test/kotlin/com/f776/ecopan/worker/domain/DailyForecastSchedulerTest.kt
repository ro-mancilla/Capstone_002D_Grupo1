package com.f776.ecopan.worker.domain

import com.f776.ecopan.data.database.repository.BranchRepository
import com.f776.ecopan.data.database.repository.ClaimResult
import com.f776.ecopan.data.database.repository.ScheduledRunRepository
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.model.Branch
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.messagebroker.ForecastRefreshBroker
import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested
import com.f776.ecopan.worker.fakes.FakeTenantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Daily refresh trigger (#46): once per branch per local day, even with several replicas ticking. */
class DailyForecastSchedulerTest {

    private val now = AtomicReference(Instant.parse("2026-07-20T04:30:00Z")) // 00:30 Santiago (UTC-4)
    private val clock = object : Clock { override fun now() = now.get() }

    private val tenants = FakeTenantRepository()
    private val branches = FakeBranches()
    private val runs = LeasedRuns(clock)
    private val broker = RecordingBroker()

    private val forecasting = tenant("a", setOf(Module.POS_SALES, Module.DEMAND_FORECASTING))
    private val posOnly = tenant("b", setOf(Module.POS_SALES))

    init {
        tenants.seed(forecasting)
        tenants.seed(posOnly)
    }

    private fun scheduler(
        tenantRepo: TenantRepository = tenants,
        runRepo: ScheduledRunRepository = runs,
        brokerImpl: ForecastRefreshBroker = broker,
    ) = DailyForecastScheduler(
        tenants = tenantRepo,
        branches = branches,
        runs = runRepo,
        broker = brokerImpl,
        reportingTz = "America/Santiago",
        lease = 5.minutes,
        clock = clock,
    )

    private fun at(iso: String) = now.set(Instant.parse(iso))

    @Test
    fun `publishes one request per active branch of forecasting tenants only`() = runBlocking {
        val b1 = branches.add(forecasting, active = true)
        val b2 = branches.add(forecasting, active = true)
        branches.add(forecasting, active = false)
        branches.add(posOnly, active = true)

        scheduler().tick()

        assertEquals(setOf(b1.id.toString(), b2.id.toString()), broker.published.map { it.branchId }.toSet())
        assertEquals(2, broker.published.size)
        assertTrue(broker.published.all { it.tenantId == forecasting.id.toString() && it.productType == null })
    }

    @Test
    fun `window opens at exactly 00-05-00 local and not a second before`() = runBlocking {
        branches.add(forecasting, active = true)

        at("2026-07-20T04:04:59Z")
        scheduler().tick()
        assertTrue(broker.published.isEmpty())

        at("2026-07-20T04:05:00Z")
        scheduler().tick()
        assertEquals(1, broker.published.size)
    }

    @Test
    fun `second tick the same local day is a no-op, next day fires again`() = runBlocking {
        branches.add(forecasting, active = true)
        val s = scheduler()
        s.tick()
        s.tick()
        assertEquals(1, broker.published.size)

        at("2026-07-21T04:30:00Z")
        s.tick()
        assertEquals(2, broker.published.size)
    }

    @Test
    fun `a tenant that enables forecasting after the first tick of the day is still refreshed that day`() = runBlocking {
        branches.add(posOnly, active = true)
        val s = scheduler()
        s.tick() // nobody eligible yet
        assertTrue(broker.published.isEmpty())

        tenants.updateEnabledModules(posOnly.id, setOf(Module.POS_SALES, Module.DEMAND_FORECASTING))
        s.tick()
        assertEquals(1, broker.published.size)
    }

    @Test
    fun `a fresh scheduler on the same day also does nothing (completed rows are durable)`() = runBlocking {
        branches.add(forecasting, active = true)
        scheduler().tick()
        scheduler().tick()
        assertEquals(1, broker.published.size)
    }

    @Test
    fun `replicas racing the same day produce one refresh per branch`() = runBlocking {
        repeat(3) { branches.add(forecasting, active = true) }

        (1..8).map { async(Dispatchers.Default) { scheduler().tick() } }.awaitAll()

        val perBranch = broker.published.groupingBy { it.branchId }.eachCount()
        assertEquals(3, perBranch.size)
        assertTrue(perBranch.values.all { it == 1 }, "each branch refreshed exactly once: $perBranch")
    }

    @Test
    fun `partial publish failure retries only the failed branch`() = runBlocking {
        val b1 = branches.add(forecasting, active = true)
        val b2 = branches.add(forecasting, active = true)
        val flaky = RecordingBroker(failFor = setOf(b2.id.toString()))
        val s = scheduler(brokerImpl = flaky)

        assertFailsWith<IllegalStateException> { s.tick() }
        assertEquals(listOf(b1.id.toString()), flaky.published.map { it.branchId })

        flaky.failFor = emptySet()
        s.tick()
        assertEquals(setOf(b1.id.toString(), b2.id.toString()), flaky.published.map { it.branchId }.toSet())
        assertEquals(2, flaky.published.size, "branch 1 must not be re-published")
    }

    @Test
    fun `release failing after a publish failure surfaces the publish error and the lease still recovers`() = runBlocking {
        val b1 = branches.add(forecasting, active = true)
        val brokenRelease = object : ScheduledRunRepository by runs {
            override suspend fun release(job: String, day: LocalDate, scope: String) = error("db blip")
        }
        val flaky = RecordingBroker(failFor = setOf(b1.id.toString()))

        val thrown = assertFailsWith<IllegalStateException> { scheduler(runRepo = brokenRelease, brokerImpl = flaky).tick() }
        assertEquals("broker down", thrown.message)

        // Claim is stuck (release failed): still held within the lease ...
        flaky.failFor = emptySet()
        scheduler(brokerImpl = flaky).tick()
        assertTrue(flaky.published.isEmpty())

        // ... and taken over once the lease expires.
        now.set(now.get() + 6.minutes)
        scheduler(brokerImpl = flaky).tick()
        assertEquals(1, flaky.published.size)
    }

    @Test
    fun `a worker killed after claiming does not lose the day once the lease expires`() = runBlocking {
        val b1 = branches.add(forecasting, active = true)
        // The dead worker: claimed, never published, never completed.
        runs.tryClaim(DailyForecastScheduler.JOB, LocalDate(2026, 7, 20), b1.id.toString(), 5.minutes)

        scheduler().tick()
        assertTrue(broker.published.isEmpty(), "lease still live: someone may be working on it")

        now.set(now.get() + 6.minutes)
        scheduler().tick()
        assertEquals(listOf(b1.id.toString()), broker.published.map { it.branchId })
    }

    @Test
    fun `a failing tenant listing claims nothing and propagates`() = runBlocking {
        branches.add(forecasting, active = true)
        val broken = object : TenantRepository by tenants {
            override suspend fun listAll(): List<Tenant> = error("db down")
        }
        assertFailsWith<IllegalStateException> { scheduler(tenantRepo = broken).tick() }
        assertTrue(runs.claims.isEmpty())

        scheduler().tick()
        assertEquals(1, broker.published.size)
    }

    @Test
    fun `schema not ready (42P01) is a quiet no-op that retries on the next tick`() = runBlocking {
        branches.add(forecasting, active = true)
        var ready = false
        val booting = object : TenantRepository by tenants {
            override suspend fun listAll(): List<Tenant> {
                if (!ready) throw RuntimeException("wrapped", java.sql.SQLException("relation \"tenants\" does not exist", "42P01"))
                return tenants.listAll()
            }
        }
        val s = scheduler(tenantRepo = booting)
        assertEquals(0, s.tick())
        assertTrue(broker.published.isEmpty())

        ready = true
        s.tick()
        assertEquals(1, broker.published.size)
    }

    @Test
    fun `other SQL errors still propagate`() = runBlocking {
        val broken = object : TenantRepository by tenants {
            override suspend fun listAll(): List<Tenant> = throw java.sql.SQLException("boom", "08006")
        }
        assertFailsWith<java.sql.SQLException> { scheduler(tenantRepo = broken).tick() }
    }

    // --- Santiago DST (UTC-3 in summer, UTC-4 in winter) --------------------------------------

    @Test
    fun `spring-forward day, where 00-00 to 00-59 does not exist, still fires once at 01-00`() = runBlocking {
        branches.add(forecasting, active = true)
        // 2026-09-06 04:00Z: Santiago jumps from 23:59:59 on Sep 5 straight to 01:00:00 on Sep 6.
        at("2026-09-06T03:59:00Z") // 23:59 Sep 5 -- already past 00:05, belongs to Sep 5
        scheduler().tick()
        assertEquals(setOf(LocalDate(2026, 9, 5)), runs.claimedDays())

        at("2026-09-06T04:00:00Z") // 01:00 Sep 6
        val s = scheduler()
        s.tick()
        s.tick()
        assertEquals(setOf(LocalDate(2026, 9, 5), LocalDate(2026, 9, 6)), runs.claimedDays())
        assertEquals(2, broker.published.size)
    }

    @Test
    fun `fall-back day, where 23-xx happens twice, fires once for that local date`() = runBlocking {
        branches.add(forecasting, active = true)
        val s = scheduler()
        at("2026-04-05T02:30:00Z") // 23:30 Apr 4 (UTC-3)
        s.tick()
        at("2026-04-05T03:30:00Z") // 23:30 Apr 4 again (UTC-4)
        s.tick()
        assertEquals(setOf(LocalDate(2026, 4, 4)), runs.claimedDays())
        assertEquals(1, broker.published.size)
    }

    private fun tenant(slug: String, modules: Set<Module>) =
        Tenant(Uuid.random(), slug, slug, "CLP", modules, Clock.System.now())

    private class RecordingBroker(@Volatile var failFor: Set<String> = emptySet()) : ForecastRefreshBroker {
        val published = CopyOnWriteArrayList<ForecastRefreshRequested>()
        override suspend fun publish(message: ForecastRefreshRequested) {
            if (message.branchId in failFor) error("broker down")
            published += message
        }
        override suspend fun consume(block: suspend (ForecastRefreshRequested) -> Unit) = Unit
    }

    /** In-memory twin of the DB claim: atomic, lease-aware, completion-aware. */
    private class LeasedRuns(private val clock: Clock) : ScheduledRunRepository {
        private data class State(var claimedAt: Instant, var completed: Boolean = false)
        val claims = ConcurrentHashMap<Triple<String, LocalDate, String>, State>()

        fun claimedDays(): Set<LocalDate> = claims.keys.map { it.second }.toSet()

        override suspend fun tryClaim(job: String, day: LocalDate, scope: String, lease: Duration): ClaimResult {
            var result = ClaimResult.HELD
            claims.compute(Triple(job, day, scope)) { _, existing ->
                val now = clock.now()
                when {
                    existing == null -> State(now).also { result = ClaimResult.WON }
                    existing.completed -> existing.also { result = ClaimResult.DONE }
                    existing.claimedAt < now - lease -> existing.also { it.claimedAt = now; result = ClaimResult.WON }
                    else -> existing
                }
            }
            return result
        }

        override suspend fun complete(job: String, day: LocalDate, scope: String) {
            claims[Triple(job, day, scope)]?.completed = true
        }

        override suspend fun release(job: String, day: LocalDate, scope: String) {
            claims.computeIfPresent(Triple(job, day, scope)) { _, s -> if (s.completed) s else null }
        }
    }

    private class FakeBranches : BranchRepository {
        private val all = CopyOnWriteArrayList<Branch>()

        fun add(tenant: Tenant, active: Boolean): Branch =
            Branch(Uuid.random(), tenant.id, "branch", active, 0, Clock.System.now()).also { all += it }

        override suspend fun create(id: Uuid, tenantId: Uuid, name: String) = error("unused")
        override suspend fun findById(id: Uuid) = all.find { it.id == id }
        override suspend fun listByTenant(tenantId: Uuid, includeInactive: Boolean) =
            all.filter { it.tenantId == tenantId && (includeInactive || it.active) }
        override suspend fun update(id: Uuid, name: String?, active: Boolean?) = error("unused")
    }
}
