package com.f776.ecopan.data

import com.f776.ecopan.data.core.DatabaseSettings
import com.f776.ecopan.data.database.connectToDb
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Testcontainers PostgreSQL base (02 §8 -- H2 PG-mode does not faithfully emulate
 * `ON CONFLICT DO NOTHING`, a shared `nextval` sequence, or `timestamptz` semantics). Singleton
 * container, `withReuse(true)`, shared across all repository test classes in this module.
 */
@Testcontainers
abstract class RepositoryTestBase {

    companion object {
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("ecopan_test")
            .withUsername("postgres")
            .withPassword("postgres")
            .withReuse(true)

        @JvmStatic
        @BeforeAll
        fun startAndConnect() {
            postgres.start()
            runBlocking { connectToDb(settings(), init = true) }
        }

        /** The container's connection settings, for tests that boot the schema again (`connectToDb`). */
        @JvmStatic
        fun settings() = DatabaseSettings(
            host = postgres.host,
            port = postgres.firstMappedPort,
            name = postgres.databaseName,
            username = postgres.username,
            password = postgres.password,
            driver = "org.postgresql.Driver"
        )

        /** Domain tables truncated between tests; example_job/batch legacy tables left alone. */
        private val TRUNCATE_TABLES = listOf(
            "stock_movements",
            "product_stock_movements",
            "production_batches",
            "sale_lines",
            "sales",
            "formula_lines",
            "formulas",
            "ingredients",
            "products",
            "refresh_tokens",
            "users",
            "branches",
            "tenants",
            "forecasts",
            "product_types",
            "scheduled_runs",
            "outbox_messages"
        )

        @JvmStatic
        fun truncateAll() {
            transaction {
                exec("TRUNCATE TABLE ${TRUNCATE_TABLES.joinToString(", ")} RESTART IDENTITY CASCADE")
            }
        }
    }
}
