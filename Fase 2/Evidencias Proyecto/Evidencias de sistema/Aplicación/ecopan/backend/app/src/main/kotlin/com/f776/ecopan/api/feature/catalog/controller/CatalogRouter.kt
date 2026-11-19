package com.f776.ecopan.api.feature.catalog.controller

import com.f776.ecopan.api.core.ErrorBody
import com.f776.ecopan.api.core.ServiceOutcome
import com.f776.ecopan.api.core.principalContext
import com.f776.ecopan.api.core.requireModule
import com.f776.ecopan.api.core.requirePermission
import com.f776.ecopan.api.core.requirePermissionInline
import com.f776.ecopan.api.core.storage.StorageUnavailableException
import com.f776.ecopan.api.feature.catalog.controller.dto.ConfirmImageRequest
import com.f776.ecopan.api.feature.catalog.controller.dto.CreateProductRequest
import com.f776.ecopan.api.feature.catalog.controller.dto.ImageUploadGrantRequest
import com.f776.ecopan.api.feature.catalog.controller.dto.PutFormulaRequest
import com.f776.ecopan.api.feature.catalog.controller.dto.UpdateProductRequest
import com.f776.ecopan.api.feature.catalog.domain.CatalogService
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import kotlin.uuid.Uuid

/**
 * C1-C6 (02 §3). `catalog/products` mixes a read permission on one HTTP method with a write
 * permission on another *at the same path* -- handled
 * with [requirePermissionInline] there instead of the route-scoped [requirePermission] (see that
 * function's doc for why). Sub-paths that are single-method/single-permission (`formula`,
 * `formulas`) use the normal route-scoped guards.
 */
