package com.f776.unigrid.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.f776.unigrid.core.database.entity.ForecastEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Schema smoke tests for the cached `forecasts` table, added in schema v13. */
class ForecastSchemaTest {

    private val database = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

    private val tenantId = Uuid.random()
    private val branchId = Uuid.random()
    private val tomorrow = LocalDate(2026, 7, 30)
    private val generatedAt = Instant.fromEpochMilliseconds(1_785_000_000_000)

    @AfterTest
    fun closeDatabase() {
        database.close()
    }

    private fun forecast(
        name: String = "Marraqueta",
        day: LocalDate = tomorrow,
        branch: Uuid = branchId,
        id: Uuid = Uuid.random(),
        grams: Long? = 12_500,
        units: Int? = null,
    ) = ForecastEntity(
        id = id, tenantId = tenantId, branchId = branch, productId = Uuid.random(), productName = name,
        productType = "Pan", forecastDate = day, predictedGrams = grams, predictedUnits = units,
        modelVersion = "baseline-trailing-mean", generatedAt = generatedAt, serverSeq = 1,
    )

    @Test
    fun `a day is read per branch, ordered by product name`() = runTest {
        val dao = database.forecastDao()
        dao.upsertAll(
            listOf(
                forecast("Zapallo", grams = null, units = 4),
                forecast("Amasado"),
                forecast("Elsewhere", branch = Uuid.random()),
                forecast("Yesterday's", day = LocalDate(2026, 7, 29)),
            ),
        )

        val rows = dao.observeForDay(branchId, tomorrow).first()

        assertEquals(listOf("Amasado", "Zapallo"), rows.map { it.productName })
        assertEquals(4, rows.last().predictedUnits)
    }

    @Test
    fun `a re-pulled row overwrites the cached one by id`() = runTest {
        val dao = database.forecastDao()
        val id = Uuid.random()
        dao.upsertAll(listOf(forecast(id = id, grams = 10_000)))
        dao.upsertAll(listOf(forecast(id = id, grams = 12_000)))

        val rows = dao.observeForDay(branchId, tomorrow).first()

        assertEquals(12_000L, rows.single().predictedGrams)
    }

    @Test
    fun `deleteBefore drops only earlier days`() = runTest {
        val dao = database.forecastDao()
        dao.upsertAll(listOf(forecast("Old", day = LocalDate(2026, 7, 20)), forecast("New")))

        dao.deleteBefore(LocalDate(2026, 7, 29))

        assertEquals(listOf("New"), dao.observeForDay(branchId, tomorrow).first().map { it.productName })
        assertEquals(emptyList(), dao.observeForDay(branchId, LocalDate(2026, 7, 20)).first())
    }
}
