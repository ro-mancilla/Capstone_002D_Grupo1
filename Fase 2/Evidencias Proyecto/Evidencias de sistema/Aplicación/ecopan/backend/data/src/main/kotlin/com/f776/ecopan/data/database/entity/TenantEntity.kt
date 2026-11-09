package com.f776.ecopan.data.database.entity

import com.f776.ecopan.data.domain.Module
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

/**
 * Tenant profile (01 §4/§7). Entitlements live in [TenantModuleTable], one row per enabled module.
 */
object TenantTable : Table("tenants") {
    val id = uuid("id")
    val slug = varchar("slug", 64).uniqueIndex()
    val name = text("name")
    val currencyCode = varchar("currency_code", 3)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

/**
 * A tenant's enabled modules: one row per (tenant, module). `module` is the [Module] enum by name
 * with a CHECK (ClosedValueSets.kt); the primary key makes a pair unique.
 */
object TenantModuleTable : Table("tenant_modules") {
    val tenantId = uuid("tenant_id").references(TenantTable.id, onDelete = ReferenceOption.CASCADE)
    val module = enumerationByName<Module>("module", 32)

    override val primaryKey = PrimaryKey(tenantId, module)
}
