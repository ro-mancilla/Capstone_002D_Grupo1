package com.f776.ecopan.data.sync.unittest

import com.f776.ecopan.data.sync.ConnectivityMonitorImpl
import com.f776.ecopan.domain.sync.ConnectivityStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectivityMonitorJvmTest {

    private fun monitor(engine: MockEngine) = ConnectivityMonitorImpl(HttpClient(engine), "http://backend")

    @Test
    fun aBackendAnsweringWithAnErrorIsStillReachable() = runTest {
        val status = monitor(MockEngine { respond("", HttpStatusCode.InternalServerError) }).observe().first()

        assertEquals(ConnectivityStatus.ONLINE, status)
    }

    @Test
    fun aBackendThatCannotBeReachedIsOffline() = runTest {
        val status = monitor(MockEngine { throw java.io.IOException("no route") }).observe().first()

        assertEquals(ConnectivityStatus.OFFLINE, status)
    }
}
