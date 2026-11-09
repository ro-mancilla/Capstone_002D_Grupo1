package com.f776.ecopan.data.tenant.unittest.fake

import com.f776.ecopan.core.crypto.PinVerifier

/** Treats [hash] as the plaintext PIN itself — no real bcrypt needed for repository-level tests. */
internal class FakePinVerifier : PinVerifier {
    override fun verify(pin: String, hash: String): Boolean = pin == hash
    override fun hash(pin: String): String = pin
}
