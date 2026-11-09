import { useQuery } from "@tanstack/react-query"
import { Skeleton } from "@/components/ui/skeleton"
import { Badge } from "@/components/ui/badge"
import { forecastsQuery } from "@/api/queries/forecast"
import { SSE_POLLING_FALLBACK } from "@/api/queries/kpis"
import type { ForecastDto } from "@/api/types"
import { formatSuggestedKg, formatUnits } from "@/lib/format"
import { t } from "@/lib/i18n"
import { modelKindLabel } from "@/lib/labels"
import { EstimatorChip } from "./EstimatorChip"
import { fullStamp, longDayLabel } from "./forecastFormat"
import { reportingDayStart } from "@/lib/reportingTime"
import { modelKind } from "./forecastSeries"

const HOUR_MS = 3_600_000

/**
 * Noon of the reporting-zone day after the one containing [now]. The backend maps both bounds to a
 * reporting date, and midnight + 36 h is clear of any DST shift, so this is "tomorrow" however the
 * zone's offset moves overnight.
 */
function tomorrowNoon(now: number): number {
  return reportingDayStart(now) + 36 * HOUR_MS
}

/**
 * *Producción sugerida para mañana* (HU-PRE-0001): tomorrow's expected sales per Panadería
 * product in its sales unit -- not dough weight, not an automatic batch. Reads the d+1 rows of the
 * forecast cache (F1), grouped by unit (kg / units) and ordered by quantity. The estimator is
 * said once in the header and per row only when it differs; a baseline row never says *Modelo*.
 * [matches] applies the page's type/product filters.
 */
export function SuggestedProduction({
  branchId,
  matches,
}: {
  branchId: string
  matches: (row: ForecastDto) => boolean
}) {
  const strings = t.dashboard.forecast.suggested
  // Stable within a reporting day, so the query key only changes at midnight (the page re-renders
  // then, via useReportingToday).
  const day = tomorrowNoon(Date.now())
  const result = useQuery({
    ...forecastsQuery({ branchId, from: day, to: day }),
    enabled: Boolean(branchId),
    ...SSE_POLLING_FALLBACK,
  })

  const rows = (result.data ?? []).filter(matches)
  const quantity = (row: ForecastDto) => row.predictedGrams ?? row.predictedUnits ?? 0
  const groups = [
    { key: "WEIGHT", label: strings.groupWeight, rows: rows.filter((r) => r.predictedGrams != null) },
    { key: "UNIT", label: strings.groupUnits, rows: rows.filter((r) => r.predictedGrams == null) },
  ]
    .map((group) => ({ ...group, rows: group.rows.sort((a, b) => quantity(b) - quantity(a)) }))
    .filter((group) => group.rows.length > 0)
  const kinds = new Set(rows.map((row) => modelKind(row.modelVersion)))
  const shared = kinds.size === 1 ? [...kinds][0] : null
  const latest = rows.length > 0 ? Math.max(...rows.map((row) => row.generatedAt)) : null

  return (
    <section
      className="bg-card border-border flex flex-col gap-4 rounded-3xl border p-6 shadow-sm"
      data-testid="suggested-production"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="text-lg font-medium">{strings.titleDated(longDayLabel(day))}</h2>
          <p className="text-muted-foreground text-sm">{strings.description}</p>
        </div>
        {rows.length > 0 && (
          <div className="flex flex-wrap gap-1.5">
            <Badge variant="info">{strings.salesUntilYesterday}</Badge>
            <Badge variant="secondary">
              {shared ? strings.estimatorAll(modelKindLabel(shared)) : strings.estimatorMixed}
            </Badge>
          </div>
        )}
      </div>
      {result.isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : result.isError ? (
        <p className="text-destructive text-sm">{strings.error}</p>
      ) : rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">{strings.empty}</p>
      ) : (
        <>
          <div className="grid gap-x-7 gap-y-4 md:grid-cols-2">
            {groups.map((group) => (
              <div key={group.key} className="flex flex-col" data-testid={`suggested-${group.key}`}>
                <div className="text-muted-foreground border-border flex justify-between border-b pb-2 text-xs font-medium">
                  <span>{group.label}</span>
                  <span>{strings.expected}</span>
                </div>
                {group.rows.map((row) => (
                  <div
                    key={row.id}
                    className="border-border grid min-h-12 grid-cols-[minmax(0,1fr)_auto] items-center gap-3 border-b py-1 last:border-b-0"
                  >
                    <div className="flex min-w-0 flex-col gap-1">
                      <span className="truncate" title={row.productName}>
                        {row.productName}
                      </span>
                      {!shared && <EstimatorChip kind={modelKind(row.modelVersion)} />}
                    </div>
                    <span className="text-xl font-semibold tabular-nums">
                      {row.predictedGrams != null
                        ? formatSuggestedKg(row.predictedGrams)
                        : formatUnits(row.predictedUnits ?? 0)}
                    </span>
                  </div>
                ))}
              </div>
            ))}
          </div>
          {latest != null && <p className="text-muted-foreground text-xs">{strings.footer(fullStamp(latest))}</p>}
        </>
      )}
    </section>
  )
}
