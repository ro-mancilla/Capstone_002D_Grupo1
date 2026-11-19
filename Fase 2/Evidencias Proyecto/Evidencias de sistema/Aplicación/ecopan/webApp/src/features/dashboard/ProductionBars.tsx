import { formatGramsAsKg } from "@/lib/format"
import { cn } from "@/lib/utils"
import { t } from "@/lib/i18n"
import type { ProductBar } from "./productionSegments"

export interface BarSeries {
  key: string
  label: string
}

/** 45-degree white stripes: tells the second series apart without colour (design 3a). */
const HATCH =
  "repeating-linear-gradient(45deg, rgb(255 255 255 / 0.22) 0 4px, transparent 4px 8px)"

/**
 * Horizontal stacked bars of dough kilos, one row per product, most dough first. Each segment is
 * a branch and prints its own value, so the chart reads in greyscale; the second branch is also
 * hatched. A tick marks the previous period's total for the same product. No Y axis: the total
 * sits at the right of the row.
 */
export function ProductionBars({
  bars,
  series,
  dayCount,
  branchLabel,
  productLabel,
  highlightProductId,
}: {
  bars: ProductBar[]
  series: BarSeries[]
  dayCount: number
  branchLabel: (seriesKey: string) => string
  productLabel: (name: string) => string
  highlightProductId?: string
}) {
  const strings = t.dashboard.production
  const peak = Math.max(1, ...bars.map((bar) => Math.max(bar.totalGrams, bar.previousGrams ?? 0)))
  const scale = peak * 1.05
  const percent = (grams: number) => `${(grams / scale) * 100}%`
  const colorOf = (key: string) => `var(--chart-${Math.min(series.findIndex((s) => s.key === key), 4) + 1})`
  const showLegend = series.length > 1
  const hasPrevious = bars.some((bar) => bar.previousGrams != null)

  return (
    <div className="flex flex-col gap-3">
      {(showLegend || hasPrevious) && (
        <ul className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
          {showLegend &&
            series.map((s, index) => (
              <li key={s.key} className="flex items-center gap-2">
                <span
                  aria-hidden="true"
                  className="size-3 rounded-[3px]"
                  style={{
                    backgroundColor: colorOf(s.key),
                    backgroundImage: index === 1 ? HATCH : undefined,
                  }}
                />
                {s.label}
              </li>
            ))}
          {hasPrevious && (
            <li className="text-ink-muted flex items-center gap-2">
              <span aria-hidden="true" className="bg-ink-subtle h-4 w-0.5" />
              {strings.previousLegend(dayCount)}
            </li>
          )}
        </ul>
      )}

      <ul className="flex flex-col">
        {bars.map((bar) => (
          <li
            key={bar.productId}
            data-highlight={bar.productId === highlightProductId ? "true" : undefined}
            className={cn(
              "grid min-h-10 items-center gap-x-4 gap-y-1 rounded-md py-1 md:grid-cols-[180px_1fr_200px]",
              bar.productId === highlightProductId && "bg-peach-soft"
            )}
          >
            <span className="truncate text-sm font-medium">{productLabel(bar.productName)}</span>
            <div className="relative flex h-7 items-center">
              <div className="flex h-7 w-full">
                {bar.segments.map((segment, index) => {
                  const seriesIndex = series.findIndex((s) => s.key === segment.seriesKey)
                  const first = index === 0
                  const last = index === bar.segments.length - 1
                  const label = strings.barTooltip(
                    productLabel(bar.productName),
                    branchLabel(segment.seriesKey),
                    formatGramsAsKg(segment.grams),
                    segment.batchCount
                  )
                  return (
                    <div
                      key={segment.seriesKey}
                      role="img"
                      aria-label={label}
                      title={label}
                      className={cn(
                        "flex h-7 items-center justify-center overflow-hidden text-xs font-semibold whitespace-nowrap text-white tabular-nums",
                        first && "rounded-l-md",
                        last && "rounded-r-md"
                      )}
                      style={{
                        width: percent(segment.grams),
                        backgroundColor: colorOf(segment.seriesKey),
                        backgroundImage: seriesIndex === 1 ? HATCH : undefined,
                      }}
                    >
                      {formatGramsAsKg(segment.grams)}
                    </div>
                  )
                })}
              </div>
              {bar.previousGrams != null && (
                <span
                  data-testid="previous-mark"
                  title={strings.previousMark(formatGramsAsKg(bar.previousGrams))}
                  className="bg-ink-subtle absolute -top-1 -bottom-1 w-0.5"
                  style={{ left: percent(bar.previousGrams) }}
                />
              )}
            </div>
            <span className="text-sm tabular-nums">
              <span className="font-semibold">{strings.barTotal(formatGramsAsKg(bar.totalGrams))}</span>
              <span className="text-ink-muted"> · {strings.barBatches(bar.batchCount)}</span>
            </span>
          </li>
        ))}
      </ul>
      <p className="text-ink-muted text-xs">{series.length > 1 ? `${strings.chartCaption} ${strings.chartCaptionStriped}` : strings.chartCaption}</p>
    </div>
  )
}
