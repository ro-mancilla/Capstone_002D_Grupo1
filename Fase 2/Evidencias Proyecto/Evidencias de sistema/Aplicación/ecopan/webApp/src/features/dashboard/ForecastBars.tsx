import type { ReactNode } from "react"
import { formatSuggestedKg, formatUnits } from "@/lib/format"
import { t } from "@/lib/i18n"
import { axisLabel, chartDays } from "./forecastFormat"
import type { ForecastUnit, ProductPoint } from "./forecastSeries"

const FORMATTERS: Record<ForecastUnit, (value: number) => string> = {
  WEIGHT: formatSuggestedKg,
  UNIT: formatUnits,
}

/** A "nice" axis maximum just above [peak]. */
function niceMax(peak: number): number {
  if (peak <= 0) return 1
  const mag = 10 ** Math.floor(Math.log10(peak))
  return ([1, 2, 2.5, 5, 10].map((step) => step * mag).find((step) => step >= peak * 1.05) ?? peak)
}

/**
 * One product's 14 days as paired bars: sold (filled) next to forecast (dashed outline), today's
 * divider and a tinted future. Bars, not a line -- with a week of data and a strong weekly rhythm a
 * line implies a trend. Future days carry no sold bar (it never happened, so it is not 0); the
 * scale is the product's own.
 */
export function ForecastBars({
  name,
  unit,
  points,
  today,
  tomorrow,
  footer,
}: {
  name: string
  unit: ForecastUnit
  points: ProductPoint[]
  today: string
  tomorrow: string
  /** Per-chart extras, e.g. the estimator chips when they differ from the group header. */
  footer?: ReactNode
}) {
  const strings = t.dashboard.forecast
  const format = FORMATTERS[unit]
  const byDate = new Map(points.map((point) => [point.date, point]))
  const days = chartDays(today).map((date) => {
    const point = byDate.get(date)
    return { date, predicted: point?.predicted ?? null, actual: date > today || !point ? null : point.actual }
  })
  const max = niceMax(Math.max(...days.map((d) => Math.max(d.predicted ?? 0, d.actual ?? 0))))
  const tomorrowValue = byDate.get(tomorrow)?.predicted
  const height = (value: number) => `${Math.max(1, (value / max) * 100)}%`

  return (
    <div
      className="border-border flex min-w-0 flex-col gap-2 rounded-2xl border p-4"
      data-testid={`forecast-bars-${name}`}
    >
      <div className="flex items-baseline justify-between gap-2">
        <span className="truncate text-sm font-medium" title={name}>
          {name}
        </span>
        {tomorrowValue != null && (
          <span className="text-muted-foreground text-xs whitespace-nowrap">
            {strings.tomorrowLabel} <b className="text-foreground font-semibold tabular-nums">{format(tomorrowValue)}</b>
          </span>
        )}
      </div>
      <div
        role="img"
        aria-label={`${name}: ${strings.legend.sold} / ${strings.legend.forecast}`}
        className="border-border relative grid h-32 border-b"
        style={{ gridTemplateColumns: "repeat(14, minmax(0, 1fr))" }}
      >
        <span className="text-muted-foreground absolute top-0 left-0 text-[11px] tabular-nums">{format(max)}</span>
        {days.map((day) => (
          <div
            key={day.date}
            title={`${day.date}: ${day.actual != null ? `${strings.legend.sold} ${format(day.actual)}` : ""} ${
              day.predicted != null ? `${strings.legend.forecast} ${format(day.predicted)}` : ""
            }`.trim()}
            className={`relative flex items-end justify-center gap-0.5 pt-4 ${day.date >= today ? "bg-secondary" : ""} ${
              day.date === today ? "border-l-foreground/60 border-l-[1.5px]" : ""
            }`}
          >
            {day.date === today && (
              <span className="absolute top-0 left-1 z-10 text-[11px] font-semibold whitespace-nowrap">{strings.todayMark}</span>
            )}
            <div className="flex h-full items-end gap-0.5 self-stretch">
              {day.actual != null && (
                <div className="w-1.5 rounded-t-sm bg-chart-1" style={{ height: height(day.actual) }} />
              )}
              {day.predicted != null && (
                <div
                  className={`w-1.5 rounded-t-sm border-[1.5px] border-b-0 border-dashed border-chart-2 ${
                    day.date === tomorrow ? "bg-chart-2/15" : ""
                  }`}
                  style={{ height: height(day.predicted) }}
                />
              )}
            </div>
          </div>
        ))}
      </div>
      <div className="grid text-center text-[11px] leading-tight tabular-nums" style={{ gridTemplateColumns: "repeat(14, minmax(0, 1fr))" }}>
        {days.map((day) => {
          const label = axisLabel(day.date)
          return (
            <span
              key={day.date}
              className={day.date === tomorrow ? "text-foreground font-bold" : "text-muted-foreground"}
            >
              {label.weekday}
              <br />
              {label.day}
            </span>
          )
        })}
      </div>
      {footer}
    </div>
  )
}
