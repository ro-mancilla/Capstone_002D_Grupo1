package com.f776.ecopan.data.sync.unittest

import com.f776.ecopan.data.catalog.di.CatalogDataModule
import com.f776.ecopan.data.inventory.di.InventoryDataModule
import com.f776.ecopan.data.production.di.ProductionDataModule
import com.f776.ecopan.data.tenant.di.TenantDataModule
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `PullWorker` collects appliers with `getAll<SyncApplier>()` and silently skips any pulled row
 * whose `entityType` has no applier bound. That combination fails quietly: the backend returns the
 * rows, the cursor advances, sync reports success, and the data simply never appears.
 *
 * It already happened. `PRODUCT`, `INGREDIENT`, `FORMULA` and `USER` had no
 * applier at all, so a fully-seeded backend produced an empty product grid and an empty staff
 * picker with no error anywhere. This test exists so the next missing applier fails a build instead
 * of a demo.
 *
 * It asserts against the real Koin modules -- the point is the *wiring*, which per-applier unit
 * tests cannot see.
 */
class SyncApplierCoverageTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private val testDependencies = module {
        single(named("defaultDispatcher")) { Dispatchers.Default }
        single(named("ioDispatcher")) { Dispatchers.IO }
        single(named("ioCoroutineScope")) { scope }
        single(named("backendBaseUrl")) { "http://localhost:8080" }
    }

    private val koin = koinApplication {
        modules(CatalogDataModule, InventoryDataModule, TenantDataModule, ProductionDataModule, testDependencies)
    }.koin

    @AfterTest
    fun tearDown() {
        koin.close()
    }

    /**
     * Entity types the client is expected to *apply* on pull. `SALE` is excluded deliberately: it
     * is push-only from a till's perspective (02 §4.2 serves it for the dashboard's benefit), and
     * no client surface reads another device's sales.
     */
    private val pullApplied = SyncEntityType.entries - SyncEntityType.SALE

    @Test
    fun `every pulled entity type has an applier bound`() {
        val covered = koin.getAll<SyncApplier>().map { it.entityType }.toSet()

        val missing = pullApplied.filterNot { it in covered }
        assertTrue(missing.isEmpty(), "no SyncApplier bound for $missing -- those rows pull and vanish")
    }

    @Test
    fun `no entity type has two appliers fighting over it`() {
        val byType = koin.getAll<SyncApplier>().groupBy { it.entityType }

        val duplicated = byType.filterValues { it.size > 1 }.keys
        assertTrue(duplicated.isEmpty(), "more than one applier bound for $duplicated")
    }

    @Test
    fun `getAll sees every applier, not just the last one registered`() {
        // Koin keys definitions by (type, qualifier). These survive only because each applier has a
        // distinct primary type -- the hardware driver factories, all sharing one interface as their
        // primary type, silently collapsed to a single definition until they were qualified.
        assertEquals(pullApplied.size, koin.getAll<SyncApplier>().size)
    }
}
