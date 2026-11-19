package com.f776.ecopan.data.sales.unittest

import com.f776.ecopan.core.domain.catalog.PaymentMethod
import com.f776.ecopan.core.domain.catalog.SoldBy
import com.f776.ecopan.data.sales.SaleRepositoryImpl
import com.f776.ecopan.data.sales.unittest.fake.FakeSaleDao
import com.f776.ecopan.data.sales.wire.SalePushPayload
import com.f776.ecopan.domain.sales.Sale
import com.f776.ecopan.domain.sales.SaleLine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SaleRepositoryImplTest {

    private val branchId = Uuid.random()
    private val dao = FakeSaleDao()
    private val repository = SaleRepositoryImpl(dao)

    @Test
    fun `recordSale persists the sale and its lines together`() = runTest {
        val sale = sale()

        repository.recordSale(sale)

        val persisted = repository.observeTodaySales(branchId).first().single()
        assertEquals(sale.id, persisted.id)
        assertEquals(sale.lines.map { it.id }, persisted.lines.map { it.id })
    }

    @Test
    fun `recordSale returns the sequential receipt number and stores it on the sale`() = runTest {
        val first = repository.recordSale(sale())
        val second = repository.recordSale(sale())

        assertEquals(listOf(1, 2), listOf(first, second))
        assertEquals(setOf(1, 2), repository.observeTodaySales(branchId).first().map { it.receiptNumber }.toSet())
    }

    @Test
    fun `recordSale enqueues an outbox row keyed by the sale's own client-minted id`() = runTest {
        val sale = sale()

        repository.recordSale(sale)

        val row = dao.outboxRows.single()
        assertEquals(sale.id, row.mutationId)
        assertEquals("SALE", row.entityType)
        assertEquals("CREATE", row.opType)
    }

    @Test
    fun `the outbox payload is the real wire shape -- no tenantId or deviceId lines embedded`() = runTest {
        val sale = sale()

        repository.recordSale(sale)

        val payload = Json.decodeFromString<SalePushPayload>(dao.outboxRows.single().payload)
        assertEquals(sale.id.toString(), payload.id)
        assertEquals(sale.branchId.toString(), payload.branchId)
        assertEquals("CASH", payload.paymentMethod)
        assertEquals(sale.lines.map { it.id.toString() }, payload.lines.map { it.id })
        assertEquals("WEIGHT", payload.lines.single().soldBy)
    }

    @Test
    fun `observeTodaySales excludes sales from other branches and other days`() = runTest {
        val now = Clock.System.now()
        repository.recordSale(sale(timestamp = now))
        repository.recordSale(sale(timestamp = now - 48.hours))
        repository.recordSale(sale(branchId = Uuid.random(), timestamp = now))

        val today = repository.observeTodaySales(branchId).first()

        assertEquals(1, today.size)
    }

    private fun sale(
        branchId: Uuid = this.branchId,
        timestamp: Instant = Clock.System.now(),
    ): Sale {
        val saleId = Uuid.random()
        return Sale(
            id = saleId,
            branchId = branchId,
            cashierId = Uuid.random(),
            timestamp = timestamp,
            paymentMethod = PaymentMethod.CASH,
            totalMinor = 1010,
            cashTenderedMinor = 2000,
            changeMinor = 990,
            lines = listOf(
                SaleLine(
                    id = Uuid.random(),
                    productId = Uuid.random(),
                    productTypeSnapshot = "Marraqueta",
                    soldBy = SoldBy.WEIGHT,
                    weightGrams = 842,
                    quantity = null,
                    unitPriceMinorSnapshot = 1200,
                    lineTotalMinor = 1010,
                ),
            ),
        )
    }
}
