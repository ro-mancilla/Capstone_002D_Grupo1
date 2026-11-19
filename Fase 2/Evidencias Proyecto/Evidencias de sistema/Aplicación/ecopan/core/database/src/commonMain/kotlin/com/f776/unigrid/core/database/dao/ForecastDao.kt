package com.f776.unigrid.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.f776.unigrid.core.database.entity.ForecastEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

@Dao
interface ForecastDao {

    /** Pull-side apply: overwrite by the server-minted id (03 §4.3 family 1). */
    @Upsert
    suspend fun upsertAll(forecasts: List<ForecastEntity>)

    @Query("SELECT * FROM forecasts WHERE branchId = :branchId AND forecastDate = :day ORDER BY productName")
    fun observeForDay(branchId: Uuid, day: LocalDate): Flow<List<ForecastEntity>>

    /** Housekeeping: days that can no longer be a "tomorrow" are dead weight on the device. */
    @Query("DELETE FROM forecasts WHERE forecastDate < :day")
    suspend fun deleteBefore(day: LocalDate)

    @Query("DELETE FROM forecasts")
    suspend fun clear()
}
