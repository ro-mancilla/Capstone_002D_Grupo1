package com.f776.ecopan.api

import com.f776.ecopan.api.core.clientAddress
import kotlin.test.Test
import kotlin.test.assertEquals

/** #129: the key the auth limiter (and anything else that names a client) uses for one caller. */
class ClientAddressTest {

    private val peer = "10.0.0.1"

    private fun trusted(forwardedFor: String?) = clientAddress(forwardedFor, peer, behindTrustedProxy = true)

    @Test
    fun `without a trusted proxy the header is ignored`() {
        assertEquals(peer, clientAddress("203.0.113.9", peer, behindTrustedProxy = false))
        assertEquals(peer, clientAddress(null, peer, behindTrustedProxy = false))
    }

    @Test
    fun `no header falls back to the peer`() {
        assertEquals(peer, trusted(null))
        assertEquals(peer, trusted("  "))
    }

    @Test
    fun `IPv4 with and without a port`() {
        assertEquals("203.0.113.9", trusted("203.0.113.9:51000"))
        assertEquals("203.0.113.9", trusted("203.0.113.9"))
    }

    @Test
    fun `only the last entry counts`() {
        assertEquals("203.0.113.9", trusted("1.1.1.1, 9.9.9.9:80, 203.0.113.9:51000"))
    }

    @Test
    fun `bracketed IPv6 with a port is keyed by its slash-64`() {
        assertEquals("2001:db8:0:0::/64", trusted("[2001:db8::7]:51000"))
        assertEquals("2001:db8:0:0::/64", trusted("[2001:db8::7]"))
    }

    @Test
    fun `bare IPv6 is keyed by its slash-64`() {
        assertEquals("2001:db8:1:2::/64", trusted("2001:db8:1:2:3:4:5:6"))
        assertEquals("2001:db8:1:2::/64", trusted("2001:DB8:1:2::ff%eth0"))
    }

    @Test
    fun `two addresses in one slash-64 get the same key, a neighbouring prefix does not`() {
        assertEquals(trusted("2001:db8:a:b::1"), trusted("[2001:db8:a:b:ffff::2]:9"))
        assert(trusted("2001:db8:a:b::1") != trusted("2001:db8:a:c::1"))
    }

    @Test
    fun `an IPv4-mapped IPv6 address is keyed as IPv4`() {
        assertEquals("203.0.113.9", trusted("[::ffff:203.0.113.9]:443"))
    }

    @Test
    fun `garbage is kept verbatim rather than collapsing everybody onto one key`() {
        assertEquals("not-an-address", trusted("not-an-address"))
    }
}
