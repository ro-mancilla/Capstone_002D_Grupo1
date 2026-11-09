package com.f776.ecopan.feature.production.unittest.fake

import com.f776.ecopan.domain.production.SuggestedProduction
import com.f776.ecopan.domain.production.SuggestedProductionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

class FakeSuggestedProductionRepository : SuggestedProductionRepository {
    val rows = MutableStateFlow<List<SuggestedProduction>>(emptyList())

    /** Every (branch, day) the view model asked for, in order. */
    val requested = mutableListOf<Pair<Uuid, LocalDate>>()

    override fun observeForDay(branchId: Uuid, day: LocalDate): Flow<List<SuggestedProduction>> {
        requested += branchId to day
        return rows.map { all -> all.filter { it.branchId == branchId && it.forecastDate == day } }
    }
}
