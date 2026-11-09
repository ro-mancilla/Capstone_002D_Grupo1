package com.f776.ecopan.api.feature.auth.controller

import com.f776.ecopan.api.core.AuthRateLimit
import com.f776.ecopan.api.core.ErrorBody
import com.f776.ecopan.api.feature.auth.controller.dto.LoginRequest
import com.f776.ecopan.api.feature.auth.controller.dto.LogoutRequest
import com.f776.ecopan.api.feature.auth.controller.dto.RefreshRequest
import com.f776.ecopan.api.feature.auth.domain.AuthService
import com.f776.ecopan.api.feature.auth.domain.LoginOutcome
import com.f776.ecopan.api.feature.auth.domain.RefreshOutcome
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

/**
 * A1/A2/A3 (02 §3): login + refresh are the two public (unauthenticated) auth routes; logout
 * requires a valid access token like every other route.
 */
fun Application.authRouter() {
    routing {
        val service by inject<AuthService>()

        route("auth") {
            rateLimit(AuthRateLimit) {
                post("login") {
                    val body = call.receive<LoginRequest>()
                    when (
                        val outcome = service.login(
                            tenantSlug = body.tenantSlug,
                            username = body.username,
                            password = body.password,
                            deviceId = body.deviceId,
                            deviceName = body.deviceName
                        )
                    ) {
                        is LoginOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.response)
                        LoginOutcome.InvalidCredentials ->
                            call.respond(HttpStatusCode.Unauthorized, ErrorBody(code = "INVALID_CREDENTIALS"))
                    }
                }

                post("refresh") {
                    val body = call.receive<RefreshRequest>()
                    when (val outcome = service.refresh(body.refreshToken)) {
                        is RefreshOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.response)
                        RefreshOutcome.Invalid ->
                            call.respond(HttpStatusCode.Unauthorized, ErrorBody(code = "TOKEN_INVALID"))
                    }
                }
            }

            authenticate("auth-jwt") {
                post("logout") {
                    val body = call.receive<LogoutRequest>()
                    service.logout(body.refreshToken)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
