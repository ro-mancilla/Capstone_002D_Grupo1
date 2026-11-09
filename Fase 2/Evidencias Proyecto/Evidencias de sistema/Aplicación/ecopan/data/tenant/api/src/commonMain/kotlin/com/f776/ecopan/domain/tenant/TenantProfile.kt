package com.f776.ecopan.domain.tenant

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import kotlin.uuid.Uuid

/** The single tenant this device is enrolled to (02 §1 `tenants`). */
data class TenantProfile(
    val id: Uuid,
    val slug: String,
    val name: String,
    val currencyCode: String,
    val enabledModules: Set<EntitlementModule>,
)
