package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.entity.ProductTypeTable
import com.f776.ecopan.data.database.repository.model.ProductType
import com.f776.ecopan.data.domain.ProductKind
import com.f776.ecopan.data.domain.ProductTypeKey
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import kotlin.uuid.Uuid

private fun ResultRow.toModel() =
    ProductType(this[ProductTypeTable.id], this[ProductTypeTable.tenantId], this[ProductTypeTable.name])

internal fun JdbcTransaction.findByKey(tenantId: Uuid, key: String): ProductType? =
    ProductTypeTable.selectAll()
        .where { (ProductTypeTable.tenantId eq tenantId) and (ProductTypeTable.key eq key) }
        .singleOrNull()?.toModel()

/**
 * Get-or-create by normalised key (the tenant's three fixed kinds are created with the tenant; this also covers a tenant that predates that), race-safe (`ON CONFLICT DO NOTHING` on the unique key, then
 * re-read). Shared by every writer of a product type so none can create a variant.
 */
internal fun JdbcTransaction.ensureProductType(tenantId: Uuid, name: String): Pair<ProductType, Boolean> {
    val clean = name.trim().replace(Regex("\\s+"), " ")
    val key = ProductTypeKey.of(clean)
    findByKey(tenantId, key)?.let { return it to false }
    val inserted = ProductTypeTable.insertIgnore {
        it[ProductTypeTable.id] = Uuid.random()
        it[ProductTypeTable.tenantId] = tenantId
        it[ProductTypeTable.name] = clean
        it[ProductTypeTable.key] = key
    }.insertedCount > 0
    return findByKey(tenantId, key)!! to inserted
}

/** Creates the tenant's fixed product types (Pan, Pastelería, Almacén); idempotent. */
internal fun JdbcTransaction.ensureDefaultProductTypes(tenantId: Uuid) {
    ProductKind.entries.forEach { ensureProductType(tenantId, it.label) }
}
