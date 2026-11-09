package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

/** One active formula per product; lines are replaced wholesale (delete+insert) on PUT. */
object FormulaTable : Table("formulas") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val productId = uuid("product_id").references(ProductTable.id)
    val active = bool("active").default(true)
    val serverSeq = long("server_seq")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_formulas_tenant_product", tenantId, productId)
    }
}

/** `basisPoints`: flour line = 10000 (01 §5). No own `server_seq` -- rides the parent formula's. */
object FormulaLineTable : Table("formula_lines") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val formulaId = uuid("formula_id").references(FormulaTable.id)
    val ingredientId = uuid("ingredient_id").references(IngredientTable.id)
    val basisPoints = integer("basis_points")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_formula_lines_formula_ingredient", formulaId, ingredientId)
    }
}
