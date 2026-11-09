package com.f776.ecopan.data.database.entity

import com.f776.ecopan.data.domain.Role
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * `passwordHash` never rides the sync pull; `pinHash` does (01 §4 offline PIN unlock). `role` is
 * stored as the enum name of [com.f776.ecopan.data.domain.Role] and references `roles(name)`.
 */
object UserTable : Table("users") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id).nullable()
    val username = varchar("username", 64)
    val passwordHash = text("password_hash")
    val pinHash = text("pin_hash").nullable()
    val displayName = text("display_name")
    val role = enumerationByName<Role>("role", 24).references(RoleTable.name)
    val active = bool("active").default(true)
    val serverSeq = long("server_seq")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_users_tenant_username", tenantId, username)
        index("idx_users_tenant_seq", false, tenantId, serverSeq)
    }
}
