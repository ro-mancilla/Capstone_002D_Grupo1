package com.f776.ecopan.data.production.sync

import com.f776.ecopan.domain.sync.SyncApplier
import com.f776.ecopan.domain.sync.SyncEntityType
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.unigrid.core.database.dao.ForecastDao
import com.f776.unigrid.core.database.entity.ForecastEntity
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Pull-side applier for `FORECAST` (03 §4.3 family 1: server-authoritative, overwrite by id). The
 * server sends only the pulling principal's branch, and only for a tenant with
 * `DEMAND_FORECASTING` on, so there is nothing to filter here; what lands is the baker's
 * *producción sugerida* for offline reading.
 *
 * Rows older than [RETAIN_DAYS] before the pulled row's own day are dropped as it is applied, so
 * the table stays a week or so deep instead of growing a row per product per day for ever.
 *
 * `tenantId` is not on the wire (it is the JWT principal, 02 §4.1) and is resolved from
 * [TenantRepository] like every other applier; a pull before any tenant is cached is a no-op.
 */
class ForecastSyncApplier(
    private val forecastDao: ForecastDao,
    private val tenantRepository: TenantRepository,
) : SyncApplier {

    override val entityType: SyncEntityType = SyncEntityType.FORECAST

    override suspend fun apply(payload: JsonElement, opType: SyncOpType, serverSeq: Long) {
        val tenantId = tenantRepository.observeTenant().first()?.id ?: return
        val wire = json.decodeFromJsonElement<ForecastPullPayload>(payload)
        val day = LocalDate.parse(wire.forecastDate)

        forecastDao.upsertAll(
            listOf(
                ForecastEntity(
                    id = Uuid.parse(wire.id),
                    tenantId = tenantId,
                    branchId = Uuid.parse(wire.branchId),
                    productId = Uuid.parse(wire.productId),
                    productName = wire.productName,
                    productType = wire.productType,
                    forecastDate = day,
                    predictedGrams = wire.predictedGrams,
                    predictedUnits = wire.predictedUnits,
                    modelVersion = wire.modelVersion,
                    generatedAt = Instant.fromEpochMilliseconds(wire.generatedAt),
                    serverSeq = serverSeq,
                ),
            ),
        )
        forecastDao.deleteBefore(day.minus(RETAIN_DAYS, DateTimeUnit.DAY))
    }

    private companion object {
        const val RETAIN_DAYS = 7
        val json = Json { ignoreUnknownKeys = true }
    }
}

/** Mirrors the backend's `ForecastPullPayload`. */
@Serializable
private data class ForecastPullPayload(
    val id: String,
    val branchId: String,
    val productId: String,
    val productName: String,
    val productType: String,
    val forecastDate: String,
    val predictedGrams: Long? = null,
    val predictedUnits: Int? = null,
    val modelVersion: String,
    val generatedAt: Long,
)
