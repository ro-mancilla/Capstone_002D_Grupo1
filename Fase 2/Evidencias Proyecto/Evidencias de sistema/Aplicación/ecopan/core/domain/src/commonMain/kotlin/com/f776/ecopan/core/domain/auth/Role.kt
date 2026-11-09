package com.f776.ecopan.core.domain.auth

/**
 * Fixed, seeded role set (01 §4) — roles are not user-definable.
 * [CASHIER], [BAKER], and [BRANCH_MANAGER] are branch-scoped; [GENERAL_MANAGER] and [ADMIN]
 * are tenant-wide (branch id is null for those).
 */
enum class Role {
    CASHIER,
    BAKER,
    BRANCH_MANAGER,
    GENERAL_MANAGER,
    ADMIN,
}

val Role.isBranchScoped: Boolean
    get() = this == Role.CASHIER || this == Role.BAKER || this == Role.BRANCH_MANAGER
