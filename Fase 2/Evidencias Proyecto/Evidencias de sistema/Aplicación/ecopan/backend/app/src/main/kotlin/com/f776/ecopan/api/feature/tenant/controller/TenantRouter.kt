package com.f776.ecopan.api.feature.tenant.controller

import com.f776.ecopan.api.core.ErrorBody
import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.core.principalContext
import com.f776.ecopan.api.core.requireModule
import com.f776.ecopan.api.core.requirePermission
import com.f776.ecopan.api.feature.tenant.controller.dto.UpdateModulesRequest
import com.f776.ecopan.api.feature.tenant.controller.dto.UpsertBranchRequest
import com.f776.ecopan.api.feature.tenant.controller.dto.UpsertUserRequest
import com.f776.ecopan.api.feature.tenant.domain.TenantService
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/** T1-T5 (02 §3). */
fun Application.tenantRouter() {
    routing {
        val service by inject<TenantService>()

        authenticate("auth-jwt") {
            route("tenant") {
                // T1: any authenticated principal.
                get {
                    val profile = service.getProfile(call.principalContext().tenantId)
                    if (profile == null) {
                        call.respond(HttpStatusCode.NotFound, ErrorBody(code = "TENANT_NOT_FOUND"))
                    } else {
                        call.respond(HttpStatusCode.OK, profile)
                    }
                }

                route("modules") {
                    requirePermission(Permission.MODULES_TOGGLE)
                    put {
                        val body = call.receive<UpdateModulesRequest>()
                        when (val outcome = service.updateModules(call.principalContext().tenantId, body)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                            is ServiceOutcome.Invalid ->
                                call.respond(HttpStatusCode.BadRequest, ErrorBody(code = outcome.code, message = outcome.message))
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "TENANT_NOT_FOUND"))
                        }
                    }
                }

                route("users") {
                    // T3/T4: USERS_MANAGE only -- always-on, never module-gated (01 §4).
                    requirePermission(Permission.USERS_MANAGE)

                    get {
                        call.respond(HttpStatusCode.OK, service.listUsers(call.principalContext().tenantId))
                    }

                    post {
                        val body = call.receive<UpsertUserRequest>()
                        when (val outcome = service.createUser(call.principalContext().tenantId, body)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.Created, outcome.value)
                            is ServiceOutcome.Invalid ->
                                call.respond(HttpStatusCode.BadRequest, ErrorBody(code = outcome.code, message = outcome.message))
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "USER_NOT_FOUND"))
                        }
                    }

                    route("{id}") {
                        put {
                            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                            if (id == null) {
                                call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "INVALID_ID"))
                                return@put
                            }
                            val body = call.receive<UpsertUserRequest>()
                            when (val outcome = service.updateUser(call.principalContext().tenantId, id, body)) {
                                is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                                is ServiceOutcome.Invalid ->
                                    call.respond(HttpStatusCode.BadRequest, ErrorBody(code = outcome.code, message = outcome.message))
                                ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "USER_NOT_FOUND"))
                            }
                        }
                    }
                }

                route("branches") {
                    // T5: branch *structure* -- MODULES_TOGGLE (GM/ADMIN) + MULTI_BRANCH_ROLES.
                    requirePermission(Permission.MODULES_TOGGLE)
                    requireModule(Module.MULTI_BRANCH_ROLES)

                    post {
                        val body = call.receive<UpsertBranchRequest>()
                        val branch = service.createBranch(call.principalContext().tenantId, body)
                        call.respond(HttpStatusCode.Created, branch)
                    }

                    route("{id}") {
                        put {
                            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                            if (id == null) {
                                call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "INVALID_ID"))
                                return@put
                            }
                            val body = call.receive<UpsertBranchRequest>()
                            val branch = service.updateBranch(call.principalContext().tenantId, id, body)
                            if (branch == null) {
                                call.respond(HttpStatusCode.NotFound, ErrorBody(code = "BRANCH_NOT_FOUND"))
                            } else {
                                call.respond(HttpStatusCode.OK, branch)
                            }
                        }
                    }
                }
            }
        }
    }
}
