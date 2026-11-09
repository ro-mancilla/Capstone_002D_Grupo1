import { useState } from "react"
import { Button } from "@/components/ui/button"
import { t } from "@/lib/i18n"
import { modelKindLabel, type ModelKind } from "@/lib/labels"
import { AccuracyBlock } from "./AccuracyBlock"
import { EstimatorChip } from "./EstimatorChip"
import { ForecastBars } from "./ForecastBars"
import type { ForecastAccuracy } from "./forecastAccuracy"
import type { ForecastUnitGroup } from "./forecastSeries"

export type AccuracyStatus = "loading" | "error" | "ready"

/** Charts shown before "Ver N productos más"; the rest are one click away. */
const INITIAL_CHARTS = 6

/** The estimator every series of the group shares, or null when it differs by product. */
function sharedKind(group: ForecastUnitGroup): ModelKind | null {
  const kinds = new Set<ModelKind>()
  for (const series of group.series) {
    const seen = group.models[series.productId] ?? []
    if (seen.length !== 1) return null
    kinds.add(seen[0])
  }
  return kinds.size === 1 ? [...kinds][0] : null
}

/** One unit's small-multiple charts (kg or units -- never both), each product on its own scale, with the
 * last-4-weeks accuracy measured on the bakery's own sales. [accuracy] must be computed from the same
 * product selection as [group]. The estimator is said once in the header; a chart carries its own
 * chip only when it differs from the group. */
export function ForecastUnitSection({
  group,
  accuracy,
  accuracyStatus,
  today,
  tomorrow,
}: {
  group: ForecastUnitGroup
  accuracy: ForecastAccuracy
  accuracyStatus: AccuracyStatus
  today: string
  tomorrow: string
}) {
  const strings = t.dashboard.forecast
  const [expanded, setExpanded] = useState(false)
  const shared = sharedKind(group)
  const bySales = (id: string) => group.points[id]?.find((p) => p.date === tomorrow)?.predicted ?? 0
  const ordered = [...group.series].sort((a, b) => bySales(b.productId) - bySales(a.productId))
  const visible = expanded ? ordered : ordered.slice(0, INITIAL_CHARTS)
  const hidden = ordered.length - visible.length

  const rows = group.series
    .flatMap((series) => {
      const value = accuracy.byProduct[series.productId]
      return value ? [{ productId: series.productId, name: series.name, value }] : []
    })
    .sort((a, b) => b.value.wape - a.value.wape)

  return (
    <section
      className="bg-card border-border flex flex-col gap-4 rounded-3xl border p-6 shadow-sm"
      data-testid={`forecast-section-${group.unit}`}
    >
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-medium">
          {group.unit === "WEIGHT" ? strings.chartTitleWeight : strings.chartTitleUnits}
        </h2>
        <p className="text-muted-foreground text-xs">
          {shared ? strings.suggested.estimatorAll(modelKindLabel(shared)) : strings.suggested.estimatorMixed}
        </p>
      </div>
      <p className="text-muted-foreground -mt-2 text-sm">{strings.chartsSubtitle}</p>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {visible.map((series) => (
          <ForecastBars
            key={series.productId}
            name={series.name}
            unit={group.unit}
            points={group.points[series.productId] ?? []}
            today={today}
            tomorrow={tomorrow}
            footer={
              shared ? undefined : (
                <div className="flex flex-wrap gap-1">
                  {(group.models[series.productId] ?? []).map((kind) => (
                    <EstimatorChip key={kind} kind={kind} />
                  ))}
                </div>
              )
            }
          />
        ))}
      </div>
      {(hidden > 0 || expanded) && ordered.length > INITIAL_CHARTS && (
        <div>
          <Button variant="secondary" size="sm" onClick={() => setExpanded((v) => !v)}>
            {expanded ? strings.showLess : strings.showMore(hidden)}
          </Button>
        </div>
      )}

      <div className="text-muted-foreground flex flex-wrap gap-4 text-xs">
        <span className="flex items-center gap-1.5">
          <span className="bg-chart-1 h-3.5 w-2.5 rounded-sm" />
          {strings.legend.sold}
        </span>
        <span className="flex items-center gap-1.5">
          <span className="border-chart-2 h-3.5 w-2.5 rounded-sm border-[1.5px] border-dashed" />
          {strings.legend.forecast}
        </span>
        <span className="flex items-center gap-1.5">
          <span className="bg-secondary border-border h-3.5 w-3.5 rounded-sm border" />
          {strings.legend.fromToday}
        </span>
      </div>
      <p className="text-muted-foreground text-xs">{strings.chartsCaption}</p>

      <AccuracyBlock status={accuracyStatus} overall={accuracy.overall[group.unit]} rows={rows} />
    </section>
  )
}
