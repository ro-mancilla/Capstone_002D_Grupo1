package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

internal const val TENANT_ID_INDEX = "uq_product_types_tenant_id"

/**
 * A tenant's product types: exactly the three `ProductKind`s, created with the tenant. A lookup
 * table (not an enum) because forecasts and products reference it by foreign key. [key] is the
 * normalised `ProductTypeKey.of(name)`, unique per tenant; [name] is the display spelling.
 *
 * `products.product_type_id` and `forecasts.product_type_id` both reference `(tenant_id, id)` (composite
 * FKs, so a row cannot use another tenant's type). The `product_type` varchar on each stays as the
 * canonical-name snapshot, so the sync wire (`productType` string)
 * the KMP clients carry is unchanged.
 */
object ProductTypeTable : Table("product_types") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 48)
    val key = varchar("key", 96)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_product_types_tenant_key", tenantId, key)
        // Redundant as a key (`id` is already unique) but it is what a composite FK from
        // `products` must target to pin the type to the product's own tenant.
        uniqueIndex(TENANT_ID_INDEX, tenantId, id)
    }
}
