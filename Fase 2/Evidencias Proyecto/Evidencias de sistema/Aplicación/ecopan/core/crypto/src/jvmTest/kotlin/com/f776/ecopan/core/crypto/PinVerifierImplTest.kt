package com.f776.ecopan.core.crypto

import at.favre.lib.crypto.bcrypt.BCrypt
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PinVerifierImplTest {

    private val verifier = PinVerifierImpl()

    // Known-good vector from the patrickfav/bcrypt README: BCrypt.withDefaults().hashToString(12, "1234").
    private val pin = "1234"
    private val hash = "\$2a\$12\$US00g/uMhoSBm.HiuieBjeMtoN69SN.GE25fCpldebzkryUyopws6"

    @Test
    fun `a matching pin verifies against its bcrypt hash`() {
        assertTrue(verifier.verify(pin, hash))
    }

    @Test
    fun `a wrong pin does not verify`() {
        assertFalse(verifier.verify("0000", hash))
    }

    @Test
    fun `a freshly hashed pin round-trips through verify`() {
        val freshHash = BCrypt.withDefaults().hashToString(4, "9317".toCharArray())

        assertTrue(verifier.verify("9317", freshHash))
        assertFalse(verifier.verify("1739", freshHash))
    }
}
