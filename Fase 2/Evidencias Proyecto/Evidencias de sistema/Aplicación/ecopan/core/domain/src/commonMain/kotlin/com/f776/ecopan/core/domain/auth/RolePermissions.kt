package com.f776.ecopan.core.domain.auth

/**
 * Table-driven role → capability lookup (01 §4: "not scattered `if role ==`"). The backend seeds
 * an equivalent `role_permissions` table independently (separate codebase, CLAUDE.md) — this is
 * the client-side mirror used for nav/UI gating, not a security boundary in itself.
 */
object RolePermissions {

    private val CASHIER_PERMISSIONS = setOf(
        Permission.SALES_RECORD,
        Permission.CATALOG_READ,
        Permission.SYNC,
    )

    private val BAKER_PERMISSIONS = setOf(
        Permission.BATCH_RECORD,
        Permission.CATALOG_READ,
        Permission.STOCK_READ,
        Permission.SYNC,
    )

    private val BRANCH_MANAGER_PERMISSIONS = CASHIER_PERMISSIONS + BAKER_PERMISSIONS + setOf(
        Permission.STOCK_ADJUST,
        Permission.ANALYTICS_READ,
    )

    private val TABLE: Map<Role, Set<Permission>> = mapOf(
        Role.CASHIER to CASHIER_PERMISSIONS,
        Role.BAKER to BAKER_PERMISSIONS,
        Role.BRANCH_MANAGER to BRANCH_MANAGER_PERMISSIONS,
        Role.GENERAL_MANAGER to Permission.entries.toSet(),
        Role.ADMIN to Permission.entries.toSet(),
    )

    fun has(role: Role, permission: Permission): Boolean =
        permission in TABLE.getValue(role)
}
