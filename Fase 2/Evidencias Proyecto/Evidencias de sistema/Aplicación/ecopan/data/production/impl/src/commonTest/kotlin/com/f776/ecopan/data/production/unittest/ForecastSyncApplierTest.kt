package com.f776.ecopan.data.production.unittest

import com.f776.ecopan.core.domain.tenant.EntitlementModule
import com.f776.ecopan.data.production.SuggestedProductionRepositoryImpl
import com.f776.ecopan.data.production.sync.ForecastSyncApplier
import com.f776.ecopan.domain.production.Estimator
import com.f776.ecopan.domain.production.SuggestedQuantity
import com.f776.ecopan.domain.sync.SyncOpType
import com.f776.ecopan.domain.tenant.Branch
import com.f776.ecopan.domain.tenant.BranchUpsert
import com.f776.ecopan.domain.tenant.TenantProfile
import com.f776.ecopan.domain.tenant.TenantRepository
import com.f776.ecopan.domain.tenant.UserAccount
import com.f776.ecopan.domain.tenant.UserAccountUpsert
import com.f776.unigrid.core.database.dao.ForecastDao
import com.f776.unigrid.core.database.entity.ForecastEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class ForecastSyncApplierTest {

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val tomorrow = LocalDate(2026, 7, 30)
    private val dao = FakeForecastDao()
    private val tenant = FixedTenantRepository(tenantId)
    private val applier = ForecastSyncApplier(dao, tenant)
    private val repository = SuggestedProductionRepositoryImpl(dao)

    private fun wire(
        id: Uuid = Uuid.random(),
        name: String = "Marraqueta",
        day: LocalDate = tomorrow,
        grams: Long? = 12_500,
        units: Int? = null,
        modelVersion: String = "baseline-trailing-mean",
    ): JsonElement = buildJsonObject {
        put("id", id.toString())
        put("branchId", branchId.toString())
        put("productId", Uuid.random().toString())
        put("productName", name)
        put("productType", "Pan")
        put("forecastDate", day.toString())
        grams?.let { put("predictedGrams", it) }
        units?.let { put("predictedUnits", it) }
        put("modelVersion", modelVersion)
        put("generatedAt", 1_785_000_000_000)
    }

    @Test
    fun `a pulled row is cached and read back as a suggestion with its unit and estimator`() = runTest {
        applier.apply(wire(), SyncOpType.UPSERT, serverSeq = 7)
        applier.apply(wire(name = "Hallulla", grams = null, units = 40, modelVersion = "lgbm-favorita"), SyncOpType.UPSERT, 8)

        val rows = repository.observeForDay(branchId, tomorrow).first().associateBy { it.productName }

        assertEquals(SuggestedQuantity.Grams(12_500), rows.getValue("Marraqueta").quantity)
        assertEquals(Estimator.BASELINE, rows.getValue("Marraqueta").estimator)
        assertEquals(SuggestedQuantity.Units(40), rows.getValue("Hallulla").quantity)
        assertEquals(Estimator.MODEL, rows.getValue("Hallulla").estimator)
        assertEquals(7, dao.rows.value.first { it.productName == "Marraqueta" }.serverSeq)
    }

    @Test
    fun `a re-pulled row overwrites the cached figure by id`() = runTest {
        val id = Uuid.random()
        applier.apply(wire(id = id, grams = 10_000), SyncOpType.UPSERT, 1)
        applier.apply(wire(id = id, grams = 12_000), SyncOpType.UPSERT, 2)

        assertEquals(
            SuggestedQuantity.Grams(12_000),
            repository.observeForDay(branchId, tomorrow).first().single().quantity,
        )
    }

    @Test
    fun `a row with no quantity is never offered as a suggestion`() = runTest {
        applier.apply(wire(grams = null, units = null), SyncOpType.UPSERT, 1)

        assertEquals(emptyList(), repository.observeForDay(branchId, tomorrow).first())
    }

    @Test
    fun `an unrecognised model version is unknown, never the model`() = runTest {
        applier.apply(wire(modelVersion = "mystery-9"), SyncOpType.UPSERT, 1)

        assertEquals(Estimator.UNKNOWN, repository.observeForDay(branchId, tomorrow).first().single().estimator)
    }

    @Test
    fun `days far behind the pulled one are pruned`() = runTest {
        applier.apply(wire(name = "Ancient", day = LocalDate(2026, 7, 1)), SyncOpType.UPSERT, 1)
        applier.apply(wire(name = "Tomorrow"), SyncOpType.UPSERT, 2)

        assertEquals(listOf("Tomorrow"), dao.rows.value.map { it.productName })
    }

    @Test
    fun `nothing is cached before a tenant is`() = runTest {
        tenant.clear()
        applier.apply(wire(), SyncOpType.UPSERT, 1)

        assertNull(dao.rows.value.firstOrNull())
    }

    private class FakeForecastDao : ForecastDao {
        val rows = MutableStateFlow<List<ForecastEntity>>(emptyList())

        override suspend fun upsertAll(forecasts: List<ForecastEntity>) {
            rows.value = rows.value.filterNot { old -> forecasts.any { it.id == old.id } } + forecasts
        }

        override fun observeForDay(branchId: Uuid, day: LocalDate): Flow<List<ForecastEntity>> =
            rows.map { all -> all.filter { it.branchId == branchId && it.forecastDate == day }.sortedBy { it.productName } }

        override suspend fun deleteBefore(day: LocalDate) {
            rows.value = rows.value.filter { it.forecastDate >= day }
        }

        override suspend fun clear() {
            rows.value = emptyList()
        }
    }

    private class FixedTenantRepository(tenantId: Uuid) : TenantRepository {
        private val profile = MutableStateFlow<TenantProfile?>(
            TenantProfile(
                id = tenantId, slug = "el-sol", name = "Panadería El Sol", currencyCode = "CLP",
                enabledModules = setOf(EntitlementModule.DEMAND_FORECASTING),
            ),
        )

        override fun observeTenant(): Flow<TenantProfile?> = profile
        override suspend fun upsertTenant(profile: TenantProfile) { this.profile.value = profile }
        override fun observeBranches(): Flow<List<Branch>> = MutableStateFlow(emptyList())
        override suspend fun upsertBranches(branches: List<BranchUpsert>) = Unit
        override fun observeUsers(): Flow<List<UserAccount>> = MutableStateFlow(emptyList())
        override suspend fun upsertUsers(users: List<UserAccountUpsert>) = Unit
        override suspend fun verifyPin(userId: Uuid, pin: String): Boolean = false
        override suspend fun clear() { profile.value = null }
    }
}
