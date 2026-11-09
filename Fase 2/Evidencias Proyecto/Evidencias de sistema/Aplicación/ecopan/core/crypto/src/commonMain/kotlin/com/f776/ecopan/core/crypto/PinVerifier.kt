package com.f776.ecopan.core.crypto

/**
 * Local-only bcrypt verification for the offline PIN unlock (01 §4 device session + PIN unlock).
 * [verify] never talks to the network — it checks [pin] against a [hash] already cached from the
 * `USER` pull payload (02 §4.2). This is an *unlock* check, not the primary authentication
 * boundary: the device session is what authenticates every request; the PIN only attributes
 * which cached user performed the action.
 */
interface PinVerifier {
    fun verify(pin: String, hash: String): Boolean

    /**
     * Hashes a freshly chosen PIN for local storage. Real device enrollment (01 §4) syncs
     * server-hashed PINs down via the `USER` pull; this exists for the offline demo-seed path
     * `:feature:auth`'s enrollment screen uses until a real backend/sync layer exists (spec/SPECS.md
     * "synthetic data: demo-only" — never used for anything but seeding this local demo).
     */
    fun hash(pin: String): String
}

internal expect class PinVerifierImpl() : PinVerifier {
    override fun verify(pin: String, hash: String): Boolean
    override fun hash(pin: String): String
}
