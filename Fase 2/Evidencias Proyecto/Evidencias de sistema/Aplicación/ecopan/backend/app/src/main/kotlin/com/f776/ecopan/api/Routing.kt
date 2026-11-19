package com.f776.ecopan.api

import com.f776.ecopan.api.feature.analytics.controller.analyticsRouter
import com.f776.ecopan.api.feature.auth.controller.authRouter
import com.f776.ecopan.api.feature.catalog.controller.catalogRouter
import com.f776.ecopan.api.feature.events.controller.eventsRouter
import com.f776.ecopan.api.feature.forecasts.controller.forecastsRouter
import com.f776.ecopan.api.feature.inventory.controller.inventoryRouter
import com.f776.ecopan.api.feature.sync.controller.syncRouter
import com.f776.ecopan.api.feature.tenant.controller.tenantRouter
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun Application.configureRouting() {
    routing {
        // Public: health check + login/refresh (02 §3 -- everything else requires auth-jwt).
        get("/") { call.respond(HttpStatusCode.OK) }
        // Same check under the path the desktop client actually probes: `ConnectivityMonitorImpl`
        // (jvmMain) polls `/health` per 03 §6. Without this route it got a 404, read that as "not
        // reachable", and pinned the desktop till to OFFLINE forever -- the sync indicator never
        // went green and the scheduler's connectivity-edge trigger never fired.
        // The BUILD_ID header names the image tag this process was deployed from (unset locally), so a
        // deploy script can tell the new container from the old one that keeps serving during a rollout.
        get("/health") {
            System.getenv("BUILD_ID")?.let { call.response.headers.append("X-Build-Id", it) }
            call.respond(HttpStatusCode.OK)
        }
    }
    authRouter()
    eventsRouter()
    tenantRouter()
    catalogRouter()
    inventoryRouter()
    syncRouter()
    analyticsRouter()
    forecastsRouter()
}
