package com.f776.ecopan.api

import com.f776.ecopan.api.core.PermissionRegistry
import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.core.mintAccessToken
import com.f776.ecopan.api.fakes.FakeBranchRepository
import com.f776.ecopan.api.fakes.FakeRefreshTokenRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.fakes.FakeUserRepository
import com.f776.ecopan.api.fakes.installTestKoin
import com.f776.ecopan.api.feature.tenant.controller.tenantRouter
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** 02 §8 AC: `MODULE_DISABLED` on T5 when `MULTI_BRANCH_ROLES` is off; config writes bump server_seq. */
class TenantRouterTest {

    private val tenantId = Uuid.random()
    private val gmId = Uuid.random()
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

    @Test
    fun `T5 branch write 403s MODULE_DISABLED when MULTI_BRANCH_ROLES is off`() = testApplication {
        PermissionRegistry.refresh(
            com.f776.ecopan.api.fakes.FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.MODULES_TOGGLE)))
        )

        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            tenantRouter()
        }

        val settings = com.f776.ecopan.api.core.Settings(config)
        val gmToken = mintAccessToken(settings.jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")

        val response = client.put("/tenant/branches/${Uuid.random()}") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Second Branch","active":true}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals("MODULE_DISABLED", body["code"]!!.jsonPrimitive.content)
        assertEquals("MULTI_BRANCH_ROLES", body["module"]!!.jsonPrimitive.content)
    }

    @Test
    fun `T2 update modules bumps server_seq and is reflected by T1`() = testApplication {
        PermissionRegistry.refresh(
            com.f776.ecopan.api.fakes.FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.MODULES_TOGGLE)))
        )

        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()

        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            tenantRouter()
        }

        val settings = com.f776.ecopan.api.core.Settings(config)
        val gmToken = mintAccessToken(settings.jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")

        val update = client.put("/tenant/modules") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"enabledModules":["POS_SALES","INVENTORY_FORMULAS"]}""")
        }
        assertEquals(HttpStatusCode.OK, update.status)

        val profile = client.get("/tenant") { header(HttpHeaders.Authorization, "Bearer $gmToken") }
        val modules = Json.parseToJsonElement(profile.bodyAsText()).jsonObject["enabledModules"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(setOf("POS_SALES", "INVENTORY_FORMULAS"), modules.toSet())
    }

    @Test
    fun `T2 update modules 400s INVALID_MODULE for an unknown id and changes nothing`() = testApplication {
        val tenantId = Uuid.random()
        val gmId = Uuid.random()
        PermissionRegistry.refresh(
            com.f776.ecopan.api.fakes.FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.MODULES_TOGGLE)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            tenantRouter()
        }
        val settings = com.f776.ecopan.api.core.Settings(config)
        val gmToken = mintAccessToken(settings.jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")

        val update = client.put("/tenant/modules") {
            header(HttpHeaders.Authorization, "Bearer $gmToken")
            contentType(ContentType.Application.Json)
            setBody("""{"enabledModules":["POS_SALES","TELEPORTER"]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, update.status)
        assertTrue(update.bodyAsText().contains("INVALID_MODULE"))
        assertEquals(setOf(Module.POS_SALES), tenants.enabledModules(tenantId))
    }

    @Test
    fun `T3 list users 403s a role missing USERS_MANAGE`() = testApplication {
        PermissionRegistry.refresh(
            com.f776.ecopan.api.fakes.FakeRolePermissionRepository(mapOf(Role.CASHIER to setOf(Permission.SALES_RECORD)))
        )

        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, FakeTenantRepository(), FakeUserRepository(), FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            tenantRouter()
        }

        val settings = com.f776.ecopan.api.core.Settings(config)
        val cashierToken = mintAccessToken(settings.jwtSettings, cashierId, tenantId, null, Role.CASHIER, "cashier1")

        val response = client.get("/tenant/users") { header(HttpHeaders.Authorization, "Bearer $cashierToken") }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.userApi(users: FakeUserRepository = FakeUserRepository()): String {
        PermissionRegistry.refresh(
            com.f776.ecopan.api.fakes.FakeRolePermissionRepository(mapOf(Role.GENERAL_MANAGER to setOf(Permission.USERS_MANAGE)))
        )
        val tenants = FakeTenantRepository()
        tenants.seed(Tenant(tenantId, "reference", "Reference Bakery", "CLP", setOf(Module.POS_SALES), Clock.System.now()))
        val config = testConfig()
        environment { this.config = config }
        application {
            installTestKoin(config, tenants, users, FakeBranchRepository(), FakeRefreshTokenRepository())
            configureAuth()
            configureSerialization()
            tenantRouter()
        }
        return mintAccessToken(com.f776.ecopan.api.core.Settings(config).jwtSettings, gmId, tenantId, null, Role.GENERAL_MANAGER, "gm1")
    }

    @Test
    fun `T3 create user 400s INVALID_ROLE for a role outside the fixed set and accepts a real one`() = testApplication {
        val token = userApi()
        fun body(role: String) = """{"username":"u1","password":"pw123456","displayName":"U","role":"$role"}"""

        val bad = client.post("/tenant/users") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body("SUPERHERO"))
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals("INVALID_ROLE", Json.parseToJsonElement(bad.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)

        val lower = client.post("/tenant/users") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body("cashier"))
        }
        assertEquals(HttpStatusCode.BadRequest, lower.status)

        val good = client.post("/tenant/users") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(body("CASHIER"))
        }
        assertEquals(HttpStatusCode.Created, good.status)
    }

    @Test
    fun `T4 update user 400s INVALID_ROLE and leaves the role unchanged`() = testApplication {
        val users = FakeUserRepository()
        val token = userApi(users)
        val created = users.create(Uuid.random(), tenantId, null, "u2", "hash", null, "U2", Role.BAKER)

        val bad = client.put("/tenant/users/${created.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"username":"u2","displayName":"U2","role":"WIZARD"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals("INVALID_ROLE", Json.parseToJsonElement(bad.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content)
        assertEquals(Role.BAKER, users.findById(created.id)!!.role)
    }
}
