package com.f776

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {

    @Test
    fun `test root endpoint`() = testApplication {
        // application.yaml plus a longer module-loading timeout (see server-test.yaml)
        configure("application.yaml", "server-test.yaml")
        // verify server root returns 200
        assertEquals(HttpStatusCode.OK, client.get("/").status)
    }

    /**
     * The desktop client's `ConnectivityMonitorImpl` (jvmMain) polls `/health` per 03 §6 and treats
     * any non-2xx as "backend unreachable". That route did not exist -- only `/` did -- so every
     * desktop till reported OFFLINE permanently. This test is what stops it regressing.
     */
    @Test
    fun `health endpoint answers the desktop connectivity probe`() = testApplication {
        configure("application.yaml", "server-test.yaml")
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }

}
