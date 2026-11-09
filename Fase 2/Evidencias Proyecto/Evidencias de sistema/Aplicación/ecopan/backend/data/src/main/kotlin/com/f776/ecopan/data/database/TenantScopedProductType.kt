package com.f776.ecopan.data.database

import com.f776.ecopan.data.database.entity.RECEIVED_AT_INDEX
import com.f776.ecopan.data.database.entity.TENANT_ID_INDEX
import com.f776.ecopan.data.database.entity.TENANT_PRODUCT_TYPE_FK
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction

private const val OLD_PRODUCT_TYPE_FK = "fk_products_product_type_id__id"

/**
 * Leading part of `pg_get_constraintdef` of the composite FK, whitespace-free, for a shape comparison
 * (the ON UPDATE/DELETE actions follow it; they are added below to match what Exposed declares, so
 * its `create` leaves the constraint alone).
 */
private const val COMPOSITE_FK_SHAPE = "FOREIGNKEY(tenant_id,product_type_id)REFERENCESproduct_types(tenant_id,id)"

/**
 * Indexes that `SchemaUtils.create` makes on a fresh database but never adds to a table that
 * already exists. `IF NOT EXISTS`, so a repeat boot takes no lock.
 */
internal fun JdbcTransaction.addMissingIndexes() {
    val salesExists = exec("SELECT to_regclass('sales') IS NOT NULL") { rs -> rs.next() && rs.getBoolean(1) } == true
    if (salesExists) {
        exec("CREATE INDEX IF NOT EXISTS $RECEIVED_AT_INDEX ON sales (tenant_id, branch_id, received_at)")
    }
}

/**
 * Makes `products (tenant_id, product_type_id)` a composite FK to `product_types (tenant_id, id)`
 * on a database booted before it existed (a fresh one gets it from the table declaration; one that
 * already has it is found in place here). Under a transaction-scoped advisory lock so replicas booting
 * together serialise. The drop of the old single-column FK, the pre-flight and the add only run when
 * the composite FK is missing or of the wrong shape, so a steady-state boot takes no table lock.
 * A product already pointing at another tenant's type fails the boot with a readable message
 * instead of a bare constraint violation.
 */
internal fun JdbcTransaction.upgradeProductTypeForeignKey() {
    val bothTablesExist = exec("SELECT to_regclass('products') IS NOT NULL AND to_regclass('product_types') IS NOT NULL") { rs ->
        rs.next() && rs.getBoolean(1)
    } == true
    if (!bothTablesExist) return // a fresh database: the table declarations create everything

    exec("SELECT pg_advisory_xact_lock(hashtext('$TENANT_PRODUCT_TYPE_FK'))")
    exec("CREATE UNIQUE INDEX IF NOT EXISTS $TENANT_ID_INDEX ON product_types (tenant_id, id)")

    val existing = exec(
        "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c " +
            "WHERE c.conname = '$TENANT_PRODUCT_TYPE_FK' AND c.conrelid = 'products'::regclass"
    ) { rs -> if (rs.next()) rs.getString(1) else null }
    if (existing != null) {
        if (existing.filterNot(Char::isWhitespace).startsWith(COMPOSITE_FK_SHAPE)) return
        exec("ALTER TABLE products DROP CONSTRAINT $TENANT_PRODUCT_TYPE_FK")
    }

    val mismatched = exec(
        "SELECT count(*) FROM products p JOIN product_types t ON t.id = p.product_type_id " +
            "WHERE t.tenant_id <> p.tenant_id"
    ) { rs -> rs.next(); rs.getLong(1) } ?: 0L
    check(mismatched == 0L) {
        "Cannot add $TENANT_PRODUCT_TYPE_FK: $mismatched product row(s) reference a product type of " +
            "another tenant. Fix products.product_type_id (or reseed the demo data) and boot again."
    }
    exec("ALTER TABLE products DROP CONSTRAINT IF EXISTS $OLD_PRODUCT_TYPE_FK")
    exec(
        "ALTER TABLE products ADD CONSTRAINT $TENANT_PRODUCT_TYPE_FK " +
            "FOREIGN KEY (tenant_id, product_type_id) REFERENCES product_types (tenant_id, id) ON UPDATE RESTRICT ON DELETE RESTRICT"
    )
}
