package com.f776.ecopan.domain.tenant

/**
 * The real device-enrollment login call (01 §4 / 03 §8 — replaces the fake local seeding
 * `:feature:auth`'s `AuthViewModel.enroll()` used to do). Kept separate from [TenantRepository]
 * (pure local-cache reads/upserts) because this one call: hits the network, persists device
 * session tokens, *and* feeds the same tenant/branch/user cache — mixing that orchestration into
 * the read-mostly cache repository would make both harder to test in isolation.
 */
interface AuthRepository {
    /**
     * On [LoginOutcome.Success]: device session tokens are already persisted and
     * [TenantRepository]'s tenant/branches/users caches already reflect the response — callers
     * don't need to do any further local-cache writes themselves, only trigger a full sync
     * bootstrap pull for the rest of the tenant's data (catalog, formulas, other staff, etc).
     */
    suspend fun login(tenantSlug: String, username: String, password: String, deviceName: String): LoginOutcome
}
