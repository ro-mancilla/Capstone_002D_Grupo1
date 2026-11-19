package com.f776.ecopan.data.sync.unittest

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.catalog.di.CatalogDataModule
import com.f776.ecopan.data.sync.CursorStore
import com.f776.ecopan.data.sync.SyncEngineImpl
import com.f776.ecopan.data.sync.pull.PullWorker
import com.f776.ecopan.data.sync.push.PushWorker
import com.f776.ecopan.data.sync.transport.SyncTransport
import com.f776.ecopan.data.sync.unittest.fake.FakeConnectivityMonitor
import com.f776.ecopan.data.sync.unittest.fake.FakePreferencesDataStore
import com.f776.ecopan.data.sync.unittest.fake.FakeServer
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncAuthProvider
import com.f776.ecopan.data.sync.unittest.fake.FakeSyncTransport
import com.f776.ecopan.domain.catalog.ProductRepository
import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.wire.PullResponse
import com.f776.ecopan.domain.sync.wire.PushRequest
import com.f776.ecopan.domain.sync.wire.PushResponse
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.unigrid.core.database.AppDatabase
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.OutboxState
import com.f776.unigrid.core.database.entity.SaleEntity
import com.f776.unigrid.core.database.entity.SaleLineEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * The acceptance line of "bootstrap pushes the outbox before the full pull": a sale the server
 * already applied (its ack was lost) is still queued here. The pulled Almacén `stockUnits`
 * already includes it, so the till must show the **server figure**, not server minus the sale.
 *
 * Runs the real pull applier and the real `ProductRepositoryImpl` (with its unsynced-units
 * subtraction) over a real in-memory Room database; only the transport is a fake.
 */
class BootstrapAlmacenStockTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    private val koin = koinApplication {
        modules(
            CatalogDataModule,
            module {
                single(named("defaultDispatcher")) { Dispatchers.Default }
                single(named("ioDispatcher")) { Dispatchers.IO }
                single(named("backendBaseUrl")) { "http://localhost:8080" }
                single { database }
            },
        )
    }.koin

    private val server = FakeServer()
    private val inner = FakeSyncTransport(server)
    private val pushedMutationIds = mutableListOf<String>()
    private val transport = object : SyncTransport {
        override suspend fun push(request: PushRequest): PushResponse {
            pushedMutationIds += request.mutations.map { it.mutationId }
            return inner.push(request)
        }

        override suspend fun pull(since: Long, limit: Int, entityTypes: Set<SyncEntityType>?): PullResponse =
            inner.pull(since, limit, entityTypes)
    }

    @AfterTest
    fun tearDown() {
        koin.close()
        database.close()
    }

    @Test
    fun `pending sale already applied by the server shows the server stock after bootstrap`() = runTest(UnconfinedTestDispatcher()) {
        val tenantId = Uuid.random()
        koin.get<TenantRepository>().upsertTenant(
            TenantProfile(tenantId, "el-sol", "El Sol", "CLP", setOf(EntitlementModule.POS_SALES)),
        )
        val colaId = Uuid.random()
        // Server figure *after* it applied the sale of 3: it had 13, now 10.
        server.seed(
            "PRODUCT", "UPSERT",
            Json.parseToJsonElement(
                """{"id":"$colaId","name":"Cola","productType":"Almacén","soldBy":"UNIT","priceMinor":1500,
                   "active":true,"origin":"ALMACEN","lowStockThresholdUnits":5,"stockUnits":10}""",
            ),
        )
        val saleId = Uuid.random()
        val now = Clock.System.now()
        database.saleDao().recordSale(
            sale = SaleEntity(saleId, Uuid.random(), Uuid.random(), now, PaymentMethod.CASH, 4500, 5000, 500),
            lines = listOf(
                SaleLineEntity(Uuid.random(), saleId, colaId, "Almacén", SoldBy.UNIT, null, 3, 1500, 4500),
            ),
            outboxRow = OutboxEntity(
                mutationId = saleId, entityType = "SALE", opType = "CREATE", payload = """{"id":"$saleId"}""",
                createdAt = now, state = OutboxState.PENDING,
            ),
        )
        val cursorStore = CursorStore(FakePreferencesDataStore())
        val engine = SyncEngineImpl(
            pushWorker = PushWorker(database.outboxDao(), transport, FakeSyncAuthProvider(), deviceIdProvider = { "till" }),
            pullWorker = PullWorker(
                transport, cursorStore, database,
                appliers = koin.getAll<SyncApplier>(),
            ),
            cursorStore = cursorStore,
            outboxDao = database.outboxDao(),
            connectivityMonitor = FakeConnectivityMonitor(),
            scope = backgroundScope,
        )

        val result = engine.bootstrap()

        assertEquals(true, result.success)
        assertEquals(listOf(saleId.toString()), pushedMutationIds, "the sale payload was actually sent")
        val cola = koin.get<ProductRepository>().observeActiveProducts().first().single()
        assertEquals(10L, cola.stockUnits, "server figure, not server minus the sale (7)")
    }
}
