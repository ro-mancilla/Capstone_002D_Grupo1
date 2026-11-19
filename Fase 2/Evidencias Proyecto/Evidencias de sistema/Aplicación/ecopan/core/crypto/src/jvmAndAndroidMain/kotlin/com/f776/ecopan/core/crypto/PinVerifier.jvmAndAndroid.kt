package com.f776.ecopan.core.crypto

import at.favre.lib.crypto.bcrypt.BCrypt

internal actual class PinVerifierImpl actual constructor() : PinVerifier {
    actual override fun verify(pin: String, hash: String): Boolean =
        BCrypt.verifyer().verify(pin.toCharArray(), hash).verified

    actual override fun hash(pin: String): String =
        BCrypt.withDefaults().hashToString(12, pin.toCharArray())
}
