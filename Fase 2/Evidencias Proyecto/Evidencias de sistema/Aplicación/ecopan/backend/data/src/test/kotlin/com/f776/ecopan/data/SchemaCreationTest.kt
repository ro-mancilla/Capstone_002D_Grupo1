package com.f776.ecopan.data

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SchemaCreationTest : RepositoryTestBase() {

    @BeforeEach
    fun setUp() = truncateAll()

    private val expectedTables = setOf(
        "tenants", "branches", "users", "role_permissions", "refresh_tokens",
        "products", "ingredients", "formulas", "formula_lines",
        "sales", "sale_lines", "production_batches", "stock_movements", "product_stock_movements",
        "forecasts"
    )

    @Test
    fun `SchemaUtils create produces every domain table`() = runTest {
        val tableNames: List<String> = suspendTransaction {
            exec(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'"
            ) { rs ->
                buildList {
                    while (rs.next()) add(rs.getString(1))
                }
            } ?: emptyList()
        }

        val missing = expectedTables - tableNames.toSet()
        assertTrue(missing.isEmpty(), "Missing tables: $missing")
    }
}
