package com.f776.ecopan.api.core

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import org.slf4j.LoggerFactory
import org.koin.ktor.ext.inject

/**
 * Browser access for `webApp/` (plan 07): the dashboard runs on its own dev-server origin
 * (`localhost:5173`) and calls this API on `localhost:8080`, which is cross-origin, so without this
 * every request fails preflight before reaching a route. The KMP clients are unaffected — CORS is a
 * browser policy and the desktop/mobile HTTP clients never enforce it.
 *
 * Origins are enumerated, never wildcarded. `anyHost()` is deliberately not offered even as an
 * opt-in: this API is multi-tenant and bearer-authenticated, and the only clients that need CORS at
 * all are ones we can name. A deployment adds its dashboard origin to `CORS_ALLOWED_ORIGINS`.
 *
 * [allowCredentials] stays off because the web client authenticates with an `Authorization` header
 * from `localStorage`, not cookies (see `webApp/src/api/http.ts`). Turning it on would let browsers
 * attach ambient credentials to cross-origin calls, which is exactly the risk this token scheme
 * avoids, and it buys nothing.
 */
fun Application.configureCors() {
    val settings by inject<Settings>()
    val logger = LoggerFactory.getLogger("com.f776.ecopan.api.cors")

    val origins = settings.corsAllowedOrigins
    if (origins.isEmpty()) {
        logger.warn("CORS_ALLOWED_ORIGINS is empty — no browser origin may call this API")
        return
    }

    install(CORS) {
        origins.forEach { origin ->
            val parsed = ParsedOrigin.parse(origin)
            if (parsed == null) {
                // Skipped rather than fatal: one typo'd origin must not stop the whole API booting,
                // and a warning naming the bad value is far easier to act on than a failed preflight.
                logger.warn("ignoring malformed CORS origin '$origin' — expected e.g. http://localhost:5173")
            } else {
                allowHost(parsed.hostAndPort, schemes = listOf(parsed.scheme))
            }
        }

        // Defaults cover GET/POST/HEAD only; the dashboard's admin CRUD also needs these.
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        allowCredentials = false
    }

    logger.info("CORS enabled for ${origins.joinToString(", ")}")
}

/**
 * `allowHost` wants host[:port] and schemes as separate arguments, while configuration carries
 * whole origins (`http://localhost:5173`) — that is the form browsers send in `Origin` and the form
 * a deployer copies out of a URL bar, so splitting it here keeps the config surface unsurprising.
 */
private data class ParsedOrigin(val scheme: String, val hostAndPort: String) {
    companion object {
        fun parse(raw: String): ParsedOrigin? {
            val trimmed = raw.trim().trimEnd('/')
            val separator = trimmed.indexOf("://")
            if (separator <= 0) return null

            val scheme = trimmed.substring(0, separator)
            val hostAndPort = trimmed.substring(separator + 3)
            if (hostAndPort.isEmpty() || '/' in hostAndPort) return null

            return ParsedOrigin(scheme, hostAndPort)
        }
    }
}
