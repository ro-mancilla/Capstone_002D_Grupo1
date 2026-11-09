import { queryOptions, useMutation } from "@tanstack/react-query"
import { http } from "@/api/http"
import type { ForecastDto, ForecastVsActualDto } from "@/api/types"

/**
 * K8 (02 §3 / plan 07 §9), `ANALYTICS_DASHBOARD` + `DEMAND_FORECASTING`-gated. `branchId` is
 * required by the backend (`BRANCH_ID_REQUIRED` 400 otherwise) -- unlike the other KPI endpoints
 * there is no "all branches" reading here. `ForecastVsActualDto` already joins the forecast cache
 * with actuals server-side (`AnalyticsServiceImpl.forecastVsActual`), one row set per Panadería
 * product (#51), so the chart overlay and accuracy need only this query.
 */
export interface ForecastVsActualFilter {
  branchId: string
  productType?: string
  from: number
  to: number
}

/** Prefix of every K8 forecast-vs-actual query key -- invalidate by this, never a retyped literal. */
export const FORECAST_VS_ACTUAL_KEY = ["kpi", "forecast-vs-actual"] as const

/** Prefix of every F1 `/forecasts`-backed query key (freshness stamp, per-product views). */
export const FORECASTS_KEY_PREFIX = ["forecasts"] as const

export function forecastVsActualQuery(filter: ForecastVsActualFilter) {
  const params = new URLSearchParams({
    branchId: filter.branchId,
    from: String(filter.from),
    to: String(filter.to),
  })
  if (filter.productType) params.set("productType", filter.productType)

  return queryOptions({
    queryKey: [
      ...FORECAST_VS_ACTUAL_KEY,
      { branchId: filter.branchId, productType: filter.productType, from: filter.from, to: filter.to },
    ] as const,
    queryFn: () => http.get<ForecastVsActualDto[]>(`/analytics/forecast-vs-actual?${params.toString()}`),
    staleTime: 0,
    retry: 1,
  })
}

/** F1 (`GET /forecasts`): read only for its `generatedAt`, which K8's joined rows don't carry --
 * the Pronóstico page's "Actualizado" line and the refresh button's "did new rows land" check.
 * Every refresh upserts today's row, so asking for just today (`from == to`) is enough -- and that
 * is also what tells this call apart from any other reader of `/forecasts` (a per-product view
 * asks for a multi-day range). The instant is taken per fetch, not baked into the key, so a tab
 * left open across midnight keeps asking for the current day. */
export function forecastGeneratedAtQuery(branchId: string) {
  return queryOptions({
    queryKey: [...FORECASTS_KEY_PREFIX, "generated-at", branchId] as const,
    queryFn: () => {
      const now = String(Date.now())
      const params = new URLSearchParams({ branchId, from: now, to: now })
      return http.get<ForecastDto[]>(`/forecasts?${params.toString()}`)
    },
    staleTime: 0,
    retry: 1,
  })
}

/**
 * F1 (`GET /forecasts`), `FORECAST_READ` + `DEMAND_FORECASTING`-gated: the forecast cache itself,
 * one row per (product, day). Read for the *Producción sugerida* table, which needs each row's
 * `generatedAt` -- K8 does not carry it. Both bounds are reporting-zone-mapped to a date by the
 * backend, so passing the same instant twice asks for that one day.
 */
export function forecastsQuery(filter: { branchId: string; from: number; to: number }) {
  const params = new URLSearchParams({
    branchId: filter.branchId,
    from: String(filter.from),
    to: String(filter.to),
  })
  return queryOptions({
    queryKey: [...FORECASTS_KEY_PREFIX, filter] as const,
    queryFn: () => http.get<ForecastDto[]>(`/forecasts?${params.toString()}`),
    staleTime: 0,
    retry: 1,
  })
}

/** F2 (`POST /forecasts/refresh`, `MODULES_TOGGLE`): 202, fire-and-forget -- the new rows arrive
 * asynchronously through the worker, so callers poll `forecastGeneratedAtQuery` rather than awaiting a result. */
export function useRequestForecastRefresh() {
  return useMutation({
    mutationFn: (body: { branchId: string; productType?: string }) =>
      http.post<void>("/forecasts/refresh", body),
  })
}
