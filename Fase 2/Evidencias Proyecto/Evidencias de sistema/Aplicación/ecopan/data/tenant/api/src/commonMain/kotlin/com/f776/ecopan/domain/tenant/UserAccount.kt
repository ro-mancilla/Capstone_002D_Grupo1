package com.f776.ecopan.domain.tenant

import com.f776.ecopan.core.domain.auth.Role
import kotlin.uuid.Uuid

/**
 * A cached user this device's tenant knows about — the offline unlock list (01 §4 device
 * session + PIN unlock). [hasPin] only reports whether a PIN is set; the hash itself never
 * leaves the local cache/DAO layer. Local verification is `:core:crypto`'s `PinVerifier`
 * (bcrypt, matching server-side hashing) — JVM/Android share `at.favre.lib:bcrypt` directly;
 * iOS needs a cinterop-bound C bcrypt implementation, not yet wired (see that module's iosMain).
 */
data class UserAccount(
    val id: Uuid,
    val tenantId: Uuid,
    val branchId: Uuid?,
    val username: String,
    val displayName: String,
    val role: Role,
    val active: Boolean,
    val hasPin: Boolean,
)
