package com.f776.ecopan.api

import com.f776.ecopan.api.core.PermissionRegistry
import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.core.mintAccessToken
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeCatalogRepository
import com.f776.ecopan.api.fakes.FakeProductStockRepository
import com.f776.ecopan.api.fakes.FakeSyncPullRepository
import com.f776.ecopan.api.fakes.FakeProductionBatchRepository
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.repository.model.PullRow
import com.f776.ecopan.data.domain.SyncEntityType
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.api.fakes.FakeRefreshTokenRepository
import com.f776.ecopan.api.fakes.FakeSaleRepository
import com.f776.ecopan.api.fakes.FakeRolePermissionRepository
import com.f776.ecopan.api.fakes.FakeStockMovementRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.api.fakes.installTestKoin
import com.f776.ecopan.api.feature.sync.controller.syncRouter
import com.f776.ecopan.data.database.repository.model.Forecast
import com.f776.ecopan.data.database.repository.model.Tenant
import kotlinx.datetime.LocalDate
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** 02 §8 AC (item 5, the frozen wire contract): idempotent re-push, branch-forbidden, pull paging. */
class SyncRouterTest {

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val otherBranchId = Uuid.random()
    private val cashierId = Uuid.random()

    private fun testConfig() = MapApplicationConfig(
        "jwt.secret" to "test-secret",
        "jwt.issuer" to "ecopan-test",
        "jwt.audience" to "ecopan-test-clients",
        "jwt.accessTokenTtlMin" to "15",
        "jwt.refreshTokenTtlDays" to "30",
        "device.sessionTtlDays" to "180",
        "reporting.tz" to "America/Santiago"
    )

    private suspend fun seedTenantAndPermissions(tenants: FakeTenantRepository) {
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        PermissionRegistry.refresh(FakeRolePermissionRepository(mapOf(Role.CASHIER to setOf(Permission.SALES_RECORD, Permission.SYNC))))
    }

