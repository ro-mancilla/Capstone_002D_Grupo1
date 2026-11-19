package com.f776.ecopan.data.sales.unittest.fake

import com.f776.unigrid.core.database.dao.SaleDao
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.SaleEntity
import com.f776.unigrid.core.database.entity.SaleLineEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.uuid.Uuid

internal class FakeSaleDao : SaleDao {
    private val sales = MutableStateFlow<Map<Uuid, SaleEntity>>(emptyMap())
    private val lines = MutableStateFlow<Map<Uuid, SaleLineEntity>>(emptyMap())
    val outboxRows = mutableListOf<OutboxEntity>()

    override suspend fun nextReceiptNumber(): Int = (sales.value.values.maxOfOrNull { it.receiptNumber } ?: 0) + 1

    override suspend fun insertSale(sale: SaleEntity) {
        sales.value = sales.value + (sale.id to sale)
    }

    override suspend fun insertLines(lines: List<SaleLineEntity>) {
        this.lines.value = this.lines.value + lines.associateBy { it.id }
    }

    override suspend fun insertOutboxRow(row: OutboxEntity) {
        outboxRows += row
    }

    override fun observeSalesInRange(branchId: Uuid, startMillis: Long, endMillis: Long) =
        sales.map { byId -> byId.values.filter { it.inRange(branchId, startMillis, endMillis) }.sortedByDescending { it.timestamp } }

    override fun observeLinesInRange(branchId: Uuid, startMillis: Long, endMillis: Long) =
        sales.map { byId ->
            val saleIds = byId.values.filter { it.inRange(branchId, startMillis, endMillis) }.map { it.id }.toSet()
            lines.value.values.filter { it.saleId in saleIds }
        }

    override fun observeUnsyncedUnitsByProduct() = kotlinx.coroutines.flow.flowOf(emptyList<com.f776.unigrid.core.database.dao.UnsyncedProductUnits>())

    override suspend fun clearSales() {
        sales.value = emptyMap()
    }

    override suspend fun clearLines() {
        lines.value = emptyMap()
    }

    private fun SaleEntity.inRange(branchId: Uuid, startMillis: Long, endMillis: Long): Boolean {
        val millis = timestamp.toEpochMilliseconds()
        return this.branchId == branchId && millis >= startMillis && millis < endMillis
    }
}
