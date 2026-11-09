package com.f776.ecopan.domain.tenant

/**
 * Read/write contract over this device's persisted session tokens (01 §4: a long-lived *device*
 * session, bound at first-login enrollment — not a per-shift/per-user login). Defined here, in
 * `:data:tenant:api`, rather than in `:data:sync:api` or `:data:sync:impl`, so that
 * `:data:sync:impl`'s real `SyncAuthProvider` (03 §3.4) can read/refresh tokens without depending
 * on `:data:tenant:impl` directly — it already depends on `:data:tenant:api` for session per 03
 * §1.2 ("Depends on `:data:tenant:api` for session"). The concrete implementation
 * (`:data:tenant:impl`'s internal `TokenStore`, DataStore-backed) is bound to this interface in
 * `TenantDataModule`.
 */
interface DeviceSessionStore {
    /** `null` means no device session yet (fresh install, never enrolled). */
    suspend fun currentAccessToken(): String?
    suspend fun currentRefreshToken(): String?

    /**
     * This install's stable identifier — generated once, persisted, and sent on every push
     * envelope (03 §3.2). Exposed because settings shows it: it is the only thing a baker can read
     * back to support to say *which* device is misbehaving, and it is not derivable from anything
     * else on screen. Survives logout; only a re-enrollment ([clear]) changes it.
     */
    suspend fun deviceId(): String

    /**
     * The name this device was enrolled under ("Caja 1"), as the person typed it at login. Shown
     * in settings next to the branch, in place of [deviceId], which is for support and never for
     * display. `null` for a device enrolled before the name was kept.
     */
    suspend fun deviceName(): String?

    suspend fun saveDeviceName(name: String)

    /** Persists a (possibly refreshed) token pair. Called after login and after a successful refresh. */
    suspend fun saveTokens(accessToken: String, refreshToken: String)

    /** Wipes the device session — device re-enrollment / "reset local data" (03 §8). */
    suspend fun clear()
}
