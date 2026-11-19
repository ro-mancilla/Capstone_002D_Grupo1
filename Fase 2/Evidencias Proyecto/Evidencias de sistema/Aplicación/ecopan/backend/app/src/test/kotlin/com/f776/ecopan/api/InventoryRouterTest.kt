package com.f776.ecopan.api

import com.f776.ecopan.api.core.PermissionRegistry
import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.core.mintAccessToken
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeCatalogRepository
import com.f776.ecopan.api.fakes.FakeIngredientRepository
import com.f776.ecopan.api.fakes.FakeProductStockRepository
import com.f776.ecopan.api.fakes.FakeRefreshTokenRepository
import com.f776.ecopan.api.fakes.FakeRolePermissionRepository
import com.f776.ecopan.api.fakes.FakeStockMovementRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.api.fakes.installTestKoin
import com.f776.ecopan.api.feature.inventory.controller.inventoryRouter
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.f776.ecopan.data.database.entity.StockMovementKind
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class InventoryRouterTest {

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val otherBranchId = Uuid.random()
    private val gmId = Uuid.random()
    private val branchManagerId = Uuid.random()

    private fun testConfig() = MapApplicationConfig(
        "jwt.secret" to "test-secret",
        "jwt.issuer" to "ecopan-test",
        "jwt.audience" to "ecopan-test-clients",
        "jwt.accessTokenTtlMin" to "15",
        "jwt.refreshTokenTtlDays" to "30",
        "device.sessionTtlDays" to "180",
        "reporting.tz" to "America/Santiago"
    )

    @Test
    fun `I1 list ingredients 403s MODULE_DISABLED when INVENTORY_FORMULAS is off`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.CATALOG_READ, Permission.STOCK_READ, Permission.STOCK_ADJUST)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            inventoryRouter()
        }

        val settings = Settings(config)
        val gmToken = mintAccessToken(settings.jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")

        val response = client.get("/inventory/ingredients") { header(HttpHeaders.Authorization, "Bearer $gmToken") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("MODULE_DISABLED", body["code"]!!.jsonPrimitive.content)
        assertEquals("INVENTORY_FORMULAS", body["module"]!!.jsonPrimitive.content)
    }

    @Test
    fun `I5 append movement is BRANCH_FORBIDDEN for a branch-scoped principal in another branch`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.BRANCH_MANAGER to setOf(Permission.CATALOG_READ, Permission.STOCK_READ, Permission.STOCK_ADJUST)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES, Module.INVENTORY_FORMULAS), Clock.System.now()))
        val ingredients = FakeIngredientRepository()
        val ingredient = kotlinx.coroutines.runBlocking { ingredients.create(Uuid.random(), tenantId, "Flour", null) }
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), ingredients = ingredients)
            configureAuth()
            configureSerialization()
            inventoryRouter()
        }

        val settings = Settings(config)
        // Principal is scoped to `branchId`, but the request targets `otherBranchId`.
        val branchManagerToken = mintAccessToken(settings.jwtSettings, branchManagerId, tenantId, branchId, Role.BRANCH_MANAGER, "bm1")

        val response = client.post("/inventory/movements") {
            header(HttpHeaders.Authorization, "Bearer $branchManagerToken")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"${Uuid.random()}","branchId":"$otherBranchId","ingredientId":"${ingredient.id}",""" +
                    """"deltaGrams":5000,"kind":"PURCHASE","occurredAt":${System.currentTimeMillis()}}"""
            )
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("BRANCH_FORBIDDEN", body["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `I5 append movement for the principal's own branch bumps server_seq`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.BRANCH_MANAGER to setOf(Permission.CATALOG_READ, Permission.STOCK_READ, Permission.STOCK_ADJUST)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES, Module.INVENTORY_FORMULAS), Clock.System.now()))
        val ingredients = FakeIngredientRepository()
        val ingredient = kotlinx.coroutines.runBlocking { ingredients.create(Uuid.random(), tenantId, "Flour", null) }
        val stockMovements = FakeStockMovementRepository()
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(
                config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(),
                ingredients = ingredients, stockMovements = stockMovements
            )
            configureAuth()
            configureSerialization()
            inventoryRouter()
        }

        val settings = Settings(config)
        val branchManagerToken = mintAccessToken(settings.jwtSettings, branchManagerId, tenantId, branchId, Role.BRANCH_MANAGER, "bm1")

        val response = client.post("/inventory/movements") {
            header(HttpHeaders.Authorization, "Bearer $branchManagerToken")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"${Uuid.random()}","branchId":"$branchId","ingredientId":"${ingredient.id}",""" +
                    """"deltaGrams":5000,"kind":"PURCHASE","occurredAt":${System.currentTimeMillis()}}"""
            )
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val seq = Json.parseToJsonElement(response.bodyAsText()).jsonObject["serverSeq"]!!.jsonPrimitive.content.toLong()
        assert(seq > 0)
    }

    @Test
    fun `a movement kind outside the set is rejected with INVALID_KIND on both ledgers`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.BRANCH_MANAGER to setOf(Permission.CATALOG_READ, Permission.STOCK_READ, Permission.STOCK_ADJUST)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES, Module.INVENTORY_FORMULAS), Clock.System.now()))
        val catalog = FakeCatalogRepository()
        val cola = kotlinx.coroutines.runBlocking {
            catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null, origin = com.f776.ecopan.data.database.entity.ProductOrigin.ALMACEN)
        }
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), catalog = catalog)
            configureAuth()
            configureSerialization()
            inventoryRouter()
        }
        val token = mintAccessToken(Settings(config).jwtSettings, branchManagerId, tenantId, branchId, Role.BRANCH_MANAGER, "bm1")

        suspend fun post(path: String, body: String) = client.post(path) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val now = System.currentTimeMillis()
        for (kind in listOf("GIFT", "purchase", "FORMULA_DECREMENT")) {
            val ingredient = post(
                "/inventory/movements",
                """{"id":"${Uuid.random()}","branchId":"$branchId","ingredientId":"${Uuid.random()}","deltaGrams":5,"kind":"$kind","occurredAt":$now}"""
            )
            assertEquals(HttpStatusCode.BadRequest, ingredient.status, kind)
            assertEquals("INVALID_KIND", Json.parseToJsonElement(ingredient.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)

            val product = post(
                "/inventory/product-movements",
                """{"id":"${Uuid.random()}","branchId":"$branchId","productId":"${cola.id}","deltaUnits":5,"kind":"$kind","occurredAt":$now}"""
            )
            assertEquals(HttpStatusCode.BadRequest, product.status, kind)
            assertEquals("INVALID_KIND", Json.parseToJsonElement(product.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `product stock nets units sold and refuses products that are not Almacén`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.BRANCH_MANAGER to setOf(Permission.CATALOG_READ, Permission.STOCK_READ, Permission.STOCK_ADJUST)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES, Module.INVENTORY_FORMULAS), Clock.System.now()))
        val catalog = FakeCatalogRepository()
        val cola = kotlinx.coroutines.runBlocking {
            catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null, origin = com.f776.ecopan.data.database.entity.ProductOrigin.ALMACEN)
        }
        val marraqueta = kotlinx.coroutines.runBlocking {
            catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "Pan", SoldBy.WEIGHT, 2500, null, null)
        }
        val productStock = FakeProductStockRepository()
        productStock.soldUnits[cola.id] = 5
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(
                config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(),
                catalog = catalog, productStock = productStock
            )
            configureAuth()
            configureSerialization()
            inventoryRouter()
        }

        val settings = Settings(config)
        val token = mintAccessToken(settings.jwtSettings, branchManagerId, tenantId, branchId, Role.BRANCH_MANAGER, "bm1")

        val purchase = client.post("/inventory/product-movements") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"${Uuid.random()}","branchId":"$branchId","productId":"${cola.id}",""" +
                    """"deltaUnits":24,"kind":"PURCHASE","note":"Factura 7","occurredAt":${System.currentTimeMillis()}}"""
            )
        }
        assertEquals(HttpStatusCode.Created, purchase.status)

        val weightAttempt = client.post("/inventory/product-movements") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"${Uuid.random()}","branchId":"$branchId","productId":"${marraqueta.id}",""" +
                    """"deltaUnits":3,"kind":"PURCHASE","occurredAt":${System.currentTimeMillis()}}"""
            )
        }
        assertEquals(HttpStatusCode.BadRequest, weightAttempt.status)
        assertEquals("PRODUCT_NOT_ALMACEN", Json.parseToJsonElement(weightAttempt.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)

        val levels = client.get("/inventory/product-stock?branchId=$branchId") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, levels.status)
        val row = Json.parseToJsonElement(levels.bodyAsText()).jsonArray.single().jsonObject
        assertEquals("Bebida cola 500ml", row["name"]!!.jsonPrimitive.content)
        assertEquals(24, row["receivedUnits"]!!.jsonPrimitive.content.toLong())
        assertEquals(5, row["soldUnits"]!!.jsonPrimitive.content.toLong())
        assertEquals(19, row["currentUnits"]!!.jsonPrimitive.content.toLong())

        val ledger = client.get("/inventory/product-movements?branchId=$branchId&productId=${cola.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, ledger.status)
        assertEquals(1, Json.parseToJsonElement(ledger.bodyAsText()).jsonArray.size)
    }

    @Test
    fun `an Almacén product can carry a reorder level that flags low stock, and Panadería products cannot`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.BRANCH_MANAGER to setOf(Permission.CATALOG_READ, Permission.STOCK_READ, Permission.STOCK_ADJUST)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES, Module.INVENTORY_FORMULAS), Clock.System.now()))
        val catalog = FakeCatalogRepository()
        val cola = kotlinx.coroutines.runBlocking {
            catalog.createProduct(Uuid.random(), tenantId, "Bebida cola 500ml", "Bebidas", SoldBy.UNIT, null, 1500, null, origin = com.f776.ecopan.data.database.entity.ProductOrigin.ALMACEN)
        }
        val marraqueta = kotlinx.coroutines.runBlocking {
            catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "Pan", SoldBy.WEIGHT, 2500, null, null)
        }
        val productStock = FakeProductStockRepository()
        productStock.soldUnits[cola.id] = 20
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(
                config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(),
                catalog = catalog, productStock = productStock
            )
            configureAuth()
            configureSerialization()
            inventoryRouter()
        }

        val token = mintAccessToken(Settings(config).jwtSettings, branchManagerId, tenantId, branchId, Role.BRANCH_MANAGER, "bm1")
        suspend fun levelRow() = Json.parseToJsonElement(
            client.get("/inventory/product-stock?branchId=$branchId") { header(HttpHeaders.Authorization, "Bearer $token") }.bodyAsText()
        ).jsonArray.single().jsonObject
        suspend fun putThreshold(productId: Uuid, body: String) = client.put("/inventory/product-stock/$productId/threshold") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        client.post("/inventory/product-movements") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"id":"${Uuid.random()}","branchId":"$branchId","productId":"${cola.id}",""" +
                    """"deltaUnits":24,"kind":"PURCHASE","occurredAt":${System.currentTimeMillis()}}"""
            )
        }

        // 24 received, 20 sold: 4 left. No level set yet, so no alert.
        assertEquals(false, levelRow()["lowStock"]!!.jsonPrimitive.content.toBoolean())

        assertEquals(HttpStatusCode.OK, putThreshold(cola.id, """{"thresholdUnits":10}""").status)
        val flagged = levelRow()
        assertEquals(10, flagged["lowStockThresholdUnits"]!!.jsonPrimitive.content.toLong())
        assertEquals(true, flagged["lowStock"]!!.jsonPrimitive.content.toBoolean())

        // At the level is low too: the till's badge uses the same "at or under" rule, so the two never disagree.
        assertEquals(HttpStatusCode.OK, putThreshold(cola.id, """{"thresholdUnits":4}""").status)
        assertEquals(true, levelRow()["lowStock"]!!.jsonPrimitive.content.toBoolean())

        // Above the level is not low.
        assertEquals(HttpStatusCode.OK, putThreshold(cola.id, """{"thresholdUnits":3}""").status)
        assertEquals(false, levelRow()["lowStock"]!!.jsonPrimitive.content.toBoolean())

        // null clears it.
        assertEquals(HttpStatusCode.OK, putThreshold(cola.id, """{"thresholdUnits":null}""").status)
        assertEquals(kotlinx.serialization.json.JsonNull, levelRow()["lowStockThresholdUnits"])

        val negative = putThreshold(cola.id, """{"thresholdUnits":-1}""")
        assertEquals(HttpStatusCode.BadRequest, negative.status)
        assertEquals("INVALID_THRESHOLD", Json.parseToJsonElement(negative.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)

        val weight = putThreshold(marraqueta.id, """{"thresholdUnits":5}""")
        assertEquals(HttpStatusCode.BadRequest, weight.status)
        assertEquals("PRODUCT_NOT_ALMACEN", Json.parseToJsonElement(weight.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)
    }

    private fun seededReconciliationApp(
        permissions: Set<Permission> = setOf(Permission.CATALOG_READ, Permission.STOCK_READ, Permission.STOCK_ADJUST),
        /** Extra `(ingredientId, grams, at)` purchases; an id that is not a catalog ingredient models an orphaned ledger row. */
        extraPurchases: List<Triple<Uuid, Long, String>> = emptyList(),
        /** Ingredients the two seeded in-window batches ALSO decrement (one batch, several ingredients). */
        alsoDecremented: List<Uuid> = emptyList(),
        block: suspend io.ktor.server.testing.ApplicationTestBuilder.(flour: Uuid, token: String) -> Unit
    ) = testApplication {
        PermissionRegistry.refresh(FakeRolePermissionRepository(mapOf(Role.BRANCH_MANAGER to permissions)))
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES, Module.INVENTORY_FORMULAS), Clock.System.now()))
        val ingredients = FakeIngredientRepository()
        val flour = kotlinx.coroutines.runBlocking { ingredients.create(Uuid.random(), tenantId, "Flour", null) }
        val stockMovements = FakeStockMovementRepository()
        kotlinx.coroutines.runBlocking {
            fun add(grams: Long, kind: StockMovementKind, at: String, batch: Uuid? = null) = kotlinx.coroutines.runBlocking {
                stockMovements.append(Uuid.random(), tenantId, branchId, flour.id, grams, kind, batch, null, null, Instant.parse(at))
            }
            add(10_000, StockMovementKind.PURCHASE, "2026-03-01T12:00:00Z")
            // 02:30Z on 10 Mar is 23:30 on 9 Mar in America/Santiago (UTC-3): still BEFORE the window.
            add(-1_000, StockMovementKind.MANUAL_ADJUSTMENT, "2026-03-10T02:30:00Z")
            add(4_000, StockMovementKind.PURCHASE, "2026-03-10T12:00:00Z")
            val batchA = Uuid.random()
            val batchB = Uuid.random()
            add(-1_500, StockMovementKind.FORMULA_DECREMENT, "2026-03-11T12:00:00Z", batchA)
            add(-500, StockMovementKind.FORMULA_DECREMENT, "2026-03-12T12:00:00Z", batchB)
            alsoDecremented.forEach { ingredientId ->
                listOf(batchA to "2026-03-11T12:00:00Z", batchB to "2026-03-12T12:00:00Z").forEach { (batch, at) ->
                    stockMovements.append(Uuid.random(), tenantId, branchId, ingredientId, -10, StockMovementKind.FORMULA_DECREMENT, batch, null, null, Instant.parse(at))
                }
            }
            add(-200, StockMovementKind.MANUAL_ADJUSTMENT, "2026-03-12T15:00:00Z")
            // 02:30Z on 13 Mar is still 12 Mar locally: INSIDE a window ending 2026-03-12.
            add(300, StockMovementKind.PURCHASE, "2026-03-13T02:30:00Z")
            add(7_000, StockMovementKind.PURCHASE, "2026-03-14T12:00:00Z")
            extraPurchases.forEach { (ingredientId, grams, at) ->
                stockMovements.append(Uuid.random(), tenantId, branchId, ingredientId, grams, StockMovementKind.PURCHASE, null, null, null, Instant.parse(at))
            }
        }
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(
                config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(),
                ingredients = ingredients, stockMovements = stockMovements
            )
            configureAuth()
            configureSerialization()
            inventoryRouter()
        }
        val token = mintAccessToken(Settings(config).jwtSettings, branchManagerId, tenantId, branchId, Role.BRANCH_MANAGER, "bm1")
        block(flour.id, token)
    }

    @Test
    fun `reconciliation balances opening plus movements to closing using reporting-timezone day bounds`() =
        seededReconciliationApp { flourId, token ->
            val response = client.get("/inventory/reconciliation?branchId=$branchId&from=2026-03-10&to=2026-03-12") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val row = Json.parseToJsonElement(response.bodyAsText()).jsonObject["rows"]!!.jsonArray.single().jsonObject
            fun long(key: String) = row[key]!!.jsonPrimitive.content.toLong()
            assertEquals(flourId.toString(), row["ingredientId"]!!.jsonPrimitive.content)
            assertEquals("Flour", row["name"]!!.jsonPrimitive.content)
            assertEquals(9_000, long("openingGrams"))
            assertEquals(4_300, long("purchasedGrams"))
            assertEquals(-2_000, long("decrementedGrams"))
            assertEquals(-200, long("adjustedGrams"))
            assertEquals(2, long("batchCount"))
            assertEquals(2, Json.parseToJsonElement(response.bodyAsText()).jsonObject["batchCount"]!!.jsonPrimitive.content.toLong())
            assertEquals(11_100, long("closingGrams"))
            assertEquals(
                long("openingGrams") + long("purchasedGrams") + long("decrementedGrams") + long("adjustedGrams"),
                long("closingGrams")
            )
        }

    @Test
    fun `reconciliation batchCount is distinct batches in the period, not the sum of the per-ingredient counts`() {
        val salt = Uuid.random()
        val yeast = Uuid.random()
        seededReconciliationApp(alsoDecremented = listOf(salt, yeast)) { _, token ->
            val response = client.get("/inventory/reconciliation?branchId=$branchId&from=2026-03-10&to=2026-03-12") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val rows = body["rows"]!!.jsonArray.map { it.jsonObject }
            // Flour, salt and yeast each report both batches...
            assertEquals(listOf(2L, 2L, 2L), rows.map { it["batchCount"]!!.jsonPrimitive.content.toLong() })
            // ...but only two batches happened.
            assertEquals(2L, body["batchCount"]!!.jsonPrimitive.content.toLong())
        }
    }

    @Test
    fun `reconciliation closing balance through the last movement day equals current stock`() =
        seededReconciliationApp { flourId, token ->
            val report = client.get("/inventory/reconciliation?branchId=$branchId&from=2026-03-01&to=2026-12-31") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val stock = client.get("/inventory/stock?branchId=$branchId") { header(HttpHeaders.Authorization, "Bearer $token") }
            val closing = Json.parseToJsonElement(report.bodyAsText()).jsonObject["rows"]!!.jsonArray.single().jsonObject["closingGrams"]!!.jsonPrimitive.content.toLong()
            val current = Json.parseToJsonElement(stock.bodyAsText()).jsonArray.single { it.jsonObject["ingredientId"]!!.jsonPrimitive.content == flourId.toString() }
                .jsonObject["currentGrams"]!!.jsonPrimitive.content.toLong()
            assertEquals(current, closing)
        }

    @Test
    fun `reconciliation uses the winter UTC-4 offset for day bounds too`() =
        seededReconciliationApp { flourId, token ->
            // Local midnight on 10 Jul is 04:00Z. Nothing seeded lands in July, so the whole March
            // history is opening and the window itself is empty.
            val response = client.get("/inventory/reconciliation?branchId=$branchId&from=2026-07-10&to=2026-07-10") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val flour = Json.parseToJsonElement(response.bodyAsText()).jsonObject["rows"]!!.jsonArray
                .map { it.jsonObject }.single { it["ingredientId"]!!.jsonPrimitive.content == flourId.toString() }
            assertEquals(18_100, flour["openingGrams"]!!.jsonPrimitive.content.toLong())
            assertEquals(0, flour["purchasedGrams"]!!.jsonPrimitive.content.toLong())
        }

    @Test
    fun `reconciliation puts a 03-30Z winter purchase on the previous local day and a 04-00Z one on the day`() {
        val ingredient = Uuid.random()
        seededReconciliationApp(
            extraPurchases = listOf(Triple(ingredient, 7L, "2026-07-10T03:30:00Z"), Triple(ingredient, 70L, "2026-07-10T04:00:00Z"))
        ) { _, token ->
            val response = client.get("/inventory/reconciliation?branchId=$branchId&from=2026-07-10&to=2026-07-10") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val row = Json.parseToJsonElement(response.bodyAsText()).jsonObject["rows"]!!.jsonArray
                .map { it.jsonObject }.single { it["ingredientId"]!!.jsonPrimitive.content == ingredient.toString() }
            assertEquals(7, row["openingGrams"]!!.jsonPrimitive.content.toLong())
            assertEquals(70, row["purchasedGrams"]!!.jsonPrimitive.content.toLong())
        }
    }

    @Test
    fun `reconciliation caps the span at 366 days and rejects absurd dates instead of failing with a 500`() =
        seededReconciliationApp { _, token ->
            suspend fun get(query: String) = client.get("/inventory/reconciliation?branchId=$branchId&$query") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            suspend fun codeOf(query: String): String {
                val response = get(query)
                assertEquals(HttpStatusCode.BadRequest, response.status, query)
                return Json.parseToJsonElement(response.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content
            }
            assertEquals(HttpStatusCode.OK, get("from=2025-03-01&to=2026-03-01").status) // 366 days inclusive
            assertEquals("INVALID_DATE_RANGE", codeOf("from=2025-02-28&to=2026-03-01")) // 367
            assertEquals("INVALID_DATE_RANGE", codeOf("from=+999999999-01-01&to=+999999999-12-31"))
            assertEquals("INVALID_DATE_RANGE", codeOf("from=-999999999-01-01&to=2026-01-01"))
            assertEquals("INVALID_DATE_RANGE", codeOf("from=0001-01-01&to=0001-01-02"))
            assertEquals("INVALID_DATE_RANGE", codeOf("from=9999-12-30&to=9999-12-31"))
            assertEquals("INVALID_DATE_RANGE", codeOf("from=2026-02-30&to=2026-03-01")) // not a real day
        }

    @Test
    fun `reconciliation keeps a ledger row whose ingredient is missing from the catalog, with an empty name and never its id`() {
        val orphan = Uuid.random()
        seededReconciliationApp(extraPurchases = listOf(Triple(orphan, 123L, "2026-03-11T12:00:00Z"))) { _, token ->
            val response = client.get("/inventory/reconciliation?branchId=$branchId&from=2026-03-10&to=2026-03-12") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            val rows = Json.parseToJsonElement(response.bodyAsText()).jsonObject["rows"]!!.jsonArray.map { it.jsonObject }
            val row = rows.single { it["ingredientId"]!!.jsonPrimitive.content == orphan.toString() }
            // The web app shows a localized "unnamed" label for an empty name; an id is never user text.
            assertEquals("", row["name"]!!.jsonPrimitive.content)
            assertEquals(123, row["closingGrams"]!!.jsonPrimitive.content.toLong())
        }
    }

    @Test
    fun `reconciliation 403s MODULE_DISABLED when INVENTORY_FORMULAS is off`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.CATALOG_READ, Permission.STOCK_READ)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            inventoryRouter()
        }

        val token = mintAccessToken(Settings(config).jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")
        val response = client.get("/inventory/reconciliation?branchId=$branchId&from=2026-03-01&to=2026-03-02") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("MODULE_DISABLED", body["code"]!!.jsonPrimitive.content)
        assertEquals("INVENTORY_FORMULAS", body["module"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reconciliation rejects a missing branch, an unparseable date and an inverted range`() =
        seededReconciliationApp { _, token ->
            suspend fun codeOf(query: String): Pair<HttpStatusCode, String> {
                val response = client.get("/inventory/reconciliation?$query") { header(HttpHeaders.Authorization, "Bearer $token") }
                return response.status to Json.parseToJsonElement(response.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content
            }
            assertEquals(HttpStatusCode.BadRequest to "BRANCH_ID_REQUIRED", codeOf("from=2026-03-01&to=2026-03-02"))
            assertEquals(HttpStatusCode.BadRequest to "INVALID_DATE_RANGE", codeOf("branchId=$branchId&from=nope&to=2026-03-02"))
            assertEquals(HttpStatusCode.BadRequest to "INVALID_DATE_RANGE", codeOf("branchId=$branchId&from=2026-03-05&to=2026-03-02"))
        }

    @Test
    fun `reconciliation is BRANCH_FORBIDDEN for another branch and 403 without STOCK_READ`() {
        seededReconciliationApp { _, token ->
            val response = client.get("/inventory/reconciliation?branchId=$otherBranchId&from=2026-03-01&to=2026-03-02") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
            assertEquals("BRANCH_FORBIDDEN", Json.parseToJsonElement(response.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)
        }
        seededReconciliationApp(permissions = setOf(Permission.CATALOG_READ)) { _, token ->
            val response = client.get("/inventory/reconciliation?branchId=$branchId&from=2026-03-01&to=2026-03-02") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }
            assertEquals(HttpStatusCode.Forbidden, response.status)
        }
    }
}
