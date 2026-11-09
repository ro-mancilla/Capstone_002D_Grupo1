package com.f776.ecopan.data.domain

/**
 * Fixed, seeded roles (01 §4). Not user-definable; permission checks are table-driven lookups
 * against [role_permissions][com.f776.ecopan.data.database.entity.RolePermissionTable], never
 * scattered `if role == ...`.
 */
enum class Role {
    CASHIER,
    BAKER,
    BRANCH_MANAGER,
    GENERAL_MANAGER,
    ADMIN
}

/**
 * Capabilities checked via `Route.requirePermission` (02 §2/§3). ADMIN implicitly has all of
 * these without an explicit `role_permissions` row (01 §4 "superuser for seeding/demo").
 */
enum class Permission {
    SALES_RECORD,
    BATCH_RECORD,
    STOCK_ADJUST,
    STOCK_READ,
    CATALOG_READ,
    CATALOG_WRITE,
    ANALYTICS_READ,
    FORECAST_READ,
    USERS_MANAGE,
    MODULES_TOGGLE,
    SYNC
}

/**
 * Tenant entitlement toggles (01 §7). `POS_SALES` is always-on and never gated by
 * `Route.requireModule` -- it is not stored as a "disable-able" module, just documented as the
 * baseline capability every tenant has.
 */
enum class Module {
    POS_SALES,
    INVENTORY_FORMULAS,
    DEMAND_FORECASTING,
    MULTI_BRANCH_ROLES,
    ANALYTICS_DASHBOARD
}

/**
 * Wire-level entity type tags for the sync push/pull envelopes (02 §4). `TENANT` is deliberately
 * absent: [com.f776.ecopan.data.database.entity.TenantTable] has no `server_seq` column (02 §1's
 * own table spec), so it cannot ride the cursor-based pull merge -- clients get the tenant profile
 * from `GET /tenant` (T1) instead (documented deviation from the plan's literal §4.2 entity list).
 */
enum class SyncEntityType {
    BRANCH,
    USER,
    PRODUCT,
    INGREDIENT,
    FORMULA,
    SALE,
    PRODUCTION_BATCH,
    STOCK_MOVEMENT,

    /** Branch-scoped d+1.. forecast rows for the baker's *producción sugerida*; DEMAND_FORECASTING only. */
    FORECAST
}
