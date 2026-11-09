package com.f776.ecopan.data

import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.ForecastRepositoryImpl
import com.f776.ecopan.data.database.repository.ForecastUpsert
import com.f776.ecopan.data.database.repository.ForecastUpsertResult
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** Upsert on (tenantId, branchId, productId, forecastDate); K8/F1 read back via listByBranch. */
class ForecastRepositoryTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()
    private val forecasts = ForecastRepositoryImpl()

    @BeforeEach
    fun setUp() = truncateAll()

    private suspend fun newTenant(): Uuid = Uuid.random().also { tenants.create(it, "t-$it", "T", "CLP") }

    private suspend fun product(tenantId: Uuid, name: String, type: String = "Pan", soldBy: SoldBy = SoldBy.WEIGHT) =
        catalog.createProduct(
            Uuid.random(), tenantId, name, type, soldBy,
            if (soldBy == SoldBy.WEIGHT) 1000 else null,
            if (soldBy == SoldBy.UNIT) 300 else null,
            null
        )

    @Test
    fun `upsert on the same key updates in place rather than duplicating`() = runTest {
        val tenantId = newTenant()
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val marraqueta = product(tenantId, "Marraqueta")
        val date = LocalDate(2026, 7, 20)

        val first = forecasts.upsert(tenantId, branch.id, marraqueta.id, date, 10_000, null, "v1")
        val second = forecasts.upsert(tenantId, branch.id, marraqueta.id, date, 12_000, null, "v2")

        assertEquals(first.id, second.id, "same key must update the existing row, not insert a new one")
        assertEquals(12_000L, second.predictedGrams)
        assertEquals("v2", second.modelVersion)
        assertEquals("Marraqueta", second.productName)
        assertEquals("Pan", second.productType)
        assertEquals(1, forecasts.listByBranch(tenantId, branch.id, date, date).size)
    }

    @Test
    fun `two products of one type keep separate rows`() = runTest {
        val tenantId = newTenant()
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val marraqueta = product(tenantId, "Marraqueta")
        val hallulla = product(tenantId, "Hallulla", soldBy = SoldBy.UNIT)
        val date = LocalDate(2026, 7, 20)

        forecasts.upsert(tenantId, branch.id, marraqueta.id, date, 10_000, null, "v1")
        forecasts.upsert(tenantId, branch.id, hallulla.id, date, null, 30, "v1")

        val listed = forecasts.listByBranch(tenantId, branch.id, date, date)
        assertEquals(setOf("Marraqueta", "Hallulla"), listed.map { it.productName }.toSet())
        assertEquals(
            listOf(marraqueta.id),
            forecasts.listByBranch(tenantId, branch.id, date, date, productId = marraqueta.id).map { it.productId }
        )
    }

    @Test
    fun `listByBranch filters by date range and type`() = runTest {
        val tenantId = newTenant()
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val bread = product(tenantId, "Marraqueta", "Pan")
        val cake = product(tenantId, "Torta", "Pastelería", SoldBy.UNIT)

        forecasts.upsert(tenantId, branch.id, bread.id, LocalDate(2026, 7, 20), 10_000, null, "v1")
        forecasts.upsert(tenantId, branch.id, cake.id, LocalDate(2026, 7, 20), null, 30, "v1")
        forecasts.upsert(tenantId, branch.id, bread.id, LocalDate(2026, 8, 1), 11_000, null, "v1")

        assertEquals(2, forecasts.listByBranch(tenantId, branch.id, LocalDate(2026, 7, 1), LocalDate(2026, 7, 31)).size)

        val breadOnly = forecasts.listByBranch(tenantId, branch.id, LocalDate(2026, 7, 1), LocalDate(2026, 7, 31), productType = "PAN")
        assertEquals(10_000L, breadOnly.single().predictedGrams)
    }

    @Test
    fun `a forecast cannot reference another tenant's product type`() = runTest {
        val a = newTenant()
        val b = newTenant()
        val branch = branches.create(Uuid.random(), a, "Main")
        val bread = product(a, "Marraqueta")
        forecasts.upsert(a, branch.id, bread.id, LocalDate(2026, 7, 20), 1, null, "v1")
        val othersType = transaction {
            exec("SELECT id FROM product_types WHERE tenant_id = '$b' AND name = 'Pan'") { rs -> rs.next(); rs.getString(1) }
        }
        assertFailsWith<Exception> {
            transaction { exec("UPDATE forecasts SET product_type_id = '$othersType' WHERE tenant_id = '$a'") }
        }
    }

    @Test
    fun `an unknown product is skipped and does not fail the rest of the batch`() = runTest {
        val tenantId = newTenant()
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val bread = product(tenantId, "Marraqueta")
        val day = LocalDate(2026, 7, 20)

        val result = forecasts.upsertAll(
            tenantId, branch.id,
            listOf(
                ForecastUpsert(Uuid.random(), day, 1, null, "v1"),
                ForecastUpsert(bread.id, day, 2, null, "v1"),
            ),
            notBefore = day
        )

        assertEquals(ForecastUpsertResult(written = 1, skippedPast = 0, skippedUnknownProduct = 1), result)
        assertEquals(listOf(bread.id), forecasts.listByBranch(tenantId, branch.id, day, day).map { it.productId })
    }

    @Test
    fun `days before notBefore are never written or rewritten`() = runTest {
        val tenantId = newTenant()
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val bread = product(tenantId, "Marraqueta")
        val today = LocalDate(2026, 7, 20)
        val yesterday = LocalDate(2026, 7, 19)
        forecasts.upsert(tenantId, branch.id, bread.id, yesterday, 10_000, null, "v1") // made back then

        val result = forecasts.upsertAll(
            tenantId, branch.id,
            listOf(ForecastUpsert(bread.id, yesterday, 99_999, null, "replayed"), ForecastUpsert(bread.id, today, 5, null, "v2")),
            notBefore = today
        )

        assertEquals(1, result.written)
        assertEquals(1, result.skippedPast)
        val past = forecasts.listByBranch(tenantId, branch.id, yesterday, yesterday).single()
        assertEquals(10_000L, past.predictedGrams)
        assertEquals("v1", past.modelVersion)
    }

    @Test
    fun `a batch gets increasing server_seq in one transaction`() = runTest {
        val tenantId = newTenant()
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val bread = product(tenantId, "Marraqueta")
        val day = LocalDate(2026, 7, 20)

        forecasts.upsertAll(
            tenantId, branch.id,
            (0 until 5).map { ForecastUpsert(bread.id, day.plus(it, DateTimeUnit.DAY), it.toLong(), null, "v1") },
            notBefore = day
        )

        val seqs = forecasts.listByBranch(tenantId, branch.id, day, day.plus(10, DateTimeUnit.DAY))
            .sortedBy { it.forecastDate }.map { it.serverSeq }
        assertEquals(seqs.sorted(), seqs)
        assertEquals(5, seqs.toSet().size)
    }

    @Test
    fun `the type filter and the reported type follow the product's current type`() = runTest {
        val tenantId = newTenant()
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val item = product(tenantId, "Empanada", "Pan", SoldBy.UNIT)
        val day = LocalDate(2026, 7, 20)
        forecasts.upsert(tenantId, branch.id, item.id, day, null, 12, "v1")

        catalog.updateProduct(item.id, productType = "Pastelería")

        assertEquals(emptyList(), forecasts.listByBranch(tenantId, branch.id, day, day, productType = "Pan"))
        val moved = forecasts.listByBranch(tenantId, branch.id, day, day, productType = "pastelería").single()
        assertEquals("Pastelería", moved.productType)
    }
}
