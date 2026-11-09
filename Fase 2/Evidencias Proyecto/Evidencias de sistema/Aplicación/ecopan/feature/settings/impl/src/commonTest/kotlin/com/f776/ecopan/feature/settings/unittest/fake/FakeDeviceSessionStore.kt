package com.f776.ecopan.feature.settings.unittest.fake

import com.f776.ecopan.domain.tenant.DeviceSessionStore

class FakeDeviceSessionStore(private val deviceId: String = "device-1", private var deviceName: String? = null) : DeviceSessionStore {
    override suspend fun currentAccessToken(): String? = null
    override suspend fun currentRefreshToken(): String? = null
    override suspend fun deviceId(): String = deviceId
    override suspend fun deviceName(): String? = deviceName
    override suspend fun saveDeviceName(name: String) {
        deviceName = name
    }
    override suspend fun saveTokens(accessToken: String, refreshToken: String) = Unit
    override suspend fun clear() = Unit
}
