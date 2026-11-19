import { useId } from "react"
import type { LucideIcon } from "lucide-react"
import { TrendingDown, TrendingUp } from "lucide-react"
import { Area, AreaChart } from "recharts"
import {
  Card,
  CardAction,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { ChartContainer, type ChartConfig } from "@/components/ui/chart"
import { cn } from "@/lib/utils"
import { Skeleton } from "@/components/ui/skeleton"

const DELTA_FORMAT = new Intl.NumberFormat("es-CL", {
  maximumFractionDigits: 1,
  signDisplay: "always",
})

const SPARK_CONFIG = {
  v: { label: "", color: "var(--chart-1)" },
} satisfies ChartConfig

export interface StatCardProps {
  label: string
  value: string
  /** Percent vs. a comparison period (e.g. yesterday) -- omit when there's nothing to compare
   * against yet. */
  deltaPercent?: number
  deltaLabel?: string
  /** Small leading glyph. Identifies the metric at a glance in a row of four near-identical
   * tiles, where the label alone needs a beat to read. */
  icon?: LucideIcon
  /** Optional trailing values for the footer sparkline. A tile is a *headline* first: this is a
   * shape cue for the recent trend, never a chart to read values off, so it has no axes, no
   * tooltip and no grid -- see `sparkline` in the chart-form guidance. */
  trend?: number[]
  /** Marks the value as money, so it renders in `--peach-price`. Peach carries money and only
   * money in this system — the One Ration Rule — which is what lets a customer-facing
   * figure be found on a wall of tiles without reading the label beside it. */
  money?: boolean
  isLoading?: boolean
}

/**
 * One KPI tile, on shadcn's `section-cards` anatomy (dashboard-01).
 *
 * Three things carry it over the plain card this replaced: the delta is a `CardAction` badge
 * pinned to the header's top-right rather than a loose span under the number, the value is
 * container-queried (`@[220px]/card:text-3xl`) so a four-up row and a two-up row both look
 * deliberate, and the number is `tabular-nums` so live SSE refreshes don't reflow the digits.
 *
 * Direction is never colour-alone: the badge pairs its tint with an up/down arrow *and* a signed
 * number, so it survives a colourblind reader and a greyscale print.
 */
export function StatCard({
  label,
  value,
  deltaPercent,
  deltaLabel,
  icon: Icon,
  trend,
  money = false,
  isLoading,
}: StatCardProps) {
  const hasDelta = deltaPercent !== undefined && !isLoading
  const up = (deltaPercent ?? 0) >= 0
  const TrendIcon = up ? TrendingUp : TrendingDown
  const sparkData = trend?.map((v, i) => ({ i, v }))
  // Unique per instance: a fixed id would collide as soon as a second tile on the page passes a
  // `trend`, and both gradients would resolve to whichever mounted last.
  const gradientId = `spark-${useId().replace(/:/g, "")}`

  // Flat: no gradient. Depth here is the card's own fill against the flour page plus a hairline,
  // which is the whole depth model of this system.
  return (
    <Card className="@container/card gap-3">
      <CardHeader>
        <CardDescription className="flex items-center gap-1.5">
          {Icon && <Icon className="size-3.5 opacity-70" aria-hidden="true" />}
          {label}
        </CardDescription>
        {isLoading ? (
          <Skeleton className="mt-1 h-8 w-28" />
        ) : (
          // `text-*` is a utility and utilities outrank the `.ds-price` component class, so the
          // money tone is applied as one here rather than via `.ds-price`.
          <CardTitle
            className={cn(
              "ds-numeric text-[28px] @[220px]/card:text-[32px]",
              money && "text-peach-price"
            )}
          >
            {value}
          </CardTitle>
        )}
        {hasDelta && (
          <CardAction>
            {/* `success`/`destructive` tones, not `--chart-5`. The chart slots are a fixed-order
                identity palette validated to 3:1 for marks; borrowing the olive for "up" tied
                every delta to a palette that exists for a different purpose, and as text on the
                dark card it measured 3.72:1 — under the 4.5:1 AA floor. */}
            <Badge
              variant={up ? "success" : "destructive"}
              className="gap-1 tabular-nums"
            >
              <TrendIcon aria-hidden="true" />
              {DELTA_FORMAT.format(deltaPercent)}%
            </Badge>
          </CardAction>
        )}
      </CardHeader>

      {(deltaLabel || sparkData) && !isLoading && (
        <CardFooter className="flex-col items-stretch gap-2">
          {sparkData && sparkData.length > 1 && (
            <ChartContainer
              config={SPARK_CONFIG}
              className="pointer-events-none h-8 w-full [&_.recharts-surface]:overflow-visible"
            >
              <AreaChart data={sparkData} margin={{ top: 2, right: 0, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="var(--color-v)" stopOpacity={0.35} />
                    <stop offset="100%" stopColor="var(--color-v)" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <Area
                  dataKey="v"
                  type="monotone"
                  stroke="var(--color-v)"
                  strokeWidth={1.5}
                  fill={`url(#${gradientId})`}
                  isAnimationActive={false}
                  dot={false}
                />
              </AreaChart>
            </ChartContainer>
          )}
          {deltaLabel && (
            <span className="text-muted-foreground text-xs">{deltaLabel}</span>
          )}
        </CardFooter>
      )}
    </Card>
  )
}
