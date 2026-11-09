package com.f776.ecopan.worker.domain

import com.f776.ecopan.data.domain.ProductTypeKey
import com.f776.ecopan.data.database.entity.ProductOrigin
import com.f776.ecopan.data.database.entity.SoldBy
import com.f776.ecopan.data.database.repository.AnalyticsRepository
import com.f776.ecopan.data.database.repository.ForecastRepository
import com.f776.ecopan.data.database.repository.ForecastUpsert
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import com.f776.ecopan.data.messagebroker.model.ForecastRefreshRequested
import com.f776.ecopan.worker.ml.MlForecastClient
import com.f776.ecopan.worker.ml.MlForecastRequest
import com.f776.ecopan.worker.ml.MlHistoryPoint
import com.f776.ecopan.worker.ml.MlSeriesHistory
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * F2's worker half (02 §9 item 7, 08 §5): on a [ForecastRefreshRequested] message, read the
 * tenant's daily demand per (branch, Panadería product) out of Postgres, `POST /forecast` it to
 * the ML service, and upsert the returned predictions into `forecasts` (#51).
 *
 * Decisions worth being able to defend:
 *
 *  * **History ends yesterday, `start_day` is today** (both in [reportingTz]). A refresh can fire
 *    at any hour, so today's sales are a partial day -- feeding them to the model as an observed
 *    demand would bias the freshest, highest-weight lag low. The model's history-overlap guard
 *    would also trim any row dated on/after `start_day` anyway; better to never send it.
 *  * **Tomorrow is horizon step 2, not d+1.** The last complete day is yesterday, so the model's
 *    d+1 is *today* and "mañana" (what the web table and the baker's card show) is its second step.
 *    `ml/RESULTS.md`'s +42.8 % edge is measured at d+1 from the last observed day; at step 2 it is
 *    smaller. Feeding today's partial sales to reach a true d+1 would bias the freshest lag low,
 *    so the UI says "calculado con ventas hasta ayer" instead of hiding the gap.
 *  * **Days without sales are demand 0, not missing.** The series is zero-filled from its first
 *    sale in the window through yesterday, because "the bakery sold none" is an observation, not
 *    a gap -- and the feature builder's lags assume a dense daily series.
 *  * **The series is the product, not its type.** A type total ("all bread, kg") informs no baking
 *    decision. The ML service is id-agnostic, so the product id travels in its `product_type`
 *    field. Almacén products are resold, not baked, and are never forecast.
 *  * **One unit per product, chosen from the sales themselves.** `forecasts` has one
 *    `predicted_grams`/`predicted_units` pair per row. A product has one `soldBy`, but its lines
 *    could disagree after an edit: the lines' majority `soldBy` decides (ties → WEIGHT),
 *    minority-unit lines are dropped from the sum rather than added apples-to-oranges, and the
 *    mix is logged.
 *  * **Series are isolated, failures still surface.** Requests are chunked, each chunk's rows are
 *    stored in one transaction, and a chunk that fails does not stop the rest; the first failure is
 *    rethrown afterwards. The broker logs and nacks without requeue, and F2 is a manual,
 *    re-triggerable 202 -- retry loops belong to the caller. Redelivery is harmless: the upsert
 *    key makes this processor idempotent, and the repository refuses past days.
 *  * **Estimator labels depend on the ML service.** The product UUID is an unseen `family` key to
 *    the trained model; until the ML service routes unseen keys to a labelled baseline (PR #56),
 *    a 28+-day series is still scored by LightGBM and labelled `lgbm-*`. The UI says *Modelo* for
 *    `lgbm-*` only, so it is honest either way, but the *meaning* of that label for bakery series
 *    is only sound once #56 is deployed.
 */
class ForecastRefreshProcessorImpl(
    private val analyticsRepository: AnalyticsRepository,
    private val forecastRepository: ForecastRepository,
    private val mlForecastClient: MlForecastClient,
    private val reportingTz: String,
    private val clock: Clock = Clock.System,
) : ForecastRefreshProcessor {
    private val logger = LoggerFactory.getLogger(ForecastRefreshProcessorImpl::class.java)

    override suspend fun processMessage(message: ForecastRefreshRequested) {
        val tenantId = runCatching { Uuid.parse(message.tenantId) }.getOrNull()
        val branchId = runCatching { Uuid.parse(message.branchId) }.getOrNull()
        if (tenantId == null || branchId == null) {
            logger.warn("ForecastRefreshRequested with unparseable ids ($message); dropping")
            return
        }

        val zone = TimeZone.of(reportingTz)
        val now = clock.now()
        val today = now.toLocalDateTime(zone).date
        val windowStart = today.plus(-HISTORY_DAYS, DateTimeUnit.DAY)

        // Almacén is bought finished and restocked by purchase: no baking decision to inform.
        val facts = analyticsRepository
            .saleLineFacts(tenantId, branchId, from = windowStart.atStartOfDayIn(zone), to = now)
            .filter { it.origin == ProductOrigin.PANADERIA }
            .filter { message.productType == null || ProductTypeKey.of(it.productType) == ProductTypeKey.of(message.productType!!) }

        val series = facts.groupBy { it.productId }
            .mapNotNull { (productId, lines) -> buildSeries(branchId, productId, lines, zone, today) }
        if (series.isEmpty()) {
            logger.info(
                "No sales history in the last $HISTORY_DAYS days for tenant={} branch={} productType={}; nothing to forecast",
                tenantId, branchId, message.productType
            )
            return
        }

        // Bounded requests, isolated failures: a branch with many products is sent in chunks, and a
        // chunk the ML service or the database rejects does not stop the others. The first failure
        // is rethrown at the end so the broker still logs the message as failed.
        val rowsByModel = mutableMapOf<String, Int>()
        var written = 0
        var skipped = 0
        var firstFailure: Exception? = null
        var failedChunks = 0
        for (chunk in series.chunked(MAX_SERIES_PER_REQUEST)) {
            try {
                val response = mlForecastClient.forecast(
                    MlForecastRequest(
                        series = chunk.map { it.history },
                        horizonDays = HORIZON_DAYS,
                        startDay = today.toString(),
                    )
                )
                val soldByOf = chunk.associate { it.history.productType to it.soldBy }
                val upserts = mutableListOf<ForecastUpsert>()
                for (forecast in response.forecasts) {
                    val soldBy = soldByOf[forecast.productType]
                    val productId = runCatching { Uuid.parse(forecast.productType) }.getOrNull()
                    if (soldBy == null || productId == null) {
                        logger.warn("ML response contains unrequested series ${forecast.productType}; skipping")
                        continue
                    }
                    for (point in forecast.predictions) {
                        val predicted = point.predicted.coerceAtLeast(0.0).roundToLong()
                        upserts += ForecastUpsert(
                            productId = productId,
                            forecastDate = LocalDate.parse(point.day),
                            predictedGrams = predicted.takeIf { soldBy == SoldBy.WEIGHT },
                            predictedUnits = if (soldBy == SoldBy.UNIT) predicted.toInt() else null,
                            modelVersion = forecast.modelVersion,
                        )
                    }
                    rowsByModel.merge(forecast.modelVersion, forecast.predictions.size, Int::plus)
                }
                // One transaction for the whole chunk; past days are refused by the repository.
                val result = forecastRepository.upsertAll(tenantId, branchId, upserts, notBefore = today)
                written += result.written
                skipped += result.skippedPast + result.skippedUnknownProduct
                if (result.skippedUnknownProduct > 0) {
                    logger.warn(
                        "{} forecast rows skipped for products deleted during the refresh (tenant={} branch={})",
                        result.skippedUnknownProduct, tenantId, branchId
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failedChunks++
                firstFailure = firstFailure ?: e
                logger.error("Forecast refresh chunk of {} series failed for tenant={} branch={}", chunk.size, tenantId, branchId, e)
            }
        }
        // Break the count down by the *row* labels, not just the service's loaded model version:
        // a service reporting `lgbm-...` still answers with cold-start baselines for any series
        // below MIN_HISTORY_FOR_MODEL, and "which rows are real forecasts?" is the operationally
        // interesting question. Logging only `service_model_version` reads as if the model
        // produced everything.
        logger.info(
            "Forecast refresh done for tenant={} branch={}: {} series in, {} rows upserted ({} skipped) by {}, {} chunk(s) failed",
            tenantId, branchId, series.size, written, skipped, rowsByModel, failedChunks
        )
        firstFailure?.let { throw it }
    }

    private data class SeriesWithUnit(val history: MlSeriesHistory, val soldBy: SoldBy)

    private fun buildSeries(
        branchId: Uuid,
        productId: Uuid,
        lines: List<SaleLineFact>,
        zone: TimeZone,
        today: LocalDate,
    ): SeriesWithUnit? {
        val byUnit = lines.groupBy { it.soldBy }
        if (byUnit.size > 1) {
            logger.warn(
                "Product {} mixes soldBy units ({}); forecasting only the majority unit",
                productId, byUnit.mapValues { it.value.size }
            )
        }
        val soldBy = byUnit.maxWith(
            compareBy<Map.Entry<SoldBy, List<SaleLineFact>>> { it.value.size }
                // Deterministic tie-break: WEIGHT (grams) over UNIT.
                .thenBy { it.key == SoldBy.WEIGHT }
        ).key

        val demandByDay = byUnit.getValue(soldBy)
            .groupBy { it.soldAt.toLocalDateTime(zone).date }
            .filterKeys { it < today }
            .mapValues { (_, dayLines) ->
                when (soldBy) {
                    SoldBy.WEIGHT -> dayLines.sumOf { it.weightGrams ?: 0L }.toDouble()
                    SoldBy.UNIT -> dayLines.sumOf { it.quantity ?: 0 }.toDouble()
                }
            }
        val firstDay = demandByDay.keys.minOrNull() ?: return null

        val history = generateSequence(firstDay) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it < today }
            .map { day -> MlHistoryPoint(day = day.toString(), demand = demandByDay[day] ?: 0.0) }
            .toList()

        return SeriesWithUnit(
            history = MlSeriesHistory(branchId = branchId.toString(), productType = productId.toString(), history = history),
            soldBy = soldBy,
        )
    }

    private companion object {
        /**
         * ~3 months of history per series (08 §5's sizing note). Enough to fully populate the
         * model's longest lag (28 days) many times over while keeping the request payload small.
         */
        const val HISTORY_DAYS = 90

        /** 7 days: the operational "how much do I bake this week?", and the trained horizon. */
        const val HORIZON_DAYS = 7

        /** Keeps one ML request to ~50 x 90 history points however many products a branch sells. */
        const val MAX_SERIES_PER_REQUEST = 50
    }
}
