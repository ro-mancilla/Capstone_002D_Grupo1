package com.f776.ecopan.data.database.repository

import com.f776.ecopan.data.database.repository.model.ProductionFact
import com.f776.ecopan.data.database.repository.model.SaleLineFact
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Raw fact fetch for the K1-K8 analytics endpoints (02 §3). Deliberately **not** SQL `GROUP BY
 * date_trunc(...)`: "day" bucketing is tenant-timezone-aware (`REPORTING_TZ`), and Postgres
 * `date_trunc(..., timestamp AT TIME ZONE ...)` semantics are easy to get subtly wrong (DST,
 * text-vs-interval zone args) without a live DB to iterate against -- fetching the tenant/branch/
 * time-range-filtered raw rows here and bucketing them in Kotlin (`AnalyticsService`, 02 §3) is a
 * few hundred rows at capstone scale and is exhaustively unit-testable without a database at all.
 * Documented deviation from the plan's literal "straight SQL aggregates" phrasing; the *filtering*
 * (tenant/branch/time-range) still happens in SQL via the `(tenant_id, branch_id, sold_at)` index.
 */
interface AnalyticsRepository {
    suspend fun saleLineFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<SaleLineFact>
    suspend fun productionFacts(tenantId: Uuid, branchId: Uuid?, from: Instant, to: Instant): List<ProductionFact>
}