    @Test
    fun `re-pushing the same mutationId returns DUPLICATE with the original serverSeq`() = testApplication {
        val tenants = FakeTenantRepository()
        val stockMovements = FakeStockMovementRepository()
        seedTenantAndPermissions(tenants)
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), stockMovements = stockMovements)
            configureAuth()
            configureSerialization()
            syncRouter()
        }

        val settings = Settings(config)
        val token = mintAccessToken(settings.jwtSettings, cashierId, tenantId, branchId, Role.CASHIER, "cashier1")
        val mutationId = Uuid.random()
        val ingredientId = Uuid.random()

        fun pushBody() = """
            {
              "deviceId": "pos-1",
              "mutations": [
                {
                  "mutationId": "$mutationId",
                  "entityType": "STOCK_MOVEMENT",
                  "opType": "CREATE",
                  "clientTimestamp": ${System.currentTimeMillis()},
                  "payload": {
                    "id": "$mutationId",
                    "branchId": "$branchId",
                    "ingredientId": "$ingredientId",
                    "deltaGrams": 5000,
                    "kind": "PURCHASE",
                    "occurredAt": ${System.currentTimeMillis()}
                  }
                }
              ]
            }
        """.trimIndent()

        val first = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(pushBody())
        }
        assertEquals(HttpStatusCode.OK, first.status)
        val firstResult = Json.parseToJsonElement(first.bodyAsText()).jsonObject["results"]!!.jsonArray.single().jsonObject
        assertEquals("APPLIED", firstResult["status"]!!.jsonPrimitive.content)
        val firstSeq = firstResult["serverSeq"]!!.jsonPrimitive.content.toLong()

        val second = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(pushBody())
        }
        assertEquals(HttpStatusCode.OK, second.status)
        val secondResult = Json.parseToJsonElement(second.bodyAsText()).jsonObject["results"]!!.jsonArray.single().jsonObject
        assertEquals("DUPLICATE", secondResult["status"]!!.jsonPrimitive.content)
        assertEquals(firstSeq, secondResult["serverSeq"]!!.jsonPrimitive.content.toLong())
    }

    /** A closed value set is enforced at ingest: a bad enum string is REJECTED with a message naming the set. */
    private fun rejectedPush(entityType: String, payload: String, expectedInMessage: String) = testApplication {
        val tenants = FakeTenantRepository()
        seedTenantAndPermissions(tenants)
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            syncRouter()
        }
        val token = mintAccessToken(Settings(config).jwtSettings, cashierId, tenantId, branchId, Role.CASHIER, "cashier1")
        val mutationId = Uuid.random()
        val response = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"deviceId":"pos-1","mutations":[{"mutationId":"$mutationId","entityType":"$entityType","opType":"CREATE",""" +
                    """"clientTimestamp":${System.currentTimeMillis()},"payload":$payload}]}"""
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject["results"]!!.jsonArray.single().jsonObject
        assertEquals("REJECTED", result["status"]!!.jsonPrimitive.content)
        val error = result["error"]!!.jsonObject
        assertEquals("VALIDATION_ERROR", error["code"]!!.jsonPrimitive.content)
        assertTrue(expectedInMessage in error["message"]!!.jsonPrimitive.content, error.toString())
    }

    private fun salePayload(paymentMethod: String, soldBy: String) =
        """{"id":"${Uuid.random()}","branchId":"$branchId","cashierId":"$cashierId","soldAt":${System.currentTimeMillis()},""" +
            """"paymentMethod":"$paymentMethod","totalMinor":500,"lines":[{"id":"${Uuid.random()}","productId":"${Uuid.random()}",""" +
            """"soldBy":"$soldBy","quantity":1,"unitPriceMinor":500,"lineTotalMinor":500}]}"""

    /**
     * #135: a database or broker hiccup says nothing about the sale. Marking it REJECTED made the
     * cashier deal with a sale that was fine; a 5xx leaves it pending on the till, which retries.
     */
    @Test
    fun `an infrastructure failure during push is a 503, not a REJECTED item`() = testApplication {
        val tenants = FakeTenantRepository()
        seedTenantAndPermissions(tenants)
        val sales = FakeSaleRepository().apply { failWith = SQLException("connection reset", "08006") }
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), sales = sales)
            configureAuth()
            configureSerialization()
            syncRouter()
        }
        val token = mintAccessToken(Settings(config).jwtSettings, cashierId, tenantId, branchId, Role.CASHIER, "cashier1")
        val response = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"deviceId":"pos-1","mutations":[{"mutationId":"${Uuid.random()}","entityType":"SALE","opType":"CREATE",""" +
                    """"clientTimestamp":${System.currentTimeMillis()},"payload":${salePayload("CASH", "UNIT")}}]}"""
            )
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertTrue("REJECTED" !in response.bodyAsText(), response.bodyAsText())
        assertEquals("SYNC_UNAVAILABLE", Json.parseToJsonElement(response.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a domain failure that is not infrastructure is still REJECTED for that item, not a 503`() = testApplication {
        val tenants = FakeTenantRepository()
        seedTenantAndPermissions(tenants)
        val sales = FakeSaleRepository().apply { failWith = IllegalStateException("cashier is not in this branch") }
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), sales = sales)
            configureAuth()
            configureSerialization()
            syncRouter()
        }
        val token = mintAccessToken(Settings(config).jwtSettings, cashierId, tenantId, branchId, Role.CASHIER, "cashier1")
        val response = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"deviceId":"pos-1","mutations":[{"mutationId":"${Uuid.random()}","entityType":"SALE","opType":"CREATE",""" +
                    """"clientTimestamp":${System.currentTimeMillis()},"payload":${salePayload("CASH", "UNIT")}}]}"""
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject["results"]!!.jsonArray.single().jsonObject
        assertEquals("REJECTED", result["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a malformed payload is still REJECTED for that item`() = rejectedPush(
        "SALE",
        """{"id":"not-a-uuid","branchId":"$branchId","cashierId":"$cashierId","soldAt":0,"paymentMethod":"CASH","totalMinor":1,"lines":[]}""",
        "id is not a valid uuid",
    )

    @Test
    fun `a sale with an unknown payment method is REJECTED naming the allowed set`() =
        rejectedPush("SALE", salePayload("BITCOIN", "UNIT"), "CASH, CARD, TRANSFER, OTHER")

    @Test
    fun `a sale line with an unknown soldBy is REJECTED naming the allowed set`() =
        rejectedPush("SALE", salePayload("CASH", "BY_THE_YARD"), "WEIGHT, UNIT")

    @Test
    fun `a stock movement with an unknown kind is REJECTED`() = rejectedPush(
        "STOCK_MOVEMENT",
        """{"id":"${Uuid.random()}","branchId":"$branchId","ingredientId":"${Uuid.random()}","deltaGrams":5,"kind":"GIFT","occurredAt":${System.currentTimeMillis()}}""",
        "GIFT"
    )

    @Test
    fun `a production batch for an ALMACEN product is REJECTED and a PANADERIA one is applied`() = testApplication {
        val tenants = FakeTenantRepository()
        val catalog = FakeCatalogRepository()
        val batches = FakeProductionBatchRepository()
        PermissionRegistry.refresh(FakeRolePermissionRepository(mapOf(Role.BAKER to setOf(Permission.SYNC))))
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val bakedId = Uuid.random()
        val boughtId = Uuid.random()
        catalog.createProduct(bakedId, tenantId, "Marraqueta", "Pan", SoldBy.WEIGHT, 2500, null, null, null, ProductOrigin.PANADERIA)
        catalog.createProduct(boughtId, tenantId, "Bebida cola", "Bebidas", SoldBy.UNIT, null, 1500, null, null, ProductOrigin.ALMACEN)
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(
                config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(),
                catalog = catalog, productionBatches = batches
            )
            configureAuth()
            configureSerialization()
            syncRouter()
        }

        val token = mintAccessToken(Settings(config).jwtSettings, cashierId, tenantId, branchId, Role.BAKER, "baker1")
        suspend fun push(productId: Uuid): kotlinx.serialization.json.JsonObject {
            val id = Uuid.random()
            val response = client.post("/sync/push") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(
                    """{"deviceId":"mobile-1","mutations":[{"mutationId":"$id","entityType":"PRODUCTION_BATCH","opType":"CREATE",""" +
                        """"clientTimestamp":${System.currentTimeMillis()},"payload":{"id":"$id","branchId":"$branchId","bakerId":"$cashierId",""" +
                        """"productId":"$productId","mixWeightGrams":10000,"batchAt":${System.currentTimeMillis()},"movementsMinted":false}}]}"""
                )
            }
            assertEquals(HttpStatusCode.OK, response.status)
            return Json.parseToJsonElement(response.bodyAsText()).jsonObject["results"]!!.jsonArray.single().jsonObject
        }

        val rejected = push(boughtId)
        assertEquals("REJECTED", rejected["status"]!!.jsonPrimitive.content)
        assertEquals("PRODUCTION_REQUIRES_PANADERIA", rejected["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
        assertTrue(batches.batches.isEmpty())

        assertEquals("APPLIED", push(bakedId)["status"]!!.jsonPrimitive.content)
        assertEquals(1, batches.batches.size)
    }

    @Test
    fun `pushing for another branch is REJECTED with BRANCH_FORBIDDEN`() = testApplication {
        val tenants = FakeTenantRepository()
        seedTenantAndPermissions(tenants)
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            syncRouter()
        }

        val settings = Settings(config)
        val token = mintAccessToken(settings.jwtSettings, cashierId, tenantId, branchId, Role.CASHIER, "cashier1")
        val mutationId = Uuid.random()

        val response = client.post("/sync/push") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "deviceId": "pos-1",
                  "mutations": [
                    {
                      "mutationId": "$mutationId",
                      "entityType": "STOCK_MOVEMENT",
                      "opType": "CREATE",
                      "clientTimestamp": ${System.currentTimeMillis()},
                      "payload": {
                        "id": "$mutationId",
                        "branchId": "$otherBranchId",
                        "ingredientId": "${Uuid.random()}",
                        "deltaGrams": 5000,
                        "kind": "PURCHASE",
                        "occurredAt": ${System.currentTimeMillis()}
                      }
                    }
                  ]
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val result = Json.parseToJsonElement(response.bodyAsText()).jsonObject["results"]!!.jsonArray.single().jsonObject
        assertEquals("REJECTED", result["status"]!!.jsonPrimitive.content)
        assertEquals("BRANCH_FORBIDDEN", result["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun `pull without SYNC permission 403s`() = testApplication {
        PermissionRegistry.refresh(FakeRolePermissionRepository(mapOf(Role.CASHIER to setOf(Permission.SALES_RECORD))))
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            syncRouter()
        }

        val settings = Settings(config)
        val token = mintAccessToken(settings.jwtSettings, cashierId, tenantId, branchId, Role.CASHIER, "cashier1")

        val response = client.get("/sync/pull?since=0&limit=10") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `pull carries Almacen stock for the principal's branch and the reorder level`() = testApplication {
        val tenants = FakeTenantRepository()
        val catalog = FakeCatalogRepository()
        val productStock = FakeProductStockRepository()
        val syncPull = FakeSyncPullRepository()
        seedTenantAndPermissions(tenants)
        val colaId = Uuid.random()
        val breadId = Uuid.random()
        val cola = catalog.createProduct(colaId, tenantId, "Bebida cola", "Bebidas", SoldBy.UNIT, null, 1500, null, null, ProductOrigin.ALMACEN)
            .copy(lowStockThresholdUnits = 5)
        val bread = catalog.createProduct(breadId, tenantId, "Marraqueta", "Pan", SoldBy.UNIT, null, 300, null, null, ProductOrigin.PANADERIA)
            .copy(lowStockThresholdUnits = 9)
        syncPull.rows += PullRow(SyncEntityType.PRODUCT.name, 1, cola)
        syncPull.rows += PullRow(SyncEntityType.PRODUCT.name, 2, bread)
        val now = Clock.System.now()
        productStock.append(Uuid.random(), tenantId, branchId, colaId, 10, ProductStockMovementKind.OPENING_COUNT, null, null, now)
        productStock.append(Uuid.random(), tenantId, branchId, colaId, 4, ProductStockMovementKind.PURCHASE, null, null, now)
        // Another branch's count must not leak into this branch's number.
        productStock.append(Uuid.random(), tenantId, otherBranchId, colaId, 99, ProductStockMovementKind.OPENING_COUNT, null, null, now)
        productStock.soldUnits[colaId] = 20 // more sold than counted: stock goes negative, never blocks
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(
                config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(),
                catalog = catalog, productStock = productStock, syncPull = syncPull
            )
            configureAuth()
            configureSerialization()
            syncRouter()
        }

        suspend fun pulled(branch: Uuid?): Map<String, kotlinx.serialization.json.JsonObject> {
            val token = mintAccessToken(Settings(config).jwtSettings, cashierId, tenantId, branch, Role.CASHIER, "cashier1")
            val response = client.get("/sync/pull?since=0&limit=10") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.OK, response.status)
            return Json.parseToJsonElement(response.bodyAsText()).jsonObject["changes"]!!.jsonArray
                .map { it.jsonObject["payload"]!!.jsonObject }
                .associateBy { it["id"]!!.jsonPrimitive.content }
        }

        val inBranch = pulled(branchId)
        assertEquals("-6", inBranch.getValue(colaId.toString())["stockUnits"]!!.jsonPrimitive.content)
        assertEquals("5", inBranch.getValue(colaId.toString())["lowStockThresholdUnits"]!!.jsonPrimitive.content)
        assertEquals("ALMACEN", inBranch.getValue(colaId.toString())["origin"]!!.jsonPrimitive.content)
        assertTrue("stockUnits" !in inBranch.getValue(breadId.toString()), "Panadería carries no stock")
        assertTrue("lowStockThresholdUnits" !in inBranch.getValue(breadId.toString()))

        // An all-branch principal has no branch to count in.
        assertTrue("stockUnits" !in pulled(null).getValue(colaId.toString()))
    }

    @Test
    fun `pull carries the branch's forecast rows only for a DEMAND_FORECASTING tenant`() = testApplication {
        val tenants = FakeTenantRepository()
        val syncPull = FakeSyncPullRepository()
        val forecasting = Tenant(
            tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES, Module.DEMAND_FORECASTING), Clock.System.now()
        )
        tenants.seed(forecasting)
        PermissionRegistry.refresh(FakeRolePermissionRepository(mapOf(Role.CASHIER to setOf(Permission.SALES_RECORD, Permission.SYNC))))
        val productId = Uuid.random()
        fun forecast(branch: Uuid, seq: Long) = Forecast(
            Uuid.random(), tenantId, branch, productId, "Marraqueta", "Pan", LocalDate(2026, 7, 30),
            12_500, null, "baseline-trailing-mean", Clock.System.now(), seq
        )
        syncPull.rows += PullRow(SyncEntityType.FORECAST.name, 1, forecast(branchId, 1))
        syncPull.rows += PullRow(SyncEntityType.FORECAST.name, 2, forecast(otherBranchId, 2))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(
                config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), syncPull = syncPull
            )
            configureAuth()
            configureSerialization()
            syncRouter()
        }

        suspend fun pulled(): List<kotlinx.serialization.json.JsonObject> {
            val token = mintAccessToken(Settings(config).jwtSettings, cashierId, tenantId, branchId, Role.CASHIER, "cashier1")
            val response = client.get("/sync/pull?since=0&limit=10") { header(HttpHeaders.Authorization, "Bearer $token") }
            assertEquals(HttpStatusCode.OK, response.status)
            return Json.parseToJsonElement(response.bodyAsText()).jsonObject["changes"]!!.jsonArray.map { it.jsonObject }
        }

        val change = pulled().single()
        assertEquals("FORECAST", change["entityType"]!!.jsonPrimitive.content)
        val payload = change["payload"]!!.jsonObject
        assertEquals(branchId.toString(), payload["branchId"]!!.jsonPrimitive.content)
        assertEquals("Marraqueta", payload["productName"]!!.jsonPrimitive.content)
        assertEquals("2026-07-30", payload["forecastDate"]!!.jsonPrimitive.content)
        assertEquals("12500", payload["predictedGrams"]!!.jsonPrimitive.content)
        assertEquals("baseline-trailing-mean", payload["modelVersion"]!!.jsonPrimitive.content)

        // Module switched off: the cached rows stay in the table but stop reaching devices.
        tenants.updateEnabledModules(tenantId, setOf(Module.POS_SALES))
        assertTrue(pulled().isEmpty())
    }
}
