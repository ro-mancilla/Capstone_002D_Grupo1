package com.f776.ecopan.api.feature.sync.controller

import com.f776.ecopan.api.core.ErrorBody
import com.f776.ecopan.api.core.principalContext
import com.f776.ecopan.api.core.requirePermission
import com.f776.ecopan.api.feature.sync.controller.dto.PushRequest
import com.f776.ecopan.api.feature.sync.domain.SyncIngestService
import com.f776.ecopan.api.feature.sync.domain.SyncPullService
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.SyncEntityType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.log
import kotlinx.coroutines.CancellationException
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

/** S1/S2 (02 §4) -- the frozen sync wire contract. */
fun Application.syncRouter() {
    routing {
        val ingestService by inject<SyncIngestService>()
        val pullService by inject<SyncPullService>()

        authenticate("auth-jwt") {
            route("sync") {
                requirePermission(Permission.SYNC)

                post("push") {
                    val context = call.principalContext()
                    val body = call.receive<PushRequest>()
                    if (body.mutations.size > 200) {
                        call.respond(HttpStatusCode.BadRequest, ErrorBody(code = "TOO_MANY_MUTATIONS", message = "max 200 mutations per push"))
                        return@post
                    }
                    val response = try {
                        ingestService.push(
                            tenantId = context.tenantId,
                            principalUserId = context.userId,
                            principalBranchId = context.branchId,
                            deviceId = body.deviceId,
                            mutations = body.mutations
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A database or broker failure (#135): nothing is wrong with the mutations, so
                        // none is REJECTED. The till keeps them pending and retries the whole push;
                        // whatever already landed comes back DUPLICATE.
                        call.application.log.error("sync push from device {} failed on infrastructure", body.deviceId, e)
                        call.respond(HttpStatusCode.ServiceUnavailable, ErrorBody(code = "SYNC_UNAVAILABLE"))
                        return@post
                    }
                    call.respond(HttpStatusCode.OK, response)
                }

                get("pull") {
                    val context = call.principalContext()
                    val params = call.request.queryParameters
                    val since = params["since"]?.toLongOrNull() ?: 0L
                    val limit = (params["limit"]?.toIntOrNull() ?: 500).coerceIn(1, 500)
                    val entityTypes = params["entityTypes"]?.split(",")?.mapNotNull { name ->
                        runCatching { SyncEntityType.valueOf(name.trim()) }.getOrNull()
                    }?.toSet()

                    val response = pullService.pull(context.tenantId, context.branchId, since, limit, entityTypes)
                    call.respond(HttpStatusCode.OK, response)
                }
            }
        }
    }
}
