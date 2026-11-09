package com.f776.ecopan.api

import com.f776.ecopan.api.core.PermissionRegistry
import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.core.Settings
import com.f776.ecopan.api.core.configureAuth
import com.f776.ecopan.api.core.configureSerialization
import com.f776.ecopan.api.core.mintAccessToken
import com.f776.ecopan.api.fakes.FakeRolePermissionRepository
import com.f776.ecopan.api.fakes.FakeTenantRepository
import com.f776.ecopan.api.feature.analytics.controller.analyticsRouter
import com.f776.ecopan.api.feature.analytics.controller.dto.ForecastVsActualDto
import com.f776.ecopan.api.feature.analytics.controller.dto.PaymentMethodDto
import com.f776.ecopan.api.feature.analytics.controller.dto.ProductMixDto
import com.f776.ecopan.api.feature.analytics.controller.dto.ProductionSummaryDto
import com.f776.ecopan.api.feature.analytics.controller.dto.RevenueByPeriodDto
import com.f776.ecopan.api.feature.analytics.controller.dto.SalesByDayDto
import com.f776.ecopan.api.feature.analytics.controller.dto.StockLevelWarningDto
import com.f776.ecopan.api.feature.analytics.controller.dto.WasteEstimateDto
import com.f776.ecopan.api.feature.analytics.domain.AnalyticsService
import com.f776.ecopan.api.feature.forecasts.controller.dto.ForecastDto
import com.f776.ecopan.api.feature.forecasts.controller.forecastsRouter
import com.f776.ecopan.api.feature.forecasts.domain.ForecastsService
import com.f776.ecopan.data.database.repository.TenantRepository
import com.f776.ecopan.data.database.repository.model.Tenant
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * #127: a branch-bound principal (BRANCH_MANAGER) reads analytics and forecasts for its own branch
 * only. Leaving the branch out defaults to its own; naming another is `BRANCH_FORBIDDEN`. Tenant-wide
 * roles are unchanged, including the tenant-wide total when no branch is given.
 */
class BranchScopeRouterTest {

    private val tenantId = Uuid.random()
    private val ownBranch = Uuid.random()
    private val otherBranch = Uuid.random()

    private val config = MapApplicationConfig(
        "jwt.secret" to "test-secret",
        "jwt.issuer" to "ecopan-test",
        "jwt.audience" to "ecopan-test-clients",
        "jwt.accessTokenTtlMin" to "15",
        "jwt.refreshTokenTtlDays" to "30",
        "device.sessionTtlDays" to "180",
        "reporting.tz" to "America/Santiago"
    )

    /** Every K endpoint that takes a `branchId` (K-wide ones with an optional one, K5/K8 with a required one). */
    private val analyticsPaths = listOf(
        "/analytics/sales-by-day?from=0&to=1",
        "/analytics/revenue?from=0&to=1",
        "/analytics/product-mix?from=0&to=1",
        "/analytics/production-summary?from=0&to=1",
        "/analytics/waste-estimate?from=0&to=1",
        "/analytics/payment-methods?from=0&to=1",
        "/analytics/stock-levels?x=1",
        "/analytics/forecast-vs-actual?from=0&to=1",
        "/forecasts?from=0&to=1",
    )

    private class Recorder {
        val branches = mutableListOf<Uuid?>()
    }

    private fun scoped(block: suspend ApplicationTestBuilder.(Recorder) -> Unit) = testApplication {
        PermissionRegistry.refresh(
            FakeRolePermissionRepository(
                mapOf(
                    Role.BRANCH_MANAGER to setOf(Permission.ANALYTICS_READ, Permission.FORECAST_READ),
                    Role.GENERAL_MANAGER to setOf(Permission.ANALYTICS_READ, Permission.FORECAST_READ),
                )
            )
        )
        val tenants = FakeTenantRepository()
        tenants.seed(
            Tenant(
                tenantId, "reference", "Reference Bakery", "CLP",
                setOf(Module.ANALYTICS_DASHBOARD, Module.INVENTORY_FORMULAS, Module.DEMAND_FORECASTING),
                Clock.System.now()
            )
        )
        val recorder = Recorder()
        environment { this.config = this@BranchScopeRouterTest.config }
        application {
            install(Koin) {
                modules(
                    module {
                        single { Settings(this@BranchScopeRouterTest.config) }
                        single<TenantRepository> { tenants }
                        single<AnalyticsService> { RecordingAnalytics(recorder) }
                        single<ForecastsService> { RecordingForecasts(recorder) }
                    }
                )
            }
            configureAuth()
            configureSerialization()
            analyticsRouter()
            forecastsRouter()
        }
        block(recorder)
    }

