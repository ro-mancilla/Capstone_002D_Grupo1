package com.f776.ecopan.api.core

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.header
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * Whether `X-Forwarded-For` can be believed (`proxy.trusted`, env `BEHIND_TRUSTED_PROXY`). On in
 * Azure, where App Service's front end appends the real client; off by default, because a backend
 * reached directly would let every request name its own address and so its own rate-limit bucket.
 */
internal fun ApplicationCall.behindTrustedProxy(): Boolean =
    application.environment.config.propertyOrNull("proxy.trusted")?.getString().toBoolean()

/** The caller, as the auth limiter and the request log name it. See [clientAddress]. */
internal fun ApplicationCall.clientAddress(): String =
    clientAddress(request.header("X-Forwarded-For"), request.origin.remoteAddress, behindTrustedProxy())

/**
 * One key per client (#129).
 *
 * Behind a trusted proxy every connection comes from the proxy, so the key is the LAST
 * `X-Forwarded-For` entry: the platform appends the real client there, and anything before it was
 * sent by the client. That entry may carry a port (`1.2.3.4:5678`, `[2001:db8::7]:5678`), which
 * changes per connection and is dropped. An IPv6 client usually holds a whole /64, so it is keyed
 * by the /64: otherwise rotating the low bits would buy a fresh bucket per request.
 *
 * Not behind a trusted proxy, the header is ignored and the peer address is the key.
 */
internal fun clientAddress(forwardedFor: String?, peer: String, behindTrustedProxy: Boolean): String {
    if (!behindTrustedProxy) return normalise(peer)
    val last = forwardedFor?.substringAfterLast(',')?.trim().orEmpty()
    return if (last.isEmpty()) normalise(peer) else normalise(last)
}

private val IPV4_WITH_OPTIONAL_PORT = Regex("""^(\d{1,3}(?:\.\d{1,3}){3})(?::\d+)?$""")
private val BRACKETED_IPV6 = Regex("""^\[([^\]]+)](?::\d+)?$""")

private fun normalise(raw: String): String {
    IPV4_WITH_OPTIONAL_PORT.matchEntire(raw)?.let { return it.groupValues[1] }
    val v6 = BRACKETED_IPV6.matchEntire(raw)?.groupValues?.get(1) ?: raw.takeIf { ':' in it } ?: return raw
    // A string containing ':' is only ever parsed as an IPv6 literal, never looked up in DNS.
    val address = try {
        InetAddress.getByName(v6.substringBefore('%'))
    } catch (_: UnknownHostException) {
        return raw
    }
    if (address !is Inet6Address) return address.hostAddress // IPv4-mapped (::ffff:a.b.c.d)
    val b = address.address
    val groups = (0 until 4).map { i -> ((b[2 * i].toInt() and 0xff) shl 8) or (b[2 * i + 1].toInt() and 0xff) }
    return groups.joinToString(":") { it.toString(16) } + "::/64"
}
