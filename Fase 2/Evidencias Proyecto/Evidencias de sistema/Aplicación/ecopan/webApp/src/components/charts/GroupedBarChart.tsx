import { useMemo } from "react"
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts"
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart"
import { chartColor } from "./chartTheme"

export interface BarSeries {
  key: string
  label: string
  /** Bars sharing a `stackId` stack instead of grouping side by side (plan 07 §4's "stacked bar
   * by product type"). Omit for grouped bars (e.g. the branch comparison's revenue-vs-count). */
  stackId?: string
}

export interface GroupedBarChartProps<T> {
  data: T[]
  xKey: string
  series: BarSeries[]
  height?: number
  valueFormatter?: (value: number) => string
  /** Tooltip-only override of `valueFormatter`, for when the axis wants the short form ("12 kg")
   * and the tooltip the full one ("12 kg de masa"). */
  tooltipValueFormatter?: (value: number) => string
  labelFormatter?: (label: string) => string
  /** Display text for an x value, for when `xKey` is an id and the tick should read as a name. */
  xTickFormatter?: (value: string) => string
  /** Every x value is a category that must be labelled (products), so no tick may be dropped to
   * avoid overlap -- unlike a date axis, where thinning ticks loses nothing. */
  categoricalX?: boolean
  /** Rotated title along the y axis -- carries the unit the ticks leave out. */
  yAxisLabel?: string
}

/**
 * Bar chart on shadcn's chart primitives, matching `TimeSeriesChart`'s theming exactly (same
 * `config`-driven `--color-<key>` variables, same shared tooltip, same legend rule: shown from
 * two series up).
 *
 * Two mark details worth keeping: stacked segments carry a 2px surface-coloured stroke so the
 * boundary between adjacent product types stays visible when their colours are close, and only
 * the topmost segment of a stack gets a rounded cap -- rounding every segment would imply each
 * one is a separate bar sitting on the axis.
 */
export function GroupedBarChart<T extends object>({
  data,
  xKey,
  series,
  height = 280,
  valueFormatter,
  tooltipValueFormatter,
  labelFormatter,
  xTickFormatter,
  categoricalX,
  yAxisLabel,
}: GroupedBarChartProps<T>) {
  const tooltipFormatter = tooltipValueFormatter ?? valueFormatter
  const config = useMemo<ChartConfig>(
    () =>
      Object.fromEntries(
        series.map((s, index) => [s.key, { label: s.label, color: chartColor(index) }])
      ),
    [series]
  )

  const lastStackedKey = [...series].reverse().find((s) => s.stackId)?.key

  return (
    <ChartContainer config={config} style={{ height }} className="w-full">
      <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
        <CartesianGrid vertical={false} strokeDasharray="3 3" />
        <XAxis
          dataKey={xKey}
          tickLine={false}
          axisLine={false}
          tickMargin={8}
          minTickGap={16}
          interval={categoricalX ? 0 : undefined}
          tickFormatter={xTickFormatter}
          fontSize={12}
        />
        <YAxis
          tickLine={false}
          axisLine={false}
          fontSize={12}
          width={yAxisLabel ? 80 : 64}
          tickFormatter={valueFormatter}
          label={
            yAxisLabel
              ? { value: yAxisLabel, angle: -90, position: "insideLeft", offset: 8, fontSize: 12 }
              : undefined
          }
        />
        <ChartTooltip
          cursor={{ fillOpacity: 0.08 }}
          content={
            <ChartTooltipContent
              indicator="dot"
              labelFormatter={labelFormatter ? (label) => labelFormatter(String(label)) : undefined}
              formatter={
                tooltipFormatter
                  ? (value, name) => (
                      <div className="flex w-full items-center justify-between gap-3">
                        <span className="text-muted-foreground">
                          {config[String(name)]?.label ?? String(name)}
                        </span>
                        <span className="font-medium tabular-nums">
                          {tooltipFormatter(Number(value))}
                        </span>
                      </div>
                    )
                  : undefined
              }
            />
          }
        />
        {series.map((s) => (
          <Bar
            key={s.key}
            dataKey={s.key}
            name={s.key}
            stackId={s.stackId}
            fill={`var(--color-${s.key})`}
            stroke={s.stackId ? "var(--color-card)" : undefined}
            strokeWidth={s.stackId ? 2 : 0}
            radius={s.stackId ? (s.key === lastStackedKey ? [4, 4, 0, 0] : 0) : [4, 4, 0, 0]}
          />
        ))}
        {series.length > 1 && <ChartLegend content={<ChartLegendContent />} />}
      </BarChart>
    </ChartContainer>
  )
}
