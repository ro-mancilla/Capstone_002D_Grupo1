package com.f776.ecopan.api.feature.analytics.controller

import com.f776.ecopan.api.core.ErrorBody
import com.f776.ecopan.api.core.branchScopeOrRespond
import com.f776.ecopan.api.core.principalContext
import com.f776.ecopan.api.core.requireModule
import com.f776.ecopan.api.core.requirePermission
import com.f776.ecopan.api.feature.analytics.domain.AnalyticsService
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** K1-K9 (02 §3; K9 is the waste estimate added for HU-ANA-0005): `ANALYTICS_READ` + `ANALYTICS_DASHBOARD` uniformly; K5/K8 layer an extra module. */
fun Application.analyticsRouter() {
    routing {
        val service by inject<AnalyticsService>()

        authenticate("auth-jwt") {
            route("analytics") {
                requirePermission(Permission.ANALYTICS_READ)
                requireModule(Module.ANALYTICS_DASHBOARD)

                get("sales-by-day") {
                    val context = call.principalContext()
                    val params = call.request.queryParameters
                    val branchId = (call.branchScopeOrRespond() ?: return@get).branchId
                    val from = params.rangeStart() ?: return@get call.respondMissingRange()
                    val to = params.rangeEnd() ?: return@get call.respondMissingRange()
                    val groupByProductType = params["groupBy"] == "productType"
                    call.respond(HttpStatusCode.OK, service.salesByDay(context.tenantId, branchId, from, to, groupByProductType))
                }

                get("revenue") {
                    val context = call.principalContext()
                    val params = call.request.queryParameters
                    val branchId = (call.branchScopeOrRespond() ?: return@get).branchId
                    val from = params.rangeStart() ?: return@get call.respondMissingRange()
                    val to = params.rangeEnd() ?: return@get call.respondMissingRange()
                    val granularity = params["granularity"] ?: "day"
                    call.respond(HttpStatusCode.OK, service.revenue(context.tenantId, branchId, from, to, granularity))
                }

                get("product-mix") {
                    val context = call.principalContext()
                    val params = call.request.queryParameters
                    val branchId = (call.branchScopeOrRespond() ?: return@get).branchId
                    val from = params.rangeStart() ?: return@get call.respondMissingRange()
                    val to = params.rangeEnd() ?: return@get call.respondMissingRange()
                    call.respond(HttpStatusCode.OK, service.productMix(context.tenantId, branchId, from, to))
                }

                get("production-summary") {
                    val context = call.principalContext()
                    val params = call.request.queryParameters
                    val branchId = (call.branchScopeOrRespond() ?: return@get).branchId
                    val from = params.rangeStart() ?: return@get call.respondMissingRange()
                    val to = params.rangeEnd() ?: return@get call.respondMissingRange()
                    call.respond(HttpStatusCode.OK, service.productionSummary(context.tenantId, branchId, from, to))
                }

                get("waste-estimate") {
                    val context = call.principalContext()
                    val params = call.request.queryParameters
                    val branchId = (call.branchScopeOrRespond() ?: return@get).branchId
                    val from = params.rangeStart() ?: return@get call.respondMissingRange()
                    val to = params.rangeEnd() ?: return@get call.respondMissingRange()
                    call.respond(HttpStatusCode.OK, service.wasteEstimate(context.tenantId, branchId, from, to))
                }

                get("payment-methods") {
                    val context = call.principalContext()
                    val params = call.request.queryParameters
                    val branchId = (call.branchScopeOrRespond() ?: return@get).branchId
                    val from = params.rangeStart() ?: return@get call.respondMissingRange()
                    val to = params.rangeEnd() ?: return@get call.respondMissingRange()
                    call.respond(HttpStatusCode.OK, service.paymentMethods(context.tenantId, branchId, from, to))
                }

                route("stock-levels") {
                    requireModule(Module.INVENTORY_FORMULAS)

                    get {
                        val context = call.principalContext()
                        val branchId = (call.branchScopeOrRespond() ?: return@get).branchId
                        if (branchId == null) {
                            call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "BRANCH_ID_REQUIRED"))
                            return@get
                        }
                        call.respond(HttpStatusCode.OK, service.stockLevels(context.tenantId, branchId))
                    }
                }

                route("forecast-vs-actual") {
                    requireModule(Module.DEMAND_FORECASTING)

                    get {
                        val context = call.principalContext()
                        val params = call.request.queryParameters
                        val branchId = (call.branchScopeOrRespond() ?: return@get).branchId
                        if (branchId == null) {
                            call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "BRANCH_ID_REQUIRED"))
                            return@get
                        }
                        val productType = params["productType"]
                        val productId = params["productId"]?.let {
                            runCatching { Uuid.parse(it) }.getOrNull()
                                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "INVALID_PRODUCT"))
                        }
                        val from = params.rangeStart() ?: return@get call.respondMissingRange()
                        val to = params.rangeEnd() ?: return@get call.respondMissingRange()
                        call.respond(HttpStatusCode.OK, service.forecastVsActual(context.tenantId, branchId, productType, productId, from, to))
                    }
                }
            }
        }
    }
}

private fun io.ktor.http.Parameters.rangeStart(): Instant? = this["from"]?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
private fun io.ktor.http.Parameters.rangeEnd(): Instant? = this["to"]?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)

private suspend fun io.ktor.server.application.ApplicationCall.respondMissingRange() =
    respond(HttpStatusCode.BadRequest, ErrorBody(code = "RANGE_REQUIRED", message = "from/to (epoch millis) are required"))
