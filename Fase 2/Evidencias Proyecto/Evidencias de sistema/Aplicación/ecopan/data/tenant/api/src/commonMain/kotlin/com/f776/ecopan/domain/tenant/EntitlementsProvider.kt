package com.f776.ecopan.domain.tenant

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import kotlinx.coroutines.flow.Flow

/**
 * Client navigation's entitlement gate (01 §7 enforcement point 1). `AppNavigation` filters
 * top-level destinations through this — a disabled module means the destination is absent, not
 * merely disabled; the feature's Koin module still loads.
 */
interface EntitlementsProvider {
    fun isModuleEnabled(module: EntitlementModule): Flow<Boolean>
}
