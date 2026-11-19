package com.f776.ecopan.api.feature.forecasts.controller

import com.f776.ecopan.api.core.ErrorBody
import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.core.branchScopeOrRespond
import com.f776.ecopan.api.core.respondBranchForbidden
import com.f776.ecopan.api.core.scopeFor
import com.f776.ecopan.api.core.principalContext
import com.f776.ecopan.api.core.requireModule
import com.f776.ecopan.api.core.requirePermissionInline
import com.f776.ecopan.api.feature.forecasts.controller.dto.RefreshForecastsRequest
import com.f776.ecopan.api.feature.forecasts.domain.ForecastsService
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** F1/F2 (02 §3), both gated by `DEMAND_FORECASTING`. */
fun Application.forecastsRouter() {
    routing {
        val service by inject<ForecastsService>()

        authenticate("auth-jwt") {
            route("forecasts") {
                requireModule(Module.DEMAND_FORECASTING)

                get {
                    if (!call.requirePermissionInline(Permission.FORECAST_READ)) return@get
                    val context = call.principalContext()
                    val params = call.request.queryParameters
                    val branchId = (call.branchScopeOrRespond() ?: return@get).branchId
                    val from = params["from"]?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
                    val to = params["to"]?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
                    if (branchId == null || from == null || to == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "MISSING_PARAMS", message = "branchId/from/to required"))
                        return@get
                    }
                    when (val outcome = service.list(context.tenantId, branchId, from, to)) {
                        is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                        is ServiceOutcome.Invalid -> call.respond(HttpStatusCode.BadRequest, ErrorBody(code = outcome.code, message = outcome.message))
                        ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "NOT_FOUND"))
                    }
                }

                post("refresh") {
                    if (!call.requirePermissionInline(Permission.MODULES_TOGGLE)) return@post
                    val context = call.principalContext()
                    val body = call.receive<RefreshForecastsRequest>()
                    val branchId = runCatching { Uuid.parse(body.branchId) }.getOrNull()
                    if (branchId == null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "INVALID_BRANCH"))
                        return@post
                    }
                    if (context.scopeFor(branchId) == null) {
                        call.respondBranchForbidden()
                        return@post
                    }
                    service.requestRefresh(context.tenantId, branchId, body.productType)
                    call.respond(HttpStatusCode.Accepted)
                }
            }
        }
    }
}
