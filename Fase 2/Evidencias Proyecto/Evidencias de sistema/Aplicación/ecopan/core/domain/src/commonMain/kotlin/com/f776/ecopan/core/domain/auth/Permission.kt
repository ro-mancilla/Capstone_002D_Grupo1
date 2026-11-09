package com.f776.ecopan.core.domain.auth

/** Mirrors the backend's `role_permissions` capability set (02 §2/§3) for client-side UI gating. */
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
    SYNC,
}
