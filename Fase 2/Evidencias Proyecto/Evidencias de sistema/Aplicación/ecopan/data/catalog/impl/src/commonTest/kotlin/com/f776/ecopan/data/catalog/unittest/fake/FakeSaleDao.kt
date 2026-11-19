package com.f776.ecopan.data.catalog.unittest.fake

import com.f776.unigrid.core.database.dao.SaleDao
import com.f776.unigrid.core.database.dao.UnsyncedProductUnits
import com.f776.unigrid.core.database.entity.OutboxEntity
import com.f776.unigrid.core.database.entity.SaleEntity
import com.f776.unigrid.core.database.entity.SaleLineEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlin.uuid.Uuid

/** Only the unsynced-units seam matters to the catalog; everything else is unused here. */
internal class FakeSaleDao : SaleDao {
    val unsynced = MutableStateFlow<List<UnsyncedProductUnits>>(emptyList())

    override fun observeUnsyncedUnitsByProduct(): Flow<List<UnsyncedProductUnits>> = unsynced

    override suspend fun insertSale(sale: SaleEntity) = Unit
    override suspend fun insertLines(lines: List<SaleLineEntity>) = Unit
    override suspend fun insertOutboxRow(row: OutboxEntity) = Unit
    override fun observeSalesInRange(branchId: Uuid, startMillis: Long, endMillis: Long) = flowOf(emptyList<SaleEntity>())
    override fun observeLinesInRange(branchId: Uuid, startMillis: Long, endMillis: Long) = flowOf(emptyList<SaleLineEntity>())
    override suspend fun nextReceiptNumber(): Int = 1
    override suspend fun clearSales() = Unit
    override suspend fun clearLines() = Unit
}
