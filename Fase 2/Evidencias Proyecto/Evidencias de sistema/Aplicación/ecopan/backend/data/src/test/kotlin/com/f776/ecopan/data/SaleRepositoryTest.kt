package com.f776.ecopan.data

import com.f776.ecopan.data.database.entity.PaymentMethod
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.BranchRepositoryImpl
import com.f776.ecopan.data.database.repository.CatalogRepositoryImpl
import com.f776.ecopan.data.database.repository.SaleRepositoryImpl
import com.f776.ecopan.data.database.repository.TenantRepositoryImpl
import com.f776.ecopan.data.database.repository.UserRepositoryImpl
import com.f776.ecopan.data.database.repository.model.IngestResult
import com.f776.ecopan.data.database.repository.model.SaleLineInput
import com.f776.ecopan.data.domain.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** 02 §8 AC: sale+lines atomic insert; duplicate sale id -> DO NOTHING + original server_seq. */
class SaleRepositoryTest : RepositoryTestBase() {

    private val tenants = TenantRepositoryImpl()
    private val branches = BranchRepositoryImpl()
    private val users = UserRepositoryImpl()
    private val catalog = CatalogRepositoryImpl()
    private val sales = SaleRepositoryImpl(Clock.System)

    @BeforeEach
    fun setUp() = truncateAll()

    @Test
    fun `sale and its lines are inserted atomically and are visible together`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "cashier1", "hash", null, "Cashier One", Role.CASHIER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)

        val saleId = Uuid.random()
        val line = SaleLineInput(
            id = Uuid.random(),
            productId = product.id,
            soldBy = SoldBy.WEIGHT,
            weightGrams = 500,
            quantity = null,
            unitPriceMinor = 2500,
            lineTotalMinor = 1250
        )

        val result = sales.createWithLines(
            id = saleId,
            tenantId = tenantId,
            branchId = branch.id,
            cashierId = cashier.id,
            soldAt = Clock.System.now(),
            paymentMethod = PaymentMethod.CASH,
            totalMinor = 1250,
            deviceId = "pos-1",
            lines = listOf(line)
        )

        assertIs<IngestResult.Applied<*>>(result)
        val sale = sales.findById(saleId)
        assertEquals(1, sale?.lines?.size)
        assertEquals(1250, sale?.lines?.single()?.lineTotalMinor)
    }

    @Test
    fun `re-pushing the same sale id is a no-op and returns the original server_seq`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "cashier1", "hash", null, "Cashier One", Role.CASHIER)
        val product = catalog.createProduct(Uuid.random(), tenantId, "Marraqueta", "BREAD", SoldBy.WEIGHT, 2500, null, null)

        val saleId = Uuid.random()
        val line = SaleLineInput(
            id = Uuid.random(), productId = product.id, soldBy = SoldBy.WEIGHT, weightGrams = 500,
            quantity = null, unitPriceMinor = 2500, lineTotalMinor = 1250
        )

        val first = sales.createWithLines(
            saleId, tenantId, branch.id, cashier.id, Clock.System.now(), PaymentMethod.CASH, 1250, "pos-1", listOf(line)
        )
        assertIs<IngestResult.Applied<*>>(first)

        val second = sales.createWithLines(
            saleId, tenantId, branch.id, cashier.id, Clock.System.now(), PaymentMethod.CARD, 9999, "pos-1",
            listOf(line.copy(id = Uuid.random()))
        )
        assertIs<IngestResult.Duplicate<*>>(second)
        assertEquals(first.value.serverSeq, second.value.serverSeq)

        // No extra lines were inserted for the duplicate push.
        assertEquals(1, sales.findById(saleId)?.lines?.size)
    }

    @Test
    fun `a bad line rolls back the whole sale -- partial failure leaves nothing behind`() = runTest {
        val tenantId = Uuid.random()
        tenants.create(tenantId, "reference", "Reference Bakery", "CLP")
        val branch = branches.create(Uuid.random(), tenantId, "Main")
        val cashier = users.create(Uuid.random(), tenantId, branch.id, "cashier1", "hash", null, "Cashier One", Role.CASHIER)

        val saleId = Uuid.random()
        // References a product that doesn't exist -- violates the FK constraint on sale_lines.
        val badLine = SaleLineInput(
            id = Uuid.random(), productId = Uuid.random(), soldBy = SoldBy.WEIGHT, weightGrams = 500,
            quantity = null, unitPriceMinor = 2500, lineTotalMinor = 1250
        )

        var threw = false
        try {
            sales.createWithLines(saleId, tenantId, branch.id, cashier.id, Clock.System.now(), PaymentMethod.CASH, 1250, "pos-1", listOf(badLine))
        } catch (_: Exception) {
            threw = true
        }

        assertEquals(true, threw, "expected the FK violation on the line to throw")
        assertEquals(null, sales.findById(saleId), "the sale row must not survive when its lines fail to insert")
    }
}
