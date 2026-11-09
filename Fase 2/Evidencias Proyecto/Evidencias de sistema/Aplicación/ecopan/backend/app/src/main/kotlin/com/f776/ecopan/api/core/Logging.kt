package com.f776.ecopan.api.core

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.calllogging.processingTimeMillis
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

/**
 * One line per request. Nothing logged requests before this, which made the backend effectively
 * opaque during local testing: a client reporting "usuario o contraseña incorrectos" looked
 * identical to a client that never reached the server at all, and the only way to tell them apart
 * was curl.
 *
 * Deliberately logs method + path + status + duration only (the path, not the query string: the
 * SSE stream carries its access token there) — never the body, never headers. Login and refresh
 * bodies carry passwords and refresh tokens, and a log that quietly captures them is worse than no
 * log at all.
 */
fun Application.configureLogging() {
    install(CallLogging) {
        // Route through a `com.f776` category rather than Ktor's application logger: logback.xml
        // keeps the root at WARN and only raises `com.f776` (and `io.ktor`) -- but nothing from
        // `io.ktor` actually reaches the console in this setup, not even the startup banner, so
        // relying on that category silently produced no request logs at all.
        logger = LoggerFactory.getLogger("com.f776.ecopan.api.http")
        level = Level.INFO
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()?.value ?: "no response"} (${call.processingTimeMillis()} ms)"
        }
    }
}
