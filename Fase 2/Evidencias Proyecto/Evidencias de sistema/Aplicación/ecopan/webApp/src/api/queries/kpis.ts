import { queryOptions } from "@tanstack/react-query"
import { http } from "@/api/http"
import type {
  PaymentMethodDto,
  ProductMixDto,
  ProductionSummaryDto,
  RevenueByPeriodDto,
  SalesByDayDto,
  StockLevelWarningDto,
  WasteEstimateDto,
} from "@/api/types"

/**
 * K1-K7 (02 §3 / plan 07 §9), all `ANALYTICS_DASHBOARD`-gated (K5 also needs
 * `INVENTORY_FORMULAS`, enforced server-side and by the `dashboard/stock` route guard). Plan §2's
 * KPI defaults: `staleTime: 0` (always fresh on mount/invalidate -- SSE and manual filter changes
 * both need a real refetch, not a cache hit) and `retry: 1`.
 *
 * `branchId` absent from a filter means "all branches" (GM only, plan 07 §4) -- every endpoint
 * accepts an optional `branchId` and aggregates across branches server-side when it's omitted,
 * except K1/K6 which still return one row *per branch* even when unfiltered (`AnalyticsServiceImpl
 * .salesByDay`/`.productionSummary` group by the fact's own `branchId`, not the filter) --
 * `features/dashboard/*` pages aggregate those rows further client-side where needed (e.g. the
 * `branches` page groups K1 rows by `branchId` for its bar chart).
 */
export interface RangeFilter {
  branchId?: string
  from: number
  to: number
}

/** Every analytics query sits under this root, so a new batch can repaint them all at once. */
export const kpiKeys = {
  all: ["kpi"] as const,
}

const KPI_STALE_TIME = 0
const KPI_RETRY = 1

function withRange(filter: RangeFilter, extra?: Record<string, string>): URLSearchParams {
  const params = new URLSearchParams({ from: String(filter.from), to: String(filter.to) })
  if (filter.branchId) params.set("branchId", filter.branchId)
  if (extra) for (const [key, value] of Object.entries(extra)) params.set(key, value)
  return params
}

/** K1: sales-by-day, optionally grouped by product type (drives both the overview sparkline/
 * today-vs-yesterday cards and the `sales`/`branches` pages). */
export function salesByDayQuery(filter: RangeFilter & { groupByProductType?: boolean }) {
  const params = withRange(filter, filter.groupByProductType ? { groupBy: "productType" } : undefined)
  return queryOptions({
    queryKey: [
      ...kpiKeys.all,
      "sales-by-day",
      { branchId: filter.branchId, from: filter.from, to: filter.to, groupByProductType: Boolean(filter.groupByProductType) },
    ] as const,
    queryFn: () => http.get<SalesByDayDto[]>(`/analytics/sales-by-day?${params.toString()}`),
    staleTime: KPI_STALE_TIME,
    retry: KPI_RETRY,
  })
}

/** K2. */
export function revenueQuery(filter: RangeFilter & { granularity?: "day" | "week" | "month" }) {
  const params = withRange(filter, { granularity: filter.granularity ?? "day" })
  return queryOptions({
    queryKey: [
      ...kpiKeys.all,
      "revenue",
      { branchId: filter.branchId, from: filter.from, to: filter.to, granularity: filter.granularity ?? "day" },
    ] as const,
    queryFn: () => http.get<RevenueByPeriodDto[]>(`/analytics/revenue?${params.toString()}`),
    staleTime: KPI_STALE_TIME,
    retry: KPI_RETRY,
  })
}

/** K3. */
export function productMixQuery(filter: RangeFilter) {
  const params = withRange(filter)
  return queryOptions({
    queryKey: [...kpiKeys.all, "product-mix", { branchId: filter.branchId, from: filter.from, to: filter.to }] as const,
    queryFn: () => http.get<ProductMixDto[]>(`/analytics/product-mix?${params.toString()}`),
    staleTime: KPI_STALE_TIME,
    retry: KPI_RETRY,
  })
}

/** K5: I3's shape plus `negativeStock` (01 §6.4 reconciliation warning). Distinct query key from
 * `inventory.ts`'s `stockLevelsQuery` (`I3`, `/inventory/stock`) -- same underlying sums, but a
 * different endpoint/DTO/module-gate combination (K5 needs `ANALYTICS_DASHBOARD` too), so the two
 * caches are deliberately not shared. `branchId` is required by the backend when the caller has
 * no branch of their own (`BRANCH_ID_REQUIRED` 400) -- dashboard pages always resolve one before
 * calling this (session branch, selected branch, or the tenant's first branch). */
export function stockLevelsAnalyticsQuery(branchId: string) {
  return queryOptions({
    queryKey: [...kpiKeys.all, "stock-levels", { branchId }] as const,
    queryFn: () => http.get<StockLevelWarningDto[]>(`/analytics/stock-levels?branchId=${branchId}`),
    staleTime: KPI_STALE_TIME,
    retry: KPI_RETRY,
  })
}

/** K6. */
export function productionSummaryQuery(filter: RangeFilter) {
  const params = withRange(filter)
  return queryOptions({
    queryKey: [...kpiKeys.all, "production-summary", { branchId: filter.branchId, from: filter.from, to: filter.to }] as const,
    queryFn: () => http.get<ProductionSummaryDto[]>(`/analytics/production-summary?${params.toString()}`),
    staleTime: KPI_STALE_TIME,
    retry: KPI_RETRY,
  })
}

/** K9: estimated waste (produced minus sold) per day and product type. */
export function wasteEstimateQuery(filter: RangeFilter) {
  const params = withRange(filter)
  return queryOptions({
    queryKey: [...kpiKeys.all, "waste-estimate", { branchId: filter.branchId, from: filter.from, to: filter.to }] as const,
    queryFn: () => http.get<WasteEstimateDto[]>(`/analytics/waste-estimate?${params.toString()}`),
    staleTime: KPI_STALE_TIME,
    retry: KPI_RETRY,
  })
}

/** K7. */
export function paymentMethodsQuery(filter: RangeFilter) {
  const params = withRange(filter)
  return queryOptions({
    queryKey: [...kpiKeys.all, "payment-methods", { branchId: filter.branchId, from: filter.from, to: filter.to }] as const,
    queryFn: () => http.get<PaymentMethodDto[]>(`/analytics/payment-methods?${params.toString()}`),
    staleTime: KPI_STALE_TIME,
    retry: KPI_RETRY,
  })
}

/** Shared fallback for SSE-adjacent queries (plan 07 §6/§10 item 10): a dead `EventSource`
 * degrades to 60s polling instead of going silently stale. Spread into a query's options
 * (`useQuery({ ...someKpiQuery(filter), ...SSE_POLLING_FALLBACK })`) rather than copy-pasting the
 * interval per call site. */
export const SSE_POLLING_FALLBACK = { refetchInterval: 60_000 } as const
