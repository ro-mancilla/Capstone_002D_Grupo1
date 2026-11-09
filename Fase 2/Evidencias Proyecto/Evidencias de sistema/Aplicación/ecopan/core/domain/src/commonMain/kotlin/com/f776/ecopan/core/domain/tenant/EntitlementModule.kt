package com.f776.ecopan.core.domain.tenant

/** Tenant entitlement units (01 §7 / spec §8). [POS_SALES] is always on, never toggled. */
enum class EntitlementModule {
    POS_SALES,
    INVENTORY_FORMULAS,
    DEMAND_FORECASTING,
    MULTI_BRANCH_ROLES,
    ANALYTICS_DASHBOARD,
}
