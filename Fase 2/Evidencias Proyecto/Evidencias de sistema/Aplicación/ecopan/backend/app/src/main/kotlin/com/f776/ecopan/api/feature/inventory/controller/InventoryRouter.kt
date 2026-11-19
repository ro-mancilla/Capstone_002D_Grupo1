package com.f776.ecopan.api.feature.inventory.controller

import com.f776.ecopan.api.core.ErrorBody
import com.f776.ecopan.api.core.principalContext
import com.f776.ecopan.api.core.requireModule
import com.f776.ecopan.api.core.requirePermission
import com.f776.ecopan.api.core.requirePermissionInline
import com.f776.ecopan.api.feature.inventory.controller.dto.CreateIngredientRequest
import com.f776.ecopan.api.feature.inventory.controller.dto.CreateProductStockMovementRequest
import com.f776.ecopan.api.feature.inventory.controller.dto.CreateStockMovementRequest
import com.f776.ecopan.api.feature.inventory.controller.dto.SetProductLowStockThresholdRequest
import com.f776.ecopan.api.feature.inventory.controller.dto.UpdateIngredientRequest
import com.f776.ecopan.api.feature.inventory.domain.InventoryService
import com.f776.ecopan.api.core.ServiceOutcome
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
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * I1-I5 (02 §3), plus the Almacén product stock ledger (`product-stock`, `product-movements`).
 * `INVENTORY_FORMULAS` gates the ingredient routes (`ingredients`, `stock`, `movements`) but not the
 * product-stock ones, so it is installed per route; per-method permissions on `ingredients`/`movements` (read vs write) are
 * checked inline for the same reason `catalog/products` is (see [requirePermissionInline]'s doc).
 */
fun Application.inventoryRouter() {
    routing {
        val service by inject<InventoryService>()

        authenticate("auth-jwt") {
            route("inventory") {
                route("ingredients") {
                    requireModule(Module.INVENTORY_FORMULAS)
                    get {
                        if (!call.requirePermissionInline(Permission.CATALOG_READ)) return@get
                        val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false
                        call.respond(HttpStatusCode.OK, service.listIngredients(call.principalContext().tenantId, includeInactive))
                    }

                    post {
                        if (!call.requirePermissionInline(Permission.CATALOG_WRITE)) return@post
                        val body = call.receive<CreateIngredientRequest>()
                        when (val outcome = service.createIngredient(call.principalContext().tenantId, body)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.Created, outcome.value)
                            is ServiceOutcome.Invalid ->
                                call.respond(HttpStatusCode.BadRequest, ErrorBody(code = outcome.code, message = outcome.message))
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "INGREDIENT_NOT_FOUND"))
                        }
                    }

                    route("{id}") {
                        put {
                            if (!call.requirePermissionInline(Permission.CATALOG_WRITE)) return@put
                            val id = call.parameters["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                            if (id == null) {
                                call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "INVALID_ID"))
                                return@put
                            }
                            val body = call.receive<UpdateIngredientRequest>()
                            when (val outcome = service.updateIngredient(call.principalContext().tenantId, id, body)) {
                                is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                                is ServiceOutcome.Invalid ->
                                    call.respond(HttpStatusCode.BadRequest, ErrorBody(code = outcome.code, message = outcome.message))
                                ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "INGREDIENT_NOT_FOUND"))
                            }
                        }
                    }
                }

                route("stock") {
                    requireModule(Module.INVENTORY_FORMULAS)
                    requirePermission(Permission.STOCK_READ)

                    get {
                        val context = call.principalContext()
                        val branchId = call.request.queryParameters["branchId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                        if (branchId == null) {
                            call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "BRANCH_ID_REQUIRED"))
                            return@get
                        }
                        when (val outcome = service.stockLevels(context.tenantId, branchId, context.branchId)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                            is ServiceOutcome.Invalid -> call.respondInvalid(outcome)
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "NOT_FOUND"))
                        }
                    }
                }

                // HU-ANA-0006: ledger balance per ingredient over a closed day range.
                route("reconciliation") {
                    requireModule(Module.INVENTORY_FORMULAS)
                    requirePermission(Permission.STOCK_READ)

                    get {
                        val context = call.principalContext()
                        val params = call.request.queryParameters
                        val branchId = params["branchId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                        if (branchId == null) {
                            call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "BRANCH_ID_REQUIRED"))
                            return@get
                        }
                        val from = params["from"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        val to = params["to"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        if (from == null || to == null) {
                            call.respond(
                                HttpStatusCode.BadRequest,
                                ErrorBody(code = "INVALID_DATE_RANGE", message = "from and to must be YYYY-MM-DD dates")
                            )
                            return@get
                        }
                        when (val outcome = service.reconcile(context.tenantId, branchId, from, to, context.branchId)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                            is ServiceOutcome.Invalid -> call.respondInvalid(outcome)
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "NOT_FOUND"))
                        }
                    }
                }

                route("movements") {
                    requireModule(Module.INVENTORY_FORMULAS)
                    get {
                        if (!call.requirePermissionInline(Permission.STOCK_READ)) return@get
                        val context = call.principalContext()
                        val params = call.request.queryParameters
                        val branchId = params["branchId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                        val ingredientId = params["ingredientId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                        val from = params["from"]?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
                        val to = params["to"]?.toLongOrNull()?.let(Instant::fromEpochMilliseconds)
                        val limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
                        val offset = params["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

                        when (
                            val outcome = service.listMovements(
                                context.tenantId, branchId, ingredientId, from, to, limit, offset, context.branchId
                            )
                        ) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                            is ServiceOutcome.Invalid -> call.respondInvalid(outcome)
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "NOT_FOUND"))
                        }
                    }

                    post {
                        if (!call.requirePermissionInline(Permission.STOCK_ADJUST)) return@post
                        val context = call.principalContext()
                        val body = call.receive<CreateStockMovementRequest>()
                        when (val outcome = service.appendMovement(context.tenantId, context.userId, body, context.branchId)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.Created, outcome.value)
                            is ServiceOutcome.Invalid -> call.respondInvalid(outcome)
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "NOT_FOUND"))
                        }
                    }
                }

                // Product stock of Almacén products (drinks, packaged goods): same permissions and shape
                // as the ingredient ledger above, different table, counted in units not grams. Core
                // catalog, so deliberately NOT behind INVENTORY_FORMULAS: a branch that only sells
                // bought-in goods can still track them.
                route("product-stock") {
                    requirePermission(Permission.STOCK_READ)

                    get {
                        val context = call.principalContext()
                        val branchId = call.request.queryParameters["branchId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                        if (branchId == null) {
                            call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "BRANCH_ID_REQUIRED"))
                            return@get
                        }
                        when (val outcome = service.productStockLevels(context.tenantId, branchId, context.branchId)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                            is ServiceOutcome.Invalid -> call.respondInvalid(outcome)
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "NOT_FOUND"))
                        }
                    }

                    put("{productId}/threshold") {
                        if (!call.requirePermissionInline(Permission.STOCK_ADJUST)) return@put
                        val productId = call.parameters["productId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                        if (productId == null) {
                            call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "INVALID_PRODUCT"))
                            return@put
                        }
                        val body = call.receive<SetProductLowStockThresholdRequest>()
                        when (val outcome = service.setProductLowStockThreshold(call.principalContext().tenantId, productId, body.thresholdUnits)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                            is ServiceOutcome.Invalid -> call.respondInvalid(outcome)
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "NOT_FOUND"))
                        }
                    }
                }

                route("product-movements") {
                    get {
                        if (!call.requirePermissionInline(Permission.STOCK_READ)) return@get
                        val context = call.principalContext()
                        val params = call.request.queryParameters
                        val branchId = params["branchId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                        val productId = params["productId"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }
                        val limit = params["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 100
                        val offset = params["offset"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0

                        when (val outcome = service.listProductMovements(context.tenantId, branchId, productId, limit, offset, context.branchId)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                            is ServiceOutcome.Invalid -> call.respondInvalid(outcome)
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "NOT_FOUND"))
                        }
                    }

                    post {
                        if (!call.requirePermissionInline(Permission.STOCK_ADJUST)) return@post
                        val context = call.principalContext()
                        val body = call.receive<CreateProductStockMovementRequest>()
                        when (val outcome = service.appendProductMovement(context.tenantId, context.userId, body, context.branchId)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.Created, outcome.value)
                            is ServiceOutcome.Invalid -> call.respondInvalid(outcome)
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "PRODUCT_NOT_FOUND"))
                        }
                    }
                }
            }
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondInvalid(outcome: ServiceOutcome.Invalid) {
    val status = if (outcome.code == "BRANCH_FORBIDDEN") HttpStatusCode.Forbidden else HttpStatusCode.BadRequest
    respond(status, ErrorBody(code = outcome.code, message = outcome.message))
}
