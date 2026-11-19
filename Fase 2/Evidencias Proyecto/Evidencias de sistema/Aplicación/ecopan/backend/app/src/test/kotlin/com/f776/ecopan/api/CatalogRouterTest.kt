package com.f776.ecopan.api

import com.f776.ecopan.api.core.PermissionRegistry
import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.core.mintAccessToken
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeCatalogRepository
import com.f776.ecopan.api.fakes.FakeRefreshTokenRepository
import com.f776.ecopan.api.fakes.FakeRolePermissionRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.InMemoryObjectStorage
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.api.fakes.installTestKoin
import com.f776.ecopan.api.feature.catalog.controller.catalogRouter
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import io.ktor.client.request.delete
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class CatalogRouterTest {

    private val tenantId = Uuid.random()
    private val gmId = Uuid.random()
    private val cashierId = Uuid.random()

    /** The managed list the product endpoints validate against (ticket #27). */

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
    fun `C2 create product 403s a role missing CATALOG_WRITE while GET still succeeds`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.CASHIER to setOf(Permission.SALES_RECORD, Permission.CATALOG_READ)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            catalogRouter()
        }

        val settings = Settings(config)
        val cashierToken = mintAccessToken(settings.jwtSettings, cashierId, tenantId, null, Role.CASHIER, "cashier1")

        val list = client.get("/catalog/products") { header(HttpHeaders.Authorization, "Bearer $cashierToken") }
        assertEquals(HttpStatusCode.OK, list.status)

        val create = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $cashierToken")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${Uuid.random()}","name":"Marraqueta","productType":"Pan","soldBy":"WEIGHT","pricePerKgMinor":2500}""")
        }
        assertEquals(HttpStatusCode.Forbidden, create.status)
    }

    @Test
    fun `C5 list formulas 403s MODULE_DISABLED when INVENTORY_FORMULAS is off`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.CATALOG_READ, Permission.CATALOG_WRITE)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            catalogRouter()
        }

        val settings = Settings(config)
        val gmToken = mintAccessToken(settings.jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")

        val response = client.get("/catalog/formulas") { header(HttpHeaders.Authorization, "Bearer $gmToken") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("MODULE_DISABLED", body["code"]!!.jsonPrimitive.content)
        assertEquals("INVENTORY_FORMULAS", body["module"]!!.jsonPrimitive.content)
    }

    @Test
    fun `C3 update product bumps server_seq`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.CATALOG_READ, Permission.CATALOG_WRITE)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val catalog = FakeCatalogRepository()
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), catalog = catalog)
            configureAuth()
            configureSerialization()
            catalogRouter()
        }

        val settings = Settings(config)
        val gmToken = mintAccessToken(settings.jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")
        val productId = Uuid.random()

        val create = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$productId","name":"Marraqueta","productType":"Pan","soldBy":"WEIGHT","pricePerKgMinor":2500}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        val createdSeq = Json.parseToJsonElement(create.bodyAsText()).jsonObject["serverSeq"]!!.jsonPrimitive.content.toLong()

        val update = client.put("/catalog/products/$productId") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Marraqueta Grande","productType":"Pan","soldBy":"WEIGHT","pricePerKgMinor":2600}""")
        }
        assertEquals(HttpStatusCode.OK, update.status)
        val updatedSeq = Json.parseToJsonElement(update.bodyAsText()).jsonObject["serverSeq"]!!.jsonPrimitive.content.toLong()

        assert(updatedSeq > createdSeq) { "expected server_seq to increase: $createdSeq -> $updatedSeq" }
    }

    @Test
    fun `product image is granted, uploaded by the client, confirmed, and revoked -- bytes never pass through the API`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.CATALOG_READ, Permission.CATALOG_WRITE)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val catalog = FakeCatalogRepository()
        val product = kotlinx.coroutines.runBlocking {
            catalog.createProduct(Uuid.random(), tenantId, "Berlín", "Pastelería", SoldBy.UNIT, null, 900, null)
        }
        val storage = InMemoryObjectStorage()
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), catalog = catalog, objectStorage = storage)
            configureAuth()
            configureSerialization()
            catalogRouter()
        }

        val settings = Settings(config)
        val gmToken = mintAccessToken(settings.jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")

        val rejected = client.post("/catalog/products/${product.id}/image/upload-url") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"contentType":"text/plain"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, rejected.status)
        assertEquals("UNSUPPORTED_IMAGE_TYPE", Json.parseToJsonElement(rejected.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)

        val granted = client.post("/catalog/products/${product.id}/image/upload-url") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"contentType":"image/png"}""")
        }
        assertEquals(HttpStatusCode.OK, granted.status)
        val grant = Json.parseToJsonElement(granted.bodyAsText()).jsonObject
        val key = grant["key"]!!.jsonPrimitive.content
        assertTrue(key.startsWith("$tenantId/${product.id}/") && key.endsWith(".png"), key)
        assertTrue(grant["uploadUrl"]!!.jsonPrimitive.content.contains(key))

        // Confirming before the client has uploaded is refused -- the API checks the blob landed.
        val early = client.put("/catalog/products/${product.id}/image") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"key":"$key"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, early.status)
        assertEquals("IMAGE_NOT_UPLOADED", Json.parseToJsonElement(early.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)

        // A key outside this product's prefix is refused even if it exists.
        storage.uploaded += "$tenantId/${Uuid.random()}/other.png"
        val foreign = client.put("/catalog/products/${product.id}/image") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"key":"${storage.uploaded.first()}"}""")
        }
        assertEquals("INVALID_IMAGE_KEY", Json.parseToJsonElement(foreign.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)

        storage.uploaded += key // the client's direct PUT to storage
        val confirmed = client.put("/catalog/products/${product.id}/image") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"key":"$key"}""")
        }
        assertEquals(HttpStatusCode.OK, confirmed.status)
        assertEquals("http://storage.test/product-images/$key", Json.parseToJsonElement(confirmed.bodyAsText()).jsonObject["imageUrl"]!!.jsonPrimitive.content)

        val cleared = client.delete("/catalog/products/${product.id}/image") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
        }
        assertEquals(HttpStatusCode.OK, cleared.status)
        assertEquals(JsonNull, Json.parseToJsonElement(cleared.bodyAsText()).jsonObject["imageUrl"])
        assertTrue(key !in storage.uploaded)
    }

    /** #138: confirm reads the blob's size and leading bytes (a ranged read) before recording it. */
    private fun photoCheckApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.(token: String, productId: Uuid, storage: InMemoryObjectStorage, catalog: FakeCatalogRepository) -> Unit) = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.CATALOG_READ, Permission.CATALOG_WRITE)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val catalog = FakeCatalogRepository()
        val product = kotlinx.coroutines.runBlocking {
            catalog.createProduct(Uuid.random(), tenantId, "Berlín", "Pastelería", SoldBy.UNIT, null, 900, null)
        }
        val storage = InMemoryObjectStorage()
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), catalog = catalog, objectStorage = storage)
            configureAuth()
            configureSerialization()
            catalogRouter()
        }
        val token = mintAccessToken(Settings(config).jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")
        block(token, product.id, storage, catalog)
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.grantKey(token: String, productId: Uuid): String {
        val granted = client.post("/catalog/products/$productId/image/upload-url") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"contentType":"image/jpeg"}""")
        }
        val grant = Json.parseToJsonElement(granted.bodyAsText()).jsonObject
        val expiresIn = grant["expiresAt"]!!.jsonPrimitive.content.toLong() - System.currentTimeMillis()
        assertTrue(expiresIn in 1..15 * 60_000, "an upload grant lives for minutes, was ${expiresIn / 1000}s")
        return grant["key"]!!.jsonPrimitive.content
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.confirm(token: String, productId: Uuid, key: String) =
        client.put("/catalog/products/$productId/image") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"key":"$key"}""")
        }

    private val jpegHead = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10, 'J'.code.toByte(), 'F'.code.toByte())

    @Test
    fun `confirming an oversized photo is a 422, deletes the blob and keeps the previous photo`() = photoCheckApp { token, productId, storage, catalog ->
        val good = grantKey(token, productId)
        storage.put(good, size = 200_000, head = jpegHead)
        assertEquals(HttpStatusCode.OK, confirm(token, productId, good).status)
        val previous = catalog.findProductById(productId)!!.imageUrl

        val huge = grantKey(token, productId)
        storage.put(huge, size = 5L * 1024 * 1024 + 1, head = jpegHead)
        val response = confirm(token, productId, huge)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("IMAGE_TOO_LARGE", code(response.bodyAsText()))
        assertTrue(huge !in storage.uploaded, "the rejected blob is deleted")
        assertEquals(previous, catalog.findProductById(productId)!!.imageUrl)
        assertTrue(good in storage.uploaded, "the previous photo stays")
    }

    @Test
    fun `confirming non-image bytes uploaded as an image is a 422 and deletes the blob`() = photoCheckApp { token, productId, storage, catalog ->
        val key = grantKey(token, productId)
        storage.put(key, size = 1_000, head = "<html><script>".toByteArray())
        val response = confirm(token, productId, key)

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals("IMAGE_NOT_AN_IMAGE", code(response.bodyAsText()))
        assertTrue(key !in storage.uploaded)
        assertEquals(null, catalog.findProductById(productId)!!.imageUrl)
    }

    @Test
    fun `JPEG, PNG and WebP within the limit confirm as before`() = photoCheckApp { token, productId, storage, _ ->
        val heads = listOf(
            jpegHead,
            byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A),
            "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(),
        )
        for (head in heads) {
            val key = grantKey(token, productId)
            storage.put(key, size = 5L * 1024 * 1024, head = head)
            assertEquals(HttpStatusCode.OK, confirm(token, productId, key).status, head.decodeToString())
        }
    }

    // --- Product origin (Panadería / Almacén) ---------------------------------------------------

    private val originBranchId = Uuid.random()

    private fun origin(body: String) = Json.parseToJsonElement(body).jsonObject["origin"]?.jsonPrimitive?.content
    private fun code(body: String) = Json.parseToJsonElement(body).jsonObject["code"]?.jsonPrimitive?.content

    private fun originApp(block: suspend io.ktor.server.testing.ApplicationTestBuilder.(token: String, catalog: FakeCatalogRepository) -> Unit) = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.CATALOG_READ, Permission.CATALOG_WRITE)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val catalog = FakeCatalogRepository()
        val branches = FakeBranchRepository()
        kotlinx.coroutines.runBlocking { branches.create(originBranchId, tenantId, "Main") }
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), branches, FakeRefreshTokenRepository(), catalog = catalog)
            configureAuth()
            configureSerialization()
            catalogRouter()
        }
        val token = mintAccessToken(Settings(config).jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")
        block(token, catalog)
    }

    @Test
    fun `origin defaults to PANADERIA on create and is listed`() = originApp { token, _ ->
        val create = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${Uuid.random()}","name":"Marraqueta","productType":"Pan","soldBy":"WEIGHT","pricePerKgMinor":2500}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        assertEquals("PANADERIA", origin(create.bodyAsText()))

        val list = client.get("/catalog/products") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals("PANADERIA", Json.parseToJsonElement(list.bodyAsText()).let { (it as kotlinx.serialization.json.JsonArray)[0].jsonObject["origin"]!!.jsonPrimitive.content })
    }

    @Test
    fun `an ALMACEN product sold by unit is created and returned with its origin`() = originApp { token, _ ->
        val create = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${Uuid.random()}","name":"Bebida cola","productType":"Almacén","soldBy":"UNIT","priceMinor":1500,"openingStock":0,"openingBranchId":"$originBranchId"}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        assertEquals("ALMACEN", origin(create.bodyAsText()))
    }

    @Test
    fun `creating an ALMACEN product sold by weight is rejected`() = originApp { token, catalog ->
        val create = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${Uuid.random()}","name":"Galletas","productType":"Almacén","soldBy":"WEIGHT","pricePerKgMinor":9000}""")
        }
        assertEquals(HttpStatusCode.BadRequest, create.status)
        assertEquals("ALMACEN_REQUIRES_UNIT", code(create.bodyAsText()))
        assertTrue(catalog.products.isEmpty())
    }

    @Test
    fun `an unknown soldBy is rejected on create and on edit and nothing is stored`() = originApp { token, catalog ->
        val create = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${Uuid.random()}","name":"Pan","productType":"Pan","soldBy":"BY_THE_YARD","priceMinor":100}""")
        }
        assertEquals(HttpStatusCode.BadRequest, create.status)
        assertEquals("INVALID_SOLD_BY", code(create.bodyAsText()))

        val id = Uuid.random()
        client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$id","name":"Galletas","productType":"Pan","soldBy":"UNIT","priceMinor":800}""")
        }
        val edit = client.put("/catalog/products/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Galletas","productType":"Pan","soldBy":"unit","priceMinor":800}""")
        }
        assertEquals(HttpStatusCode.BadRequest, edit.status)
        assertEquals("INVALID_SOLD_BY", code(edit.bodyAsText()))
    }

    @Test
    fun `an explicit origin is ignored - the Tipo decides`() = originApp { token, _ ->
        suspend fun create(type: String, origin: String) = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${Uuid.random()}","name":"X $type","productType":"$type","origin":"$origin","soldBy":"UNIT","priceMinor":100,"openingStock":0,"openingBranchId":"$originBranchId"}""")
        }
        assertEquals("PANADERIA", origin(create("Pan", "ALMACEN").bodyAsText()))
        assertEquals("PANADERIA", origin(create("Pastelería", "BODEGA").bodyAsText()))
        assertEquals("ALMACEN", origin(create("Almacén", "PANADERIA").bodyAsText()))
    }

    @Test
    fun `Tipo spelling is normalised to the canonical label and origin is derived`() = originApp { token, _ ->
        val create = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${Uuid.random()}","name":"Cola","productType":"ALMACEN","soldBy":"UNIT","priceMinor":100,"openingStock":2,"openingBranchId":"$originBranchId"}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        assertEquals("Almacén", Json.parseToJsonElement(create.bodyAsText()).jsonObject["productType"]!!.jsonPrimitive.content)
        assertEquals("ALMACEN", origin(create.bodyAsText()))
    }

    @Test
    fun `Tipo can move between Pan and Pastelería but not across Almacén, and Almacén stays by unit`() = originApp { token, catalog ->
        val id = Uuid.random()
        client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$id","name":"Galletas","productType":"Pan","soldBy":"UNIT","priceMinor":800}""")
        }
        suspend fun edit(body: String, target: Uuid = id) = client.put("/catalog/products/$target") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

        val toAlmacen = edit("""{"name":"Galletas","productType":"Almacén","soldBy":"UNIT","priceMinor":800}""")
        assertEquals(HttpStatusCode.BadRequest, toAlmacen.status)
        assertEquals("ORIGIN_IMMUTABLE", code(toAlmacen.bodyAsText()))
        assertEquals(ProductOrigin.PANADERIA, catalog.products.getValue(id).origin)
        assertEquals("Pan", catalog.products.getValue(id).productType)

        val toPasteleria = edit("""{"name":"Galletas","productType":"Pastelería","origin":"ALMACEN","soldBy":"UNIT","priceMinor":800}""")
        assertEquals(HttpStatusCode.OK, toPasteleria.status)
        assertEquals("Pastelería", catalog.products.getValue(id).productType)
        assertEquals("PANADERIA", origin(toPasteleria.bodyAsText()))

        val almacenId = Uuid.random()
        catalog.createProduct(almacenId, tenantId, "Cola", "Almacén", SoldBy.UNIT, null, 100, null, null, ProductOrigin.ALMACEN, null)
        val toPan = edit("""{"name":"Cola","productType":"Pan","soldBy":"UNIT","priceMinor":100}""", almacenId)
        assertEquals("ORIGIN_IMMUTABLE", code(toPan.bodyAsText()))
        assertEquals("Almacén", catalog.products.getValue(almacenId).productType)

        // An Almacén product still cannot switch to weight.
        val weight = edit("""{"name":"Cola","productType":"Almacén","soldBy":"WEIGHT","pricePerKgMinor":9000}""", almacenId)
        assertEquals(HttpStatusCode.BadRequest, weight.status)
        assertEquals("ALMACEN_REQUIRES_UNIT", code(weight.bodyAsText()))
    }

    // --- Production is Panadería-only (ticket #25) ----------------------------------------------

    @Test
    fun `putting a formula on an ALMACEN product is rejected while a PANADERIA one is accepted`() = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.CATALOG_READ, Permission.CATALOG_WRITE)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(
            Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES, Module.INVENTORY_FORMULAS), Clock.System.now())
        )
        val catalog = FakeCatalogRepository()
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository(), catalog = catalog)
            configureAuth()
            configureSerialization()
            catalogRouter()
        }
        val token = mintAccessToken(Settings(config).jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")

        val bakedId = Uuid.random()
        val boughtId = Uuid.random()
        client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$bakedId","name":"Marraqueta","productType":"Pan","soldBy":"WEIGHT","pricePerKgMinor":2500}""")
        }
        catalog.createProduct(boughtId, tenantId, "Bebida cola", "Almacén", SoldBy.UNIT, null, 1500, null, null, ProductOrigin.ALMACEN)
        val body = """{"lines":[{"ingredientId":"${Uuid.random()}","basisPoints":6000}]}"""

        val rejected = client.put("/catalog/products/$boughtId/formula") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.BadRequest, rejected.status)
        assertEquals("FORMULA_REQUIRES_PANADERIA", code(rejected.bodyAsText()))
        assertTrue(catalog.formulas.isEmpty())

        val accepted = client.put("/catalog/products/$bakedId/formula") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertEquals(HttpStatusCode.OK, accepted.status)
    }

    // --- Fixed product types ----------------------------------------------------------------------------------------------------

    @Test
    fun `a product stores the canonical Tipo spelling and an unknown type is rejected on create and edit`() = originApp { token, catalog ->
        val id = Uuid.random()
        val create = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"$id","name":"Marraqueta","productType":"PASTELERIA","soldBy":"WEIGHT","pricePerKgMinor":2500}""")
        }
        assertEquals(HttpStatusCode.Created, create.status)
        assertEquals("Pastelería", Json.parseToJsonElement(create.bodyAsText()).jsonObject["productType"]!!.jsonPrimitive.content)

        val unknown = client.post("/catalog/products") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"id":"${Uuid.random()}","name":"X","productType":"Cremas","soldBy":"UNIT","priceMinor":100}""")
        }
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
        assertEquals("UNKNOWN_PRODUCT_TYPE", code(unknown.bodyAsText()))
        assertEquals(1, catalog.products.size)

        val edit = client.put("/catalog/products/$id") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Marraqueta","productType":"Tortas","soldBy":"WEIGHT","pricePerKgMinor":2500}""")
        }
        assertEquals("UNKNOWN_PRODUCT_TYPE", code(edit.bodyAsText()))
        assertEquals("Pastelería", catalog.products.getValue(id).productType)
    }
}
