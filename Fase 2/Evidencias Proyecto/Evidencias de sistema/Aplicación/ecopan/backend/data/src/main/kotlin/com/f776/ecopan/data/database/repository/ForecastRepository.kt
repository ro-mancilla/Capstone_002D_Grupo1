package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.repository.model.Forecast
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** One predicted day for one product, as the worker hands it over. */
data class ForecastUpsert(
    val productId: Uuid,
    val forecastDate: LocalDate,
    val predictedGrams: Long?,
    val predictedUnits: Int?,
    val modelVersion: String
)

/** What a [ForecastRepository.upsertAll] did with its rows. */
data class ForecastUpsertResult(
    val written: Int,
    /** Rows for a day before `notBefore`: past days are scored later, so they are never rewritten. */
    val skippedPast: Int,
    /** Rows whose product does not exist in the tenant any more (deleted while the ML call ran). */
    val skippedUnknownProduct: Int
)

/** F1/F2 (02 §3) -- `forecasts` is the server-side ML cache; devices get it through sync pull. */
interface ForecastRepository {
    /**
     * Upserts [rows] for one branch in a **single transaction**, on
     * `(tenantId, branchId, productId, forecastDate)`. The row's type snapshot is copied from the
     * product.
     *
     * Rows dated before [notBefore] (the caller's "today" in the reporting zone) are skipped, never
     * written: a past day's forecast is what accuracy scoring compares to what was sold, so a
     * replayed message or a skewed clock must not be able to rewrite it. Rows for a product that
     * no longer exists are skipped too, so one deleted product cannot fail the whole branch.
     *
     * `server_seq` is allocated **after** a transaction-scoped advisory lock, so concurrent
     * forecast writers commit in `server_seq` order and a device's cursor cannot step past a
     * row that commits later with a lower number (writers of other tables do not take this lock;
     * that gap predates forecasts).
     */
    suspend fun upsertAll(
        tenantId: Uuid,
        branchId: Uuid,
        rows: List<ForecastUpsert>,
        notBefore: LocalDate
    ): ForecastUpsertResult

    /** [productType] matches the product's *current* type by normalised key; [productId] narrows to one product. */
    suspend fun listByBranch(
        tenantId: Uuid,
        branchId: Uuid,
        from: LocalDate,
        to: LocalDate,
        productType: String? = null,
        productId: Uuid? = null
    ): List<Forecast>
}
