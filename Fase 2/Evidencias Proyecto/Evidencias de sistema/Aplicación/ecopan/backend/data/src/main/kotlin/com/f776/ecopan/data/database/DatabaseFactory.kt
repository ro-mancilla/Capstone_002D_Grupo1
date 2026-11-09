package com.f776.ecopan.data.database

import com.f776.ecopan.data.core.DatabaseSettings
import com.f776.ecopan.data.database.entity.BranchTable
import com.f776.ecopan.data.database.entity.ForecastTable
import com.f776.ecopan.data.database.entity.FormulaLineTable
import com.f776.ecopan.data.database.entity.FormulaTable
import com.f776.ecopan.data.database.entity.IngredientTable
import com.f776.ecopan.data.database.entity.ProductStockMovementTable
import com.f776.ecopan.data.database.entity.ProductTable
import com.f776.ecopan.data.database.entity.ProductTypeTable
import com.f776.ecopan.data.database.entity.ProductionBatchTable
import com.f776.ecopan.data.database.entity.RefreshTokenTable
import com.f776.ecopan.data.database.entity.RolePermissionTable
import com.f776.ecopan.data.database.entity.RoleTable
import com.f776.ecopan.data.database.entity.SaleLineTable
import com.f776.ecopan.data.database.entity.SaleTable
import com.f776.ecopan.data.database.entity.OutboxTable
import com.f776.ecopan.data.database.entity.ScheduledRunTable
import com.f776.ecopan.data.database.entity.SeedRevisionTable
import com.f776.ecopan.data.database.entity.StockMovementTable
import com.f776.ecopan.data.database.entity.TenantModuleTable
import com.f776.ecopan.data.database.entity.TenantTable
import com.f776.ecopan.data.database.entity.UserTable
import com.f776.ecopan.data.domain.Permission
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.delay
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.slf4j.LoggerFactory
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction

/** All domain tables (02 §1); every synced one consumes `nextval('server_seq')` on write. */
private val DOMAIN_TABLES = arrayOf(
    TenantTable,
    BranchTable,
    RoleTable,
    UserTable,
    RolePermissionTable,
    RefreshTokenTable,
    ProductTable,
    IngredientTable,
    FormulaTable,
    FormulaLineTable,
    SaleTable,
    SaleLineTable,
    ProductionBatchTable,
    StockMovementTable,
    ForecastTable
)

/**
 * Tables that are not part of the synced domain: no `server_seq`, never pulled by a client. Kept
 * out of [DOMAIN_TABLES] so that array keeps meaning exactly "everything a device can sync".
 */
private val INFRA_TABLES = arrayOf(SeedRevisionTable, ScheduledRunTable, OutboxTable)

/**
 * Domain data that no device syncs: the resold-product unit ledger is written and read by the web
 * dashboard only, so it has no `server_seq` and stays out of [DOMAIN_TABLES]' "everything a
 * device can sync" meaning.
 */
private val UNSYNCED_DOMAIN_TABLES = arrayOf(ProductStockMovementTable, TenantModuleTable, ProductTypeTable)

/** Fixed, table-driven role -> permission grid (01 §4). ADMIN is intentionally absent: it
 * implicitly passes every `requirePermission` check (02 §2), no explicit rows required. */
private val ROLE_PERMISSIONS: List<Pair<Role, Permission>> = listOf(
    Role.CASHIER to Permission.SALES_RECORD,
    Role.CASHIER to Permission.SYNC,
    Role.BAKER to Permission.BATCH_RECORD,
    Role.BAKER to Permission.SYNC,
    Role.BRANCH_MANAGER to Permission.STOCK_ADJUST,
    Role.BRANCH_MANAGER to Permission.STOCK_READ,
    Role.BRANCH_MANAGER to Permission.CATALOG_READ,
    Role.BRANCH_MANAGER to Permission.ANALYTICS_READ,
    Role.BRANCH_MANAGER to Permission.FORECAST_READ,
    Role.BRANCH_MANAGER to Permission.SYNC,
    Role.GENERAL_MANAGER to Permission.STOCK_ADJUST,
    Role.GENERAL_MANAGER to Permission.STOCK_READ,
    Role.GENERAL_MANAGER to Permission.CATALOG_READ,
    Role.GENERAL_MANAGER to Permission.CATALOG_WRITE,
    Role.GENERAL_MANAGER to Permission.ANALYTICS_READ,
    Role.GENERAL_MANAGER to Permission.FORECAST_READ,
    Role.GENERAL_MANAGER to Permission.USERS_MANAGE,
    Role.GENERAL_MANAGER to Permission.MODULES_TOGGLE,
    Role.GENERAL_MANAGER to Permission.SYNC
)

