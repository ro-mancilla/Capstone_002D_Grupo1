package com.f776.ecopan.api.core

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.http.HttpHeaders
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.RateLimiter
import io.ktor.server.response.header
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.minutes

/** The limiter on `/auth/login` and `/auth/refresh`, which shares one bucket per client address. */
val AuthRateLimit = RateLimitName("auth")

private val log = LoggerFactory.getLogger("com.f776.ecopan.api.ratelimit")

/**
 * Login costs a bcrypt hash (cost 12) on a shared two-vCPU instance, so an unauthenticated flood of
 * attempts starves everyone, including the people signing in correctly. The default of 30 a minute
 * per address is a few bakers signing in at the start of a shift, nowhere near a script.
 *
 * Account lockout is deliberately not done: it would let anyone lock the manager out.
 */
fun Application.configureRateLimit() {
    val perMinute = environment.config.propertyOrNull("rateLimit.authPerMinute")?.getString()?.toIntOrNull() ?: 30
    install(RateLimit) {
        register(AuthRateLimit) {
            rateLimiter(limit = perMinute, refillPeriod = 1.minutes)
            requestKey { call -> call.clientAddress() }
            modifyResponse { call, state ->
                // Setting modifyResponse replaces Ktor's default, so Retry-After is ours to write.
                if (state is RateLimiter.State.Exhausted) {
                    call.response.header(HttpHeaders.RetryAfter, state.toWait.inWholeSeconds.coerceAtLeast(1))
                    log.warn("auth rate limit hit by {}", call.clientAddress())
                }
            }
        }
    }
}
