package com.f776.ecopan.data.database

import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.ProductTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.Uuid

/**
 * One global PostgreSQL sequence backs every synced row's `server_seq` (01 §3, 02 §1). Global
 * monotonic ⇒ monotonic per tenant, which is all the sync cursor needs. Bootstrapped in
 * [connectToDb] via `CREATE SEQUENCE IF NOT EXISTS server_seq`.
 */
internal fun JdbcTransaction.nextServerSeq(): Long =
    exec("SELECT nextval('server_seq')") { rs ->
        rs.next()
        rs.getLong(1)
    } ?: error("server_seq.nextval() returned no row")

/**
 * Gives each of [productIds] that is an Almacén product a fresh `server_seq`, so devices re-pull it
 * and pick up its recomputed stock. Product stock is derived (ledger + sales), so nothing about the
 * product row itself changes when stock moves; without this bump the till's badge would only refresh
 * when a manager happened to edit the product. One `nextval` per row keeps `server_seq` unique, which
 * the pull cursor (`> since`, paged) relies on. Ids are updated in sorted order so two concurrent
 * transactions touching overlapping products take row locks in the same order and cannot deadlock.
 */
internal fun JdbcTransaction.bumpAlmacenProductSeq(productIds: Collection<Uuid>) {
    productIds.distinct().sorted().forEach { id ->
        val seq = nextServerSeq()
        ProductTable.update({ (ProductTable.id eq id) and (ProductTable.origin eq ProductOrigin.ALMACEN) }) {
            it[serverSeq] = seq
        }
    }
}
