package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

/** Unit is always grams (01 §5) -- no unit column. */
object IngredientTable : Table("ingredients") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val name = text("name")
    val lowStockThresholdGrams = long("low_stock_threshold_grams").nullable()
    val active = bool("active").default(true)
    val serverSeq = long("server_seq")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_ingredients_tenant_seq", false, tenantId, serverSeq)
    }
}