    private fun token(role: Role, branchId: Uuid?): String =
        mintAccessToken(Settings(config).jwtSettings, Uuid.random(), tenantId, branchId, role, "u")

    private suspend fun HttpClient.getAs(path: String, token: String): HttpResponse =
        get(path) { header(HttpHeaders.Authorization, "Bearer $token") }

    private fun String.withBranch(branchId: Uuid) = "$this&branchId=$branchId"

    @Test
    fun `a branch manager who names no branch gets their own branch`() = scoped { recorder ->
        val bm = token(Role.BRANCH_MANAGER, ownBranch)
        for (path in analyticsPaths) {
            val response = client.getAs(path, bm)
            assertEquals(HttpStatusCode.OK, response.status, path)
            assertEquals(ownBranch, recorder.branches.last(), path)
        }
    }

    @Test
    fun `a branch manager who names another branch is BRANCH_FORBIDDEN`() = scoped { recorder ->
        val bm = token(Role.BRANCH_MANAGER, ownBranch)
        for (path in analyticsPaths) {
            val response = client.getAs(path.withBranch(otherBranch), bm)
            assertEquals(HttpStatusCode.Forbidden, response.status, path)
            val code = Json.parseToJsonElement(response.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content
            assertEquals("BRANCH_FORBIDDEN", code, path)
        }
        assertEquals(emptyList(), recorder.branches)
    }

    @Test
    fun `a branch manager who names their own branch gets it`() = scoped { recorder ->
        val bm = token(Role.BRANCH_MANAGER, ownBranch)
        for (path in analyticsPaths) {
            assertEquals(HttpStatusCode.OK, client.getAs(path.withBranch(ownBranch), bm).status, path)
            assertEquals(ownBranch, recorder.branches.last(), path)
        }
    }

    @Test
    fun `a tenant-wide role is unchanged - any branch, and tenant-wide totals with none`() = scoped { recorder ->
        val gm = token(Role.GENERAL_MANAGER, null)
        for (path in analyticsPaths) {
            assertEquals(HttpStatusCode.OK, client.getAs(path.withBranch(otherBranch), gm).status, path)
            assertEquals(otherBranch, recorder.branches.last(), path)
        }
        val tenantWide = analyticsPaths.filterNot { "stock-levels" in it || "forecast-vs-actual" in it || "forecasts" in it }
        for (path in tenantWide) {
            assertEquals(HttpStatusCode.OK, client.getAs(path, gm).status, path)
            assertEquals(null, recorder.branches.last(), path)
        }
    }

    private class RecordingAnalytics(private val r: Recorder) : AnalyticsService {
        override suspend fun salesByDay(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant, groupByProductType: Boolean): List<SalesByDayDto> =
            emptyList<SalesByDayDto>().also { r.branches += branchId }
        override suspend fun revenue(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant, granularity: String): List<RevenueByPeriodDto> =
            emptyList<RevenueByPeriodDto>().also { r.branches += branchId }
        override suspend fun productMix(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductMixDto> =
            emptyList<ProductMixDto>().also { r.branches += branchId }
        override suspend fun stockLevels(tenantId: Uuid, branchId: Uuid): List<StockLevelWarningDto> =
            emptyList<StockLevelWarningDto>().also { r.branches += branchId }
        override suspend fun productionSummary(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductionSummaryDto> =
            emptyList<ProductionSummaryDto>().also { r.branches += branchId }
        override suspend fun wasteEstimate(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<WasteEstimateDto> =
            emptyList<WasteEstimateDto>().also { r.branches += branchId }
        override suspend fun paymentMethods(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<PaymentMethodDto> =
            emptyList<PaymentMethodDto>().also { r.branches += branchId }
        override suspend fun forecastVsActual(tenantId: Uuid, branchId: Uuid, productType: String?, productId: Uuid?, from: Instant, to: Instant): List<ForecastVsActualDto> =
            emptyList<ForecastVsActualDto>().also { r.branches += branchId }
    }

    private class RecordingForecasts(private val r: Recorder) : ForecastsService {
        override suspend fun list(tenantId: Uuid, branchId: Uuid, from: Instant, to: Instant): ServiceOutcome<List<ForecastDto>> =
            ServiceOutcome.Success(emptyList<ForecastDto>()).also { r.branches += branchId }
        override suspend fun requestRefresh(tenantId: Uuid, branchId: Uuid, productType: String?) {
            r.branches += branchId
        }
    }
}
