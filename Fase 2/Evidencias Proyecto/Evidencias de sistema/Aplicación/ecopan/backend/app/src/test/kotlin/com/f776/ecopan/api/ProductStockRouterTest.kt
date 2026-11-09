package com.f776.ecopan.api

import com.f776.ecopan.api.core.PermissionRegistry
import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.core.mintAccessToken
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeCatalogRepository
import com.f776.ecopan.api.fakes.FakeProductStockRepository
import com.f776.ecopan.api.fakes.FakeRefreshTokenRepository
import com.f776.ecopan.api.fakes.FakeRolePermissionRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.api.fakes.installTestKoin
import com.f776.ecopan.api.feature.catalog.controller.catalogRouter
import com.f776.ecopan.api.feature.inventory.controller.inventoryRouter
import com.f776.ecopan.api.feature.sync.controller.syncRouter
import com.f776.ecopan.api.fakes.FakeSyncPullRepository
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Almacén product stock end to end: opening count on create, computed stock, no module gate (ticket #24). */
class ProductStockRouterTest {

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val otherBranchId = Uuid.random()
    private val managerId = Uuid.random()

    private fun testConfig() = MapApplicationConfig(
        "jwt.secret" to "test-secret",
        "jwt.issuer" to "ecopan-test",
        "jwt.audience" to "ecopan-test-clients",
        "jwt.accessTokenTtlMin" to "15",
        "jwt.refreshTokenTtlDays" to "30",
        "device.sessionTtlDays" to "180",
        "reporting.tz" to "America/Santiago"
    )

    private class Harness(
        val client: HttpClient,
        val token: String,
        val productStock: FakeProductStockRepository,
        val catalog: FakeCatalogRepository,
        val syncPull: FakeSyncPullRepository
    ) {
        suspend fun get(path: String) = client.get(path) { header(HttpHeaders.Authorization, "Bearer $token") }
        suspend fun post(path: String, body: String) = client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }

    private fun app(
        modules: Set<Module>,
        role: Role = Role.BRANCH_MANAGER,
        tokenBranch: Uuid? = branchId,
        block: suspend Harness.() -> Unit
    ) = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(
                mapOf(role to setOf(Permission.CATALOG_READ, Permission.CATALOG_WRITE, Permission.STOCK_READ, Permission.STOCK_ADJUST, Permission.SYNC))
            )
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", modules, Clock.System.now()))
        val branches = FakeBranchRepository()
        kotlinx.coroutines.runBlocking {
            branches.seed(branches.create(branchId, tenantId, "Main"))
            branches.seed(branches.create(otherBranchId, tenantId, "Other"))
        }
        val catalog = FakeCatalogRepository()
        val productStock = FakeProductStockRepository()
        val syncPull = FakeSyncPullRepository()
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(
                config, tenants, FakeUserRepository(), branches, FakeRefreshTokenRepository(),
                catalog = catalog, productStock = productStock, syncPull = syncPull
            )
            configureAuth()
            configureSerialization()
            catalogRouter()
            inventoryRouter()
            syncRouter()
        }
        val token = mintAccessToken(Settings(config).jwtSettings, managerId, tenantId, tokenBranch, role, "m1")
        Harness(client, token, productStock, catalog, syncPull).block()
    }

    private fun json(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject
    private suspend fun HttpResponse.code() = json(bodyAsText())["code"]?.jsonPrimitive?.content

    private fun almacenBody(id: Uuid, extra: String = "") =
        """{"id":"$id","name":"Bebida cola","productType":"Almacén","soldBy":"UNIT","priceMinor":1500$extra}"""

    private suspend fun Harness.levels(branch: Uuid = branchId) =
        Json.parseToJsonElement(get("/inventory/product-stock?branchId=$branch").bodyAsText()).jsonArray.map { it.jsonObject }

    private suspend fun Harness.stockOf(productId: Uuid, branch: Uuid = branchId) =
        levels(branch).single { it["productId"]!!.jsonPrimitive.content == productId.toString() }["currentUnits"]!!.jsonPrimitive.content.toLong()

    @Test
    fun `an Almacen product is created with its opening count and shows it as current stock`() = app(setOf(Module.POS_SALES)) {
        val id = Uuid.random()
        val create = post("/catalog/products", almacenBody(id, ""","openingStock":12"""))
        assertEquals(HttpStatusCode.Created, create.status)

        val row = levels().single()
        assertEquals(12, row["receivedUnits"]!!.jsonPrimitive.content.toLong())
        assertEquals(12, row["currentUnits"]!!.jsonPrimitive.content.toLong())
        val ledger = Json.parseToJsonElement(get("/inventory/product-movements?branchId=$branchId&productId=$id").bodyAsText()).jsonArray
        assertEquals("OPENING_COUNT", ledger.single().jsonObject["kind"]!!.jsonPrimitive.content)
    }

    @Test
    fun `each stock row says whether its product is active so clients can leave out retired ones`() = app(setOf(Module.POS_SALES)) {
        val live = Uuid.random()
        val retired = Uuid.random()
        post("/catalog/products", almacenBody(live, ""","openingStock":3"""))
        post("/catalog/products", almacenBody(retired, ""","openingStock":0""").replace("Bebida cola", "Bebida"))
        catalog.updateProduct(retired, active = false)

        val byId = levels().associate { it["productId"]!!.jsonPrimitive.content to it["active"]!!.jsonPrimitive.content.toBoolean() }
        assertEquals(mapOf(live.toString() to true, retired.toString() to false), byId)
    }

    @Test
    fun `a failing opening count leaves no product behind`() = app(setOf(Module.POS_SALES)) {
        productStock.failAppend = IllegalStateException("ledger down")
        val id = Uuid.random()
        val create = runCatching { post("/catalog/products", almacenBody(id, ""","openingStock":12""")) }.getOrNull()
        assertTrue(create == null || !create.status.isSuccess())
        assertTrue(catalog.products.isEmpty(), "a product without an opening count must not survive")

        productStock.failAppend = null
        assertEquals(HttpStatusCode.Created, post("/catalog/products", almacenBody(id, ""","openingStock":12""")).status)
        assertEquals(12, stockOf(id))
    }

    @Test
    fun `the till's pulled stock equals the web stock list`() = app(setOf(Module.POS_SALES)) {
        val id = Uuid.random()
        post("/catalog/products", almacenBody(id, ""","openingStock":10"""))
        post(
            "/inventory/product-movements",
            """{"id":"${Uuid.random()}","branchId":"$branchId","productId":"$id","deltaUnits":6,"kind":"PURCHASE","occurredAt":${System.currentTimeMillis()}}"""
        )
        productStock.soldUnits[id] = 20
        syncPull.rows += com.f776.ecopan.data.database.repository.model.PullRow(
            com.f776.ecopan.data.domain.SyncEntityType.PRODUCT.name, 1, catalog.products.getValue(id)
        )

        val pulled = json(get("/sync/pull?since=0&limit=10").bodyAsText())["changes"]!!.jsonArray
            .single().jsonObject["payload"]!!.jsonObject["stockUnits"]!!.jsonPrimitive.content.toLong()
        assertEquals(stockOf(id), pulled)
        assertEquals(-4, pulled)
    }

    @Test
    fun `an opening count of zero is accepted and a missing or negative one is refused without storing the product`() = app(setOf(Module.POS_SALES)) {
        assertEquals(HttpStatusCode.Created, post("/catalog/products", almacenBody(Uuid.random(), ""","openingStock":0""")).status)
        assertEquals(0, levels().single()["currentUnits"]!!.jsonPrimitive.content.toLong())

        val missing = post("/catalog/products", almacenBody(Uuid.random()))
        assertEquals(HttpStatusCode.BadRequest, missing.status)
        assertEquals("OPENING_STOCK_REQUIRED", missing.code())

        val negative = post("/catalog/products", almacenBody(Uuid.random(), ""","openingStock":-1"""))
        assertEquals(HttpStatusCode.BadRequest, negative.status)
        assertEquals("INVALID_OPENING_STOCK", negative.code())
        assertEquals(1, catalog.products.size)
    }

    @Test
    fun `a Panaderia product needs no opening count and carries no product stock`() = app(setOf(Module.POS_SALES)) {
        val id = Uuid.random()
        val create = post("/catalog/products", """{"id":"$id","name":"Marraqueta","productType":"Pan","soldBy":"WEIGHT","pricePerKgMinor":2500}""")
        assertEquals(HttpStatusCode.Created, create.status)
        assertTrue(levels().isEmpty())

        val purchase = post(
            "/inventory/product-movements",
            """{"id":"${Uuid.random()}","branchId":"$branchId","productId":"$id","deltaUnits":3,"kind":"PURCHASE","occurredAt":${System.currentTimeMillis()}}"""
        )
        assertEquals("PRODUCT_NOT_ALMACEN", purchase.code())
    }

    @Test
    fun `stock is the opening count plus movements minus units sold and may go negative`() = app(setOf(Module.POS_SALES)) {
        val id = Uuid.random()
        post("/catalog/products", almacenBody(id, ""","openingStock":10"""))
        fun movement(delta: Long, kind: String) =
            """{"id":"${Uuid.random()}","branchId":"$branchId","productId":"$id","deltaUnits":$delta,"kind":"$kind","occurredAt":${System.currentTimeMillis()}}"""
        assertEquals(HttpStatusCode.Created, post("/inventory/product-movements", movement(6, "PURCHASE")).status)
        assertEquals(HttpStatusCode.Created, post("/inventory/product-movements", movement(-1, "MANUAL_ADJUSTMENT")).status)
        productStock.soldUnits[id] = 4
        assertEquals(11, stockOf(id)) // 10 + 6 - 1 - 4

        productStock.soldUnits[id] = 20
        val negative = levels().single()
        assertEquals(-5, negative["currentUnits"]!!.jsonPrimitive.content.toLong())
        assertEquals(HttpStatusCode.OK, get("/inventory/product-stock?branchId=$branchId").status, "negative stock is a warning, not an error")
    }

    @Test
    fun `OPENING_COUNT cannot be recorded through the movement endpoint`() = app(setOf(Module.POS_SALES)) {
        val id = Uuid.random()
        post("/catalog/products", almacenBody(id, ""","openingStock":1"""))
        val response = post(
            "/inventory/product-movements",
            """{"id":"${Uuid.random()}","branchId":"$branchId","productId":"$id","deltaUnits":50,"kind":"OPENING_COUNT","occurredAt":${System.currentTimeMillis()}}"""
        )
        assertEquals("INVALID_KIND", response.code())
    }

    @Test
    fun `product stock routes work without INVENTORY_FORMULAS while the ingredient routes stay gated`() = app(setOf(Module.POS_SALES)) {
        post("/catalog/products", almacenBody(Uuid.random(), ""","openingStock":3"""))
        assertEquals(HttpStatusCode.OK, get("/inventory/product-stock?branchId=$branchId").status)
        assertEquals(HttpStatusCode.OK, get("/inventory/product-movements?branchId=$branchId").status)

        for (path in listOf("/inventory/ingredients", "/inventory/stock?branchId=$branchId", "/inventory/movements?branchId=$branchId")) {
            val gated = get(path)
            assertEquals(HttpStatusCode.Forbidden, gated.status, path)
            assertEquals("MODULE_DISABLED", gated.code(), path)
        }
    }

    @Test
    fun `the opening count lands in the callers own branch and a branch-scoped caller cannot target another`() = app(setOf(Module.POS_SALES)) {
        val own = Uuid.random()
        post("/catalog/products", almacenBody(own, ""","openingStock":5"""))
        assertEquals(5, stockOf(own))

        val foreign = post("/catalog/products", almacenBody(Uuid.random(), ""","openingStock":5,"openingBranchId":"$otherBranchId""""))
        assertEquals(HttpStatusCode.Forbidden, foreign.status)
        assertEquals("BRANCH_FORBIDDEN", foreign.code())
    }

    @Test
    fun `an all-branch manager names the opening branch, and omitting it is refused`() =
        app(setOf(Module.POS_SALES), role = Role.GENERAL_MANAGER, tokenBranch = null) {
            val omitted = post("/catalog/products", almacenBody(Uuid.random(), ""","openingStock":5"""))
            assertEquals(HttpStatusCode.BadRequest, omitted.status)
            assertEquals("OPENING_BRANCH_REQUIRED", omitted.code())

            val id = Uuid.random()
            assertEquals(HttpStatusCode.Created, post("/catalog/products", almacenBody(id, ""","openingStock":5,"openingBranchId":"$otherBranchId"""")).status)
            assertEquals(5, stockOf(id, otherBranchId))
            assertEquals(0, stockOf(id, branchId), "another branch of the same product starts at zero")
        }

    @Test
    fun `retrying the same create does not count the shelf twice`() = app(setOf(Module.POS_SALES)) {
        val id = Uuid.random()
        post("/catalog/products", almacenBody(id, ""","openingStock":7"""))
        post("/catalog/products", almacenBody(id, ""","openingStock":7"""))
        assertEquals(7, stockOf(id))
    }
}
