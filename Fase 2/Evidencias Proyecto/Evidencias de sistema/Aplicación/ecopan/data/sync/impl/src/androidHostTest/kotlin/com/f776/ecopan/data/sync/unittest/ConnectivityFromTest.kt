package com.f776.ecopan.data.sync.unittest

import com.f776.ecopan.data.sync.connectivityFrom
import com.f776.ecopan.domain.sync.ConnectivityStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectivityFromTest {

    @Test
    fun noActiveNetworkIsOffline() {
        assertEquals(ConnectivityStatus.OFFLINE, connectivityFrom(validated = null))
    }

    @Test
    fun anAssociatedButUnvalidatedNetworkIsOffline() {
        assertEquals(ConnectivityStatus.OFFLINE, connectivityFrom(validated = false))
    }

    @Test
    fun aValidatedNetworkIsOnline() {
        assertEquals(ConnectivityStatus.ONLINE, connectivityFrom(validated = true))
    }
}