suspend fun connectToDb(databaseSettings: DatabaseSettings, init: Boolean = false) {
    Database.connect(databaseSettings.url, driver = databaseSettings.driver)

    if (init) {
        suspendTransaction {
            // Replicas booting together serialise on this, so the drop/create/alter steps below
            // never interleave (same idea as the closed-value-set constraints).
            exec("SELECT pg_advisory_xact_lock(hashtext('ecopan_schema_boot'))")
            exec("CREATE SEQUENCE IF NOT EXISTS server_seq")
            resetStaleForecastCache()
            // Before `create`: on an old database Exposed would otherwise try the composite FKs
            // before the unique index they target exists.
            upgradeProductTypeForeignKey()
            addMissingIndexes()
            SchemaUtils.create(*DOMAIN_TABLES, *UNSYNCED_DOMAIN_TABLES, *INFRA_TABLES)
            // `create` only creates missing *tables*. A column added to a table that already
            // exists -- `products.image_url`, say -- would never reach a database that has been
            // booted before, and the failure is a runtime "column does not exist" on the first
            // query rather than anything at startup. Additive DDL only; Exposed does not drop.
            (DOMAIN_TABLES + UNSYNCED_DOMAIN_TABLES + INFRA_TABLES).forEach { table ->
                SchemaUtils.addMissingColumnsStatements(table).forEach { exec(it) }
            }
            RETIRED_SCHEMA.forEach { exec(it) }
            // `create` skips a table that already exists, so an index added later never reaches
            // a database booted before it. `IF NOT EXISTS`: a no-op on a fresh one and on repeat boots.
            ADDED_INDEXES.forEach { exec(it) }
            seedRoles()
            seedRolePermissions()
        }
        applyClosedValueSetConstraints()
    }
}

/** Indexes declared on a table after it first shipped; keep in step with the table's `init` block. */
private val ADDED_INDEXES = listOf(
    "CREATE INDEX IF NOT EXISTS idx_stock_movements_tenant_branch_occurred " +
        "ON stock_movements (tenant_id, branch_id, occurred_at)",
    "CREATE INDEX IF NOT EXISTS idx_refresh_tokens_family ON refresh_tokens (family_id)",
)

/**
 * Creates `scheduled_runs` if absent. The app owns schema init ([connectToDb] with `init`), but the
 * worker's scheduler tick needs this one table and can boot first; `CREATE TABLE IF NOT EXISTS`
 * is safe to run from both. Concurrent creates can still collide inside Postgres' catalog, so a
 * few retries; a persistent failure is real and propagates.
 */
suspend fun ensureScheduledRuns(attempts: Int = 5) = createIfAbsent(ScheduledRunTable, attempts)

/** Same as [ensureScheduledRuns], for the outbox the worker's relay reads (#136). */
suspend fun ensureOutbox(attempts: Int = 5) = createIfAbsent(OutboxTable, attempts)

private suspend fun createIfAbsent(table: org.jetbrains.exposed.v1.core.Table, attempts: Int) {
    // `create` on a table that already exists still issues its plain CREATE INDEX statements, which
    // then fail every time: an outbox the app created first would crash-loop the worker. And
    // `exists()` is cached per connection, so it cannot settle a race with the app's boot either.
    // Make every statement idempotent instead; the retries cover catalog collisions.
    val create = suspend {
        suspendTransaction {
            SchemaUtils.createStatements(table)
                .map { it.replaceFirst(Regex("^CREATE (UNIQUE )?INDEX (?!IF NOT EXISTS)"), "CREATE $1INDEX IF NOT EXISTS ") }
                .forEach { exec(it) }
        }
    }
    repeat(attempts - 1) {
        try {
            create()
            return
        } catch (e: Exception) {
            delay(500)
        }
    }
    create()
}

/**
 * Schema the code no longer has, dropped from databases that were booted before it went away.
 * Exposed only ever *adds* here (see above), so without this a pre-existing database would keep
 * the old NOT NULL `sale_lines.is_day_old` column (no default) and reject every sale insert. All
 * `IF EXISTS`, so a fresh database and a repeat boot are both no-ops.
 */
private val RETIRED_SCHEMA = listOf(
    "DROP TABLE IF EXISTS day_old_policies",
    "ALTER TABLE sale_lines DROP COLUMN IF EXISTS is_day_old",
    "ALTER TABLE sale_lines DROP COLUMN IF EXISTS day_old_discount_bp"
)

/**
 * `forecasts` is a regenerable ML cache whose series moved from product type to product (#51). A
 * table from before that has no `product_id` / `server_seq` and could not gain a NOT NULL one over
 * existing rows, so it is dropped (and recreated by `create`); the next F2 refresh refills it. A
 * no-op once the columns exist.
 */
private fun JdbcTransaction.resetStaleForecastCache() {
    val stale = exec(
        """
        SELECT EXISTS (SELECT 1 FROM information_schema.tables
                       WHERE table_schema = current_schema() AND table_name = 'forecasts')
           AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                           WHERE table_schema = current_schema() AND table_name = 'forecasts'
                             AND column_name = 'server_seq')
        """.trimIndent()
    ) { rs -> rs.next() && rs.getBoolean(1) } ?: false
    if (stale) {
        LoggerFactory.getLogger("DatabaseFactory")
            .warn("Dropping the pre-#51 `forecasts` cache (per product type); the next forecast refresh refills it")
        exec("DROP TABLE forecasts")
    }
}

/** Idempotent seed of the fixed role lookup from [Role]; must run before any row can reference it. */
private fun seedRoles() {
    Role.entries.forEach { role -> RoleTable.insertIgnore { it[RoleTable.name] = role } }
}

/** Idempotent upsert of the role/permission grid -- safe to re-run on every boot. */
private fun seedRolePermissions() {
    ROLE_PERMISSIONS.forEach { (role, permission) ->
        RolePermissionTable.insertIgnore {
            it[RolePermissionTable.role] = role
            it[RolePermissionTable.permission] = permission
        }
    }
}
