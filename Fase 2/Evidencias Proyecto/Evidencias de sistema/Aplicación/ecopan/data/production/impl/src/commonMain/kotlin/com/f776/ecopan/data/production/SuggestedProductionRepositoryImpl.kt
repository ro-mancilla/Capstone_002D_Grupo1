package com.f776.ecopan.data.production

import com.f776.ecopan.data.production.local.mapper.toDomain
import com.f776.ecopan.domain.production.SuggestedProduction
import com.f776.ecopan.domain.production.SuggestedProductionRepository
import com.f776.unigrid.core.database.dao.ForecastDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

internal class SuggestedProductionRepositoryImpl(
    private val forecastDao: ForecastDao,
) : SuggestedProductionRepository {

    override fun observeForDay(branchId: Uuid, day: LocalDate): Flow<List<SuggestedProduction>> =
        forecastDao.observeForDay(branchId, day).map { rows -> rows.mapNotNull { it.toDomain() } }
}
