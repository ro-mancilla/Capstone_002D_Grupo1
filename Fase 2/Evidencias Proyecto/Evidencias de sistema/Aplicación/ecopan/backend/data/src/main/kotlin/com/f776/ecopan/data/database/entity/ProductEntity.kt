package com.f776.ecopan.data.database.entity

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.datetime.CurrentTimestamp
import org.jetbrains.exposed.v1.datetime.timestamp

internal const val TENANT_PRODUCT_TYPE_FK = "fk_products_tenant_product_type"

/** `soldBy` is the enum name of `SoldBy` (`WEIGHT` | `UNIT`); exactly one price column is set. */
object ProductTable : Table("products") {
    val id = uuid("id")
    val tenantId = uuid("tenant_id").references(TenantTable.id)
    val name = text("name")
    // Canonical display name of the product's type -- a snapshot kept equal to `product_types.name`
    // so the sync wire (a string the KMP clients store) is unchanged. `productTypeId` is the
    // reference of record.
    val productType = varchar("product_type", 48)
    val productTypeId = uuid("product_type_id")
    val soldBy = enumerationByName<SoldBy>("sold_by", 8)
    // Enum name of `ProductOrigin`. Closed by a CHECK constraint (ClosedValueSets).
    val origin = enumerationByName<ProductOrigin>("origin", 16)
    val pricePerKgMinor = long("price_per_kg_minor").nullable()
    val priceMinor = long("price_minor").nullable()
    val barcode = varchar("barcode", 64).nullable()
    // Absolute http(s) URL of a product photo, served by whoever hosts it. Deliberately a URL
    // and not a blob: the clients are offline-first for *ledger* data, and a picture is not
    // ledger data -- a tile without its photo is a tile, a sale without its price is not a sale.
    val imageUrl = text("image_url").nullable()
    // Reorder level for a product stocked by the unit (HU-ANA stock alerts); null = no alert.
    // Web-only: it never travels on the sync wire, the clients have no use for it.
    val lowStockThresholdUnits = long("low_stock_threshold_units").nullable()
    val active = bool("active").default(true)
    val serverSeq = long("server_seq")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)

    init {
        // Composite, not `references(id)`: the type must belong to this product's own tenant.
        foreignKey(
            tenantId to ProductTypeTable.tenantId,
            productTypeId to ProductTypeTable.id,
            name = TENANT_PRODUCT_TYPE_FK
        )
        index("idx_products_tenant_seq", false, tenantId, serverSeq)
        index("idx_products_tenant_active", false, tenantId, active)
        // Partial unique index: barcode unique per tenant only where present.
        uniqueIndex("uq_products_tenant_barcode", tenantId, barcode) { barcode.isNotNull() }
    }
}

enum class SoldBy { WEIGHT, UNIT }

/**
 * Where a product comes from, independent of how it is priced ([SoldBy]). PANADERIA is baked by
 * the bakery (formula, batches, sold by weight or unit); ALMACEN is bought finished and resold
 * (no formula, unit-only, tracked as product stock).
 */
enum class ProductOrigin { PANADERIA, ALMACEN }
