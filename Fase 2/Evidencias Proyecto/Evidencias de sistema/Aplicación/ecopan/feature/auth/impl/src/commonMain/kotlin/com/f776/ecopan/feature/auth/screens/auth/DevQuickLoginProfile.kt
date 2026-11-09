package com.f776.ecopan.feature.auth.screens.auth

import com.f776.ecopan.core.domain.auth.Role

/**
 * Seeded `reference` tenant users (README) — dev-only one-tap enrollment shortcuts. The button
 * label comes from [role] via [Role.label], so no user-facing text lives here.
 */
internal enum class DevQuickLoginProfile(val role: Role, val username: String, val password: String) {
    CASHIER(Role.CASHIER, "cajero", "cajero123"),
    BAKER(Role.BAKER, "panadero", "panadero123"),
    BRANCH_MANAGER(Role.BRANCH_MANAGER, "jefelocal", "jefelocal123"),
    GENERAL_MANAGER(Role.GENERAL_MANAGER, "gerente", "gerente123"),
    ADMIN(Role.ADMIN, "admin", "admin123"),
}
