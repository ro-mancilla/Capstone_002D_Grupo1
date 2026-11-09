package com.f776.ecopan.data.core

import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * A one-route HTTP server that answers `GET /health` with `200 ok`.
 *
 * The worker and the notifier are headless queue consumers — they have no HTTP surface and want
 * none. This exists for one reason: **Azure App Service requires it.**
 *
 * App Service treats a Linux custom container as a web app whatever it actually is. At startup it
 * polls the container's port, and if nothing answers within `WEBSITES_CONTAINER_START_TIME_LIMIT`
 * it declares the container failed and restarts it — forever, in a loop, no matter how healthy the
 * consumer inside is. Ten lines of `com.sun.net.httpserver` (JDK built-in, so no new dependency for
 * two services that otherwise pull in no server framework at all) is the cheapest way to satisfy
 * that contract.
 *
 * Note what this does *not* claim. It reports that the process is alive, not that it is consuming:
 * a worker whose broker connection is wedged still answers 200. Genuine liveness would mean
 * tracking last-message-processed time and failing the probe past a threshold — worth doing if this
 * were ever fronted by a real orchestrator's liveness check, and deliberately not done here,
 * because App Service would respond by restarting the container during a quiet afternoon in a
 * bakery when "no messages for an hour" is the correct state.
 */
fun startHealthEndpoint(port: Int = healthPortFromEnv()): HttpServer {
    val logger = LoggerFactory.getLogger("com.f776.ecopan.data.core.HealthEndpoint")
    // Backlog 0 = the JVM's default. Nothing here is under load; it answers a probe every so often.
    val server = HttpServer.create(InetSocketAddress(port), 0)
    server.createContext("/health") { exchange ->
        val body = "ok".toByteArray()
        exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }
    // Any other path also gets a 200: App Service probes "/" by default, and a 404 there is
    // reported as an unhealthy container even though the process is fine.
    server.createContext("/") { exchange ->
        exchange.sendResponseHeaders(200, -1)
        exchange.close()
    }
    server.start()
    logger.info("Health endpoint listening on :{}/health", port)
    return server
}

/**
 * App Service injects `PORT` (and `WEBSITES_PORT` when the image declares one) and expects the
 * container to bind exactly that. Falling back to 8081 keeps `docker compose` unchanged, where
 * neither variable is set and 8080 already belongs to the API.
 */
private fun healthPortFromEnv(): Int =
    (System.getenv("PORT") ?: System.getenv("WEBSITES_PORT"))?.toIntOrNull() ?: 8081
