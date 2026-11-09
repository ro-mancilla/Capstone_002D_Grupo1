package com.f776.ecopan.data.sales

import com.f776.ecopan.data.sales.local.mapper.toDomain
import com.f776.ecopan.data.sales.local.mapper.toEntity
import com.f776.ecopan.data.sales.wire.toPushPayload
import com.f776.ecopan.domain.sales.Sale
import com.f776.ecopan.domain.sales.SaleRepository
import com.f776.ecopan.domain.sync.outboxRow
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.unigrid.core.database.dao.SaleDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * "Today" is decided (not an open question) to always be the till's own OS timezone — 02's
 * `REPORTING_TZ` env var is a *server-side analytics aggregation* concern (a fixed deployment-wide
 * constant for cross-branch KPI rollups); this is a different, till-local concern (what a cashier
 * standing at that specific piece of hardware sees as "today"), so the two are allowed to diverge
 * and neither needs a tenant-level override.
 */
internal class SaleRepositoryImpl(
    private val saleDao: SaleDao,
) : SaleRepository {

    override suspend fun recordSale(sale: Sale): Int {
        val outboxRow = outboxRow(
            mutationId = sale.id,
            entityType = SyncEntityType.SALE,
            opType = SyncOpType.CREATE,
            payloadJson = Json.encodeToString(sale.toPushPayload()),
        )
        return saleDao.recordSale(sale.toEntity(), sale.lines.map { it.toEntity(sale.id) }, outboxRow)
    }

    override fun observeTodaySales(branchId: Uuid): Flow<List<Sale>> {
        val (startMillis, endMillis) = todayRangeMillis()
        return combine(
            saleDao.observeSalesInRange(branchId, startMillis, endMillis),
            saleDao.observeLinesInRange(branchId, startMillis, endMillis),
        ) { sales, lines ->
            sales.map { sale -> sale.toDomain(lines.filter { it.saleId == sale.id }) }
        }
    }

    private fun todayRangeMillis(): Pair<Long, Long> {
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(zone).date
        val startOfDay = LocalDateTime(today, LocalTime(0, 0)).toInstant(zone)
        val startOfNextDay = startOfDay.plus(1, DateTimeUnit.DAY, zone)
        return startOfDay.toEpochMilliseconds() to startOfNextDay.toEpochMilliseconds()
    }
}
