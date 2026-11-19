package com.f776.ecopan.data.database

import com.f776.ecopan.data.database.entity.DecrementStatus
import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.ProductStockMovementKind
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.entity.StockMovementKind
import com.f776.ecopan.data.domain.Module
import com.f776.ecopan.data.domain.Permission
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/** A varchar column whose values are a closed set owned by a Kotlin enum (mapped by name). */
internal class ClosedValueSet(val table: String, val column: String, val allowed: List<String>) {
    val constraint = "ck_${table}_$column"
}

private inline fun <reified E : Enum<E>> closed(table: String, column: String) =
    ClosedValueSet(table, column, enumValues<E>().map { it.name })

/** Derived from the enums, so a new enum entry widens the constraint on the next boot's re-creation. */
internal val CLOSED_VALUE_SETS = listOf(
    closed<PaymentMethod>("sales", "payment_method"),
    closed<SoldBy>("products", "sold_by"),
    closed<SoldBy>("sale_lines", "sold_by"),
    closed<StockMovementKind>("stock_movements", "kind"),
    closed<ProductStockMovementKind>("product_stock_movements", "kind"),
    closed<DecrementStatus>("production_batches", "decrement_status"),
    closed<ProductOrigin>("products", "origin"),
    closed<Permission>("role_permissions", "permission"),
    closed<Module>("tenant_modules", "module")
)

/**
 * Idempotently adds one CHECK constraint per closed value set. Strict: a row outside a set fails
 * boot loudly. If the set changed since the constraint was created (a new enum entry), the
 * constraint is recreated. Each set is handled in one transaction under a transaction-scoped
 * advisory lock, so replicas booting together serialise instead of racing the drop and add.
 */
internal suspend fun applyClosedValueSetConstraints() {
    CLOSED_VALUE_SETS.forEach { applyClosedValueSet(it) }
}

internal suspend fun applyClosedValueSet(set: ClosedValueSet) {
    suspendTransaction { apply(set) }
}

/** A quoted enum constant as Postgres prints it: any legal Kotlin identifier char, digits included. */
internal val CONSTANT_LITERAL = Regex("'([A-Za-z0-9_]+)'")

private fun JdbcTransaction.apply(set: ClosedValueSet) {
    exec("SELECT pg_advisory_xact_lock(hashtext('${set.constraint}'))")
    val list = set.allowed.joinToString(", ") { "'$it'" }
    val existing = exec(
        "SELECT pg_get_constraintdef(c.oid) FROM pg_constraint c WHERE c.conname = '${set.constraint}' " +
            "AND c.conrelid = '${set.table}'::regclass"
    ) { rs -> if (rs.next()) rs.getString(1) else null }
    if (existing != null) {
        // Postgres normalises the stored expression (ANY (ARRAY[...])), so compare by member set.
        val stored = CONSTANT_LITERAL.findAll(existing).map { it.groupValues[1] }.toSet()
        if (stored == set.allowed.toSet()) return
        exec("ALTER TABLE ${set.table} DROP CONSTRAINT IF EXISTS ${set.constraint}")
    }
    exec("ALTER TABLE ${set.table} ADD CONSTRAINT ${set.constraint} CHECK (${set.column} IN ($list))")
}
