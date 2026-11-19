import { useId, useMemo } from "react"
import { Area, AreaChart, CartesianGrid, Line, LineChart, XAxis, YAxis } from "recharts"
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { formatDayLabel, formatShortDayLabel } from "@/lib/format"
import { chartColor } from "./chartTheme"

export interface TimeSeriesSeries {
  key: string
  label: string
}

export interface TimeSeriesChartProps<T> {
  /** One row per x-axis tick (e.g. one per day) -- gaps must already be filled with zeros by the
   * caller (plan 07 §10 AC: "empty-day gaps rendered as zero/empty rather than silently
   * skipped") so every day in the requested range gets a tick, sold or not. */
  data: T[]
  xKey: string
  series: TimeSeriesSeries[]
  variant?: "line" | "area"
  height?: number
  valueFormatter?: (value: number) => string
  labelFormatter?: (label: string) => string
  /** Tooltip heading and x-axis tick labels; both default to es-CL days ("mié 8 oct") because
   * every x value here is a `yyyy-MM-dd` day. Raw ISO dates are never shown. */
  xTickFormatter?: (label: string) => string
  /** Segments between points are straight by default: a smoothed curve implies values between
   * days that were never measured. */
  curve?: "linear" | "monotone"
}

/**
 * Time-series chart on shadcn's chart primitives (`ChartContainer` + `ChartTooltipContent`),
 * replacing a hand-rolled `ResponsiveContainer`/`Tooltip` pair.
 *
 * What that buys, beyond looking like the rest of the design system: `ChartContainer` injects
 * per-series CSS variables (`--color-<key>`) from `config`, so a series' colour is declared once
 * and reused by its stroke, its gradient stops, its legend swatch and its tooltip dot -- the old
 * wrapper passed `chartColor(i)` to each of those by hand, which is exactly how a legend ends up
 * disagreeing with its line. The tooltip is also the shared one, so number formatting matches
 * every other chart in the app.
 *
 * Area fills are gradients (shadcn's `charts/area` recipe): opaque at the line, transparent at
 * the baseline. A flat 20% fill, which this used before, muddies overlapping series and hides
 * the crossing points that forecast-vs-actual exists to show.
 *
 * A legend appears from two series up; a single series is named by its card title instead, so a
 * one-line chart doesn't carry a legend box repeating the heading.
 */
export function TimeSeriesChart<T extends object>({
  data,
  xKey,
  series,
  variant = "line",
  height = 280,
  valueFormatter,
  labelFormatter = formatDayLabel,
  xTickFormatter = formatShortDayLabel,
  curve = "linear",
}: TimeSeriesChartProps<T>) {
  // Gradient ids must be unique per mounted chart: two charts on one page sharing `fillRevenue`
  // would both resolve to whichever mounted last.
  const gradientPrefix = useId().replace(/:/g, "")

  const config = useMemo<ChartConfig>(
    () =>
      Object.fromEntries(
        series.map((s, index) => [s.key, { label: s.label, color: chartColor(index) }])
      ),
    [series]
  )

  const ChartComponent = variant === "area" ? AreaChart : LineChart

  return (
    <ChartContainer config={config} style={{ height }} className="w-full">
      <ChartComponent data={data} margin={{ top: 8, right: 16, left: 0, bottom: 0 }}>
        {variant === "area" && (
          <defs>
            {series.map((s) => (
              <linearGradient
                key={s.key}
                id={`${gradientPrefix}-${s.key}`}
                x1="0"
                y1="0"
                x2="0"
                y2="1"
              >
                <stop offset="5%" stopColor={`var(--color-${s.key})`} stopOpacity={0.7} />
                <stop offset="95%" stopColor={`var(--color-${s.key})`} stopOpacity={0.05} />
              </linearGradient>
            ))}
          </defs>
        )}
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis
          dataKey={xKey}
          tickLine={false}
          axisLine={false}
          tickMargin={8}
          minTickGap={24}
          // A week of days gets every label (recharts otherwise drops the last one); a long range
          // thins out but keeps both ends.
          interval={data.length <= 8 ? 0 : "preserveStartEnd"}
          tickFormatter={xTickFormatter}
          fontSize={12}
        />
        <YAxis
          // Never let the axis dip below zero: revenue and volume are non-negative.
          domain={[0, "auto"]}
          tickLine={false}
          axisLine={false}
          fontSize={12}
          width={64}
          tickFormatter={valueFormatter}
        />
        <ChartTooltip
          cursor={{ strokeDasharray: "3 3" }}
          content={
            <ChartTooltipContent
              indicator="dot"
              labelFormatter={(label) => labelFormatter(String(label))}
              formatter={
                valueFormatter
                  ? (value, name) => (
                      <div className="flex w-full items-center justify-between gap-3">
                        <span className="text-muted-foreground">
                          {config[String(name)]?.label ?? String(name)}
                        </span>
                        <span className="font-medium tabular-nums">
                          {valueFormatter(Number(value))}
                        </span>
                      </div>
                    )
                  : undefined
              }
            />
          }
        />
        {series.map((s) =>
          variant === "area" ? (
            <Area
              key={s.key}
              type={curve}
              dataKey={s.key}
              name={s.key}
              stroke={`var(--color-${s.key})`}
              fill={`url(#${gradientPrefix}-${s.key})`}
              strokeWidth={2}
              // A 2px surface ring keeps overlapping fills legible where they cross.
              activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--color-card)" }}
            />
          ) : (
            <Line
              key={s.key}
              type={curve}
              dataKey={s.key}
              name={s.key}
              stroke={`var(--color-${s.key})`}
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--color-card)" }}
            />
          )
        )}
        {series.length > 1 && <ChartLegend content={<ChartLegendContent />} />}
      </ChartComponent>
    </ChartContainer>
  )
}