fun Application.catalogRouter() {
    routing {
        val service by inject<CatalogService>()

        authenticate("auth-jwt") {
            route("catalog") {
                route("products") {
                    get {
                        if (!call.requirePermissionInline(Permission.CATALOG_READ)) return@get
                        val includeInactive = call.request.queryParameters["includeInactive"]?.toBoolean() ?: false
                        call.respond(HttpStatusCode.OK, service.listProducts(call.principalContext().tenantId, includeInactive))
                    }

                    post {
                        if (!call.requirePermissionInline(Permission.CATALOG_WRITE)) return@post
                        val body = call.receive<CreateProductRequest>()
                        val context = call.principalContext()
                        when (val outcome = service.createProduct(context.tenantId, body, context.userId, context.branchId)) {
                            is ServiceOutcome.Success -> call.respond(HttpStatusCode.Created, outcome.value)
                            is ServiceOutcome.Invalid -> call.respond(
                                if (outcome.code == "BRANCH_FORBIDDEN") HttpStatusCode.Forbidden else HttpStatusCode.BadRequest,
                                ErrorBody(code = outcome.code, message = outcome.message)
                            )
                            ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "PRODUCT_NOT_FOUND"))
                        }
                    }

                    route("{id}") {
                        put {
                            if (!call.requirePermissionInline(Permission.CATALOG_WRITE)) return@put
                            val id = call.parameters.productId() ?: return@put call.respondInvalidId()
                            val body = call.receive<UpdateProductRequest>()
                            when (val outcome = service.updateProduct(call.principalContext().tenantId, id, body)) {
                                is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                                is ServiceOutcome.Invalid ->
                                    call.respond(HttpStatusCode.BadRequest, ErrorBody(code = outcome.code, message = outcome.message))
                                ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "PRODUCT_NOT_FOUND"))
                            }
                        }

                        // Product photo. Bytes never touch this API: `upload-url` signs a
                        // one-blob, minutes-long write grant, the client PUTs the file straight
                        // to storage, then `PUT image` confirms the key and the product starts
                        // pointing at the blob's public URL. Storage trouble is a 503, not a 500.
                        route("image") {
                            post("upload-url") {
                                if (!call.requirePermissionInline(Permission.CATALOG_WRITE)) return@post
                                val id = call.parameters.productId() ?: return@post call.respondInvalidId()
                                val body = call.receive<ImageUploadGrantRequest>()
                                val outcome = try {
                                    service.grantProductImageUpload(call.principalContext().tenantId, id, body.contentType)
                                } catch (e: StorageUnavailableException) {
                                    return@post call.respondStorageUnavailable(e)
                                }
                                call.respondImageOutcome(outcome)
                            }

                            put {
                                if (!call.requirePermissionInline(Permission.CATALOG_WRITE)) return@put
                                val id = call.parameters.productId() ?: return@put call.respondInvalidId()
                                val body = call.receive<ConfirmImageRequest>()
                                val outcome = try {
                                    service.confirmProductImage(call.principalContext().tenantId, id, body.key)
                                } catch (e: StorageUnavailableException) {
                                    return@put call.respondStorageUnavailable(e)
                                }
                                call.respondImageOutcome(outcome)
                            }

                            delete {
                                if (!call.requirePermissionInline(Permission.CATALOG_WRITE)) return@delete
                                val id = call.parameters.productId() ?: return@delete call.respondInvalidId()
                                val outcome = try {
                                    service.deleteProductImage(call.principalContext().tenantId, id)
                                } catch (e: StorageUnavailableException) {
                                    return@delete call.respondStorageUnavailable(e)
                                }
                                call.respondImageOutcome(outcome)
                            }
                        }

                        delete {
                            if (!call.requirePermissionInline(Permission.CATALOG_WRITE)) return@delete
                            val id = call.parameters.productId() ?: return@delete call.respondInvalidId()
                            val deleted = service.deleteProduct(call.principalContext().tenantId, id)
                            if (deleted) {
                                call.respond(HttpStatusCode.NoContent)
                            } else {
                                call.respond(HttpStatusCode.NotFound, ErrorBody(code = "PRODUCT_NOT_FOUND"))
                            }
                        }

                        route("formula") {
                            requirePermission(Permission.CATALOG_WRITE)
                            requireModule(Module.INVENTORY_FORMULAS)

                            put {
                                val id = call.parameters.productId() ?: return@put call.respondInvalidId()
                                val body = call.receive<PutFormulaRequest>()
                                when (val outcome = service.putFormula(call.principalContext().tenantId, id, body)) {
                                    is ServiceOutcome.Success -> call.respond(HttpStatusCode.OK, outcome.value)
                                    is ServiceOutcome.Invalid ->
                                        call.respond(HttpStatusCode.BadRequest, ErrorBody(code = outcome.code, message = outcome.message))
                                    ServiceOutcome.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorBody(code = "PRODUCT_NOT_FOUND"))
                                }
                            }
                        }
                    }
                }

                route("formulas") {
                    requirePermission(Permission.CATALOG_READ)
                    requireModule(Module.INVENTORY_FORMULAS)

                    get {
                        call.respond(HttpStatusCode.OK, service.listFormulas(call.principalContext().tenantId))
                    }
                }
            }
        }
    }
}

private fun Parameters.productId(): Uuid? = this["id"]?.let { runCatching { Uuid.parse(it) }.getOrNull() }

private suspend fun ApplicationCall.respondInvalidId() =
    respond(HttpStatusCode.BadRequest, ErrorBody(code = "INVALID_ID"))

private suspend inline fun <reified T : Any> ApplicationCall.respondImageOutcome(outcome: ServiceOutcome<T>) {
    when (outcome) {
        is ServiceOutcome.Success -> respond(HttpStatusCode.OK, outcome.value)
        is ServiceOutcome.Invalid -> {
            // The request was well-formed; what the client uploaded is what is wrong (#138).
            val status = if (outcome.code in REJECTED_UPLOAD_CODES) HttpStatusCode.UnprocessableEntity else HttpStatusCode.BadRequest
            respond(status, ErrorBody(code = outcome.code, message = outcome.message))
        }
        ServiceOutcome.NotFound -> respond(HttpStatusCode.NotFound, ErrorBody(code = "PRODUCT_NOT_FOUND"))
    }
}

private val REJECTED_UPLOAD_CODES = setOf("IMAGE_TOO_LARGE", "IMAGE_NOT_AN_IMAGE")

private suspend fun ApplicationCall.respondStorageUnavailable(e: StorageUnavailableException) {
    respond(HttpStatusCode.ServiceUnavailable, ErrorBody(code = "STORAGE_UNAVAILABLE", message = e.message))
}
