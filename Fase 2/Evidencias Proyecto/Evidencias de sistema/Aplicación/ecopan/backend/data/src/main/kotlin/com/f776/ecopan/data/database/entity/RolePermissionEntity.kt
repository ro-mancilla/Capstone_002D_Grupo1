package com.f776.ecopan.data.database.entity

import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import org.jetbrains.exposed.v1.core.Table

/**
 * The fixed role set (01 §4), seeded from [Role] at boot. Users and [RolePermissionTable] reference
 * it by foreign key, so a typo cannot create a ghost role. Adding a role is a deliberate change:
 * a new [Role] entry, which the next boot seeds.
 */
object RoleTable : Table("roles") {
    val name = enumerationByName<Role>("name", 24)

    override val primaryKey = PrimaryKey(name)
}

/**
 * Global (not per-tenant) role -> capability map (01 §4). Seeded idempotently in
 * `connectToDb(init = true)`; not user-editable. ADMIN implicitly passes every
 * `Route.requirePermission` check without needing rows here (02 §2). `role` references
 * [RoleTable] by foreign key; `permission` is the [Permission] enum by name, with a CHECK (ClosedValueSets.kt).
 */
object RolePermissionTable : Table("role_permissions") {
    val role = enumerationByName<Role>("role", 24).references(RoleTable.name)
    val permission = enumerationByName<Permission>("permission", 48)

    override val primaryKey = PrimaryKey(role, permission)
}
