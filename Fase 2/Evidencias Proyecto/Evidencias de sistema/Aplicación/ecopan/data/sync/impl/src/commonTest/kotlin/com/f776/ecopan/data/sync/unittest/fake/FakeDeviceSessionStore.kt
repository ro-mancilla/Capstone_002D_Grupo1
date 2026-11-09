package com.f776.ecopan.data.sync.unittest.fake

import com.f776.ecopan.domain.tenant.DeviceSessionStore

internal class FakeDeviceSessionStore(
    private var accessToken: String? = null,
    private var refreshToken: String? = null,
) : DeviceSessionStore {
    var saveTokensCallCount = 0
        private set

    override suspend fun currentAccessToken(): String? = accessToken
    override suspend fun currentRefreshToken(): String? = refreshToken

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        saveTokensCallCount++
        this.accessToken = accessToken
        this.refreshToken = refreshToken
    }

    override suspend fun deviceId(): String = "fake-device"
    override suspend fun deviceName(): String? = null
    override suspend fun saveDeviceName(name: String) = Unit

    override suspend fun clear() {
        accessToken = null
        refreshToken = null
    }
}
