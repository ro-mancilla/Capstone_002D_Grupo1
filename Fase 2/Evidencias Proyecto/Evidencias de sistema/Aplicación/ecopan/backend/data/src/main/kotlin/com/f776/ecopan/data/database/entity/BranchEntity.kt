package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

/** Config row, LWW by [serverSeq] (01 §6.2). */
object BranchTable : Table("branches") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val name = text("name")
    val active = bool("active").default(true)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val serverSeq = long("server_seq")

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_branches_tenant_seq", false, tenantId, serverSeq)
    }
}
