package com.f776.ecopan.data

import com.f776.ecopan.data.core.DatabaseSettings
import com.f776.ecopan.data.database.connectToDb
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Boots the app's schema step against a database shaped like one from before #51/#42: `forecasts`
 * per product type with a single-column type FK, and no `(tenant_id, id)` unique index on
 * `product_types`. Recreating `forecasts` with its composite FK before that index exists used to
 * fail boot outright.
 */
class LegacySchemaBootTest : RepositoryTestBase() {

    private val dbName = "legacy_${Uuid.random().toString().replace("-", "")}"

    private fun jdbc(db: String) = DriverManager.getConnection(
        "jdbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/$db", postgres.username, postgres.password
    )

    private fun settings(db: String) = DatabaseSettings(
        host = postgres.host, port = postgres.firstMappedPort, name = db,
        username = postgres.username, password = postgres.password, driver = "org.postgresql.Driver"
    )

    @AfterEach
    fun backToSharedDatabase() {
        // Other test classes in this JVM expect the default connection to be the shared database.
        Database.connect(
            "jdbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/${postgres.databaseName}",
            driver = "org.postgresql.Driver", user = postgres.username, password = postgres.password
        )
    }

    private fun scalar(db: String, sql: String): String? =
        jdbc(db).use { c -> c.createStatement().executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null } }

    @Test
    fun `an old-shape database boots and forecasts is recreated after the product_types unique index exists`() {
        jdbc(postgres.databaseName).use { it.createStatement().execute("CREATE DATABASE $dbName") }
        runBlocking { connectToDb(settings(dbName), init = true) }

        // Degrade to the old shape.
        jdbc(dbName).use { c ->
            c.createStatement().execute(
                """
                DROP TABLE forecasts;
                ALTER TABLE products DROP CONSTRAINT fk_products_tenant_product_type;
                ALTER TABLE product_types DROP CONSTRAINT uq_product_types_tenant_id;
                CREATE TABLE forecasts (
                    id uuid PRIMARY KEY,
                    tenant_id uuid NOT NULL REFERENCES tenants(id),
                    branch_id uuid NOT NULL REFERENCES branches(id),
                    product_type varchar(48) NOT NULL,
                    product_type_id uuid NOT NULL REFERENCES product_types(id),
                    forecast_date date NOT NULL,
                    predicted_grams bigint,
                    predicted_units integer,
                    model_version varchar(64) NOT NULL,
                    generated_at timestamptz NOT NULL
                );
                """.trimIndent()
            )
        }
        assertEquals(null, scalar(dbName, "SELECT to_regclass('uq_product_types_tenant_id')::text"))

        runBlocking { connectToDb(settings(dbName), init = true) }

        assertEquals(
            "2",
            scalar(
                dbName,
                "SELECT count(*) FROM information_schema.columns " +
                    "WHERE table_name = 'forecasts' AND column_name IN ('product_id', 'server_seq')"
            )
        )
        assertTrue(scalar(dbName, "SELECT to_regclass('uq_product_types_tenant_id')::text") != null)
        assertEquals(
            "1",
            scalar(dbName, "SELECT count(*) FROM pg_constraint WHERE conname = 'fk_forecasts_tenant_product_type'")
        )
        assertEquals(
            "1",
            scalar(dbName, "SELECT count(*) FROM pg_constraint WHERE conname = 'fk_products_tenant_product_type'")
        )

        // And a third boot is a no-op.
        runBlocking { connectToDb(settings(dbName), init = true) }
    }
}
