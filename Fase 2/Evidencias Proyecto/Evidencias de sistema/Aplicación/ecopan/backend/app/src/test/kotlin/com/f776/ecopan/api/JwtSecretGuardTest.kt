package com.f776.ecopan.api

import com.f776.ecopan.api.core.Settings
import io.ktor.server.config.MapApplicationConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** #128: the backend will not sign tokens with the well-known development secret unless told it is local. */
class JwtSecretGuardTest {

    private fun settings(vararg pairs: Pair<String, String>) = Settings(MapApplicationConfig(*pairs))

    @Test
    fun `no secret at all refuses to start and names the setting`() {
        val e = assertFailsWith<IllegalStateException> { settings() }
        assertTrue("JWT_SECRET" in e.message.orEmpty(), e.message)
    }

    @Test
    fun `the development secret refuses to start without the local flag`() {
        val e = assertFailsWith<IllegalStateException> { settings("jwt.secret" to "dev-secret") }
        assertTrue("JWT_SECRET" in e.message.orEmpty() && "JWT_ALLOW_DEV_SECRET" in e.message.orEmpty(), e.message)
        assertFailsWith<IllegalStateException> { settings("jwt.secret" to "dev-secret", "jwt.allowDevSecret" to "false") }
        assertFailsWith<IllegalStateException> { settings("jwt.secret" to "  ") }
    }

    @Test
    fun `the development secret starts with the local flag on`() {
        assertEquals("dev-secret", settings("jwt.secret" to "dev-secret", "jwt.allowDevSecret" to "true").jwtSettings.secret)
        assertEquals("dev-secret", settings("jwt.allowDevSecret" to "true").jwtSettings.secret)
    }

    @Test
    fun `a real secret starts without the flag`() {
        assertEquals("a-real-secret", settings("jwt.secret" to "a-real-secret").jwtSettings.secret)
    }
}
