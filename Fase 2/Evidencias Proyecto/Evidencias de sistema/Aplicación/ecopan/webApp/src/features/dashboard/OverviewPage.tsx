import { useMemo } from "react"
import { useQuery } from "@tanstack/react-query"
import { Link } from "@tanstack/react-router"
import { AlertTriangle, Receipt, Scale, Wallet } from "lucide-react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Badge } from "@/components/ui/badge"
import { TimeSeriesChart } from "@/components/charts/TimeSeriesChart"
import { productStockLevelsQuery } from "@/api/queries/inventory"
import { salesByDayQuery, stockLevelsAnalyticsQuery, SSE_POLLING_FALLBACK } from "@/api/queries/kpis"
import { useAuth } from "@/features/auth/AuthContext"
import { hasModule } from "@/lib/entitlements"
import { can } from "@/lib/roles"
import { formatCurrencyMinor, formatGramsAsKg, formatUnits } from "@/lib/format"
import { t } from "@/lib/i18n"
import { DashboardFilterBar } from "./DashboardFilterBar"
import { LowStockCard } from "./LowStockCard"
import { StatCard } from "./StatCard"
import { useDashboardFilters, presetRange } from "./filters"
import { fillDaysTotals, sumDayTotals, totalsByDate } from "./aggregate"
import { addDays, reportingDate, reportingDays, startOfReportingDay } from "@/lib/reportingDay"

const SPARKLINE_DAYS = 7

function percentDelta(current: number, previous: number): number | undefined {
  if (previous === 0) return current === 0 ? 0 : undefined
  return ((current - previous) / previous) * 100
}

/** `dashboard/` overview (plan 07 §4): KPI stat cards (today's revenue/tx-count/kg-sold vs.
 * yesterday), a low-stock count, and a 7-day revenue area chart. The low-stock card counts
 * ingredients (only with `INVENTORY_FORMULAS` on) *and* Almacén products (always: product stock is
 * core catalog), and says how many of each so the two are never blurred.
 *
 * Laid out on shadcn's `dashboard-01` anatomy: a container-queried four-up card row over a full
 * width chart card. The grid breaks on `@container/main` (declared by `AppShell`), not on the
 * viewport, so collapsing the sidebar re-flows the cards -- with viewport breakpoints the row
 * stays stubbornly 2-up on a wide screen with an open sidebar. */
export function OverviewPage() {
  const { session } = useAuth()
  const strings = t.dashboard.overview
  const branches = session?.branches ?? []
  const showBranchSelect = can(session?.role, "SELECT_BRANCH") && branches.length > 1
  const { filters, setBranchId, setPreset, setCustomRange } = useDashboardFilters(
    session?.branchId ?? undefined
  )

  // Memoized (computed once per mount): `presetRange` derives `to` from `new Date()`, so calling
  // it fresh on every render would change these queries' keys every render and cause an infinite
  // refetch loop instead of a stable "today"/"yesterday"/"last 7 days" for the page's lifetime.
  const { today, yesterdayRange, sparklineRange } = useMemo(() => {
    const todayRange = presetRange("today")
    return {
      today: todayRange,
      yesterdayRange: { from: todayRange.from - 86_400_000, to: todayRange.from },
      // Seven reporting-zone calendar days ending today. The backend buckets sales into
      // reporting-zone days, so the browser's own midnight would drop or shift a day.
      sparklineRange: {
        from: startOfReportingDay(addDays(reportingDate(todayRange.to), -(SPARKLINE_DAYS - 1))),
        to: todayRange.to,
      },
    }
  }, [])

  const todayResult = useQuery(salesByDayQuery({ branchId: filters.branchId, ...today }))
  const yesterdayResult = useQuery(salesByDayQuery({ branchId: filters.branchId, ...yesterdayRange }))
  const sparklineResult = useQuery({
    ...salesByDayQuery({ branchId: filters.branchId, ...sparklineRange }),
    ...SSE_POLLING_FALLBACK,
  })

  const stockEnabled = hasModule(session, "INVENTORY_FORMULAS")
  const stockBranchId = filters.branchId ?? session?.branchId ?? branches[0]?.id
  const stockResult = useQuery({
    ...stockLevelsAnalyticsQuery(stockBranchId ?? ""),
    enabled: stockEnabled && Boolean(stockBranchId),
    ...SSE_POLLING_FALLBACK,
  })

  // Almacén product stock is not behind a module, so it is always asked for.
  const productStockResult = useQuery({
    ...productStockLevelsQuery(stockBranchId ?? ""),
    enabled: Boolean(stockBranchId),
    ...SSE_POLLING_FALLBACK,
  })

  const todayTotals = sumDayTotals(totalsByDate(todayResult.data ?? []))
  const yesterdayTotals = sumDayTotals(totalsByDate(yesterdayResult.data ?? []))
  const revenueDelta = percentDelta(todayTotals.revenueMinor, yesterdayTotals.revenueMinor)
  const countDelta = percentDelta(todayTotals.salesCount, yesterdayTotals.salesCount)
  const weightDelta = percentDelta(todayTotals.weightGrams, yesterdayTotals.weightGrams)

  const sparklineData = fillDaysTotals(
    reportingDays(sparklineRange.to, SPARKLINE_DAYS),
    totalsByDate(sparklineResult.data ?? [])
  )
  const branchName = filters.branchId ? branches.find((branch) => branch.id === filters.branchId)?.name : undefined
  const currencyCode = session?.currencyCode ?? "CLP"

  const stockLevels = stockResult.data ?? []
  // A retired product keeps its stock row for history but is no longer sold, so it needs no restock.
  const productLevels = (productStockResult.data ?? []).filter((level) => level.active)
  const lowIngredientCount = stockLevels.filter((level) => level.lowStock || level.negativeStock).length
  const lowProductCount = productLevels.filter((level) => level.lowStock || level.currentUnits <= 0).length
  const negativeStock = stockLevels.filter((level) => level.negativeStock)
  const negativeProducts = productLevels.filter((level) => level.currentUnits < 0)

  const isLoading = todayResult.isLoading || yesterdayResult.isLoading
  // The same 7-day revenue series the chart below plots, reused as a shape cue inside the revenue
  // tile -- one fetch, two readings.
  const revenueTrend = sparklineData.map((day) => day.revenueMinor)

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="grid gap-1">
          <span className="eyebrow">{strings.eyebrow}</span>
          <h1 className="page-title">{strings.title}</h1>
        </div>
        <DashboardFilterBar
          filters={filters}
          branches={branches}
          showBranchSelect={showBranchSelect}
          onBranchChange={setBranchId}
          onPresetChange={setPreset}
          onCustomRangeChange={setCustomRange}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 @xl/main:grid-cols-2 @5xl/main:grid-cols-4">
        <StatCard
          label={strings.salesTodayRevenue}
          value={formatCurrencyMinor(todayTotals.revenueMinor, currencyCode)}
          money
          deltaPercent={revenueDelta}
          deltaLabel={strings.vsYesterday}
          icon={Wallet}
          trend={revenueTrend}
          isLoading={isLoading}
        />
        <StatCard
          label={strings.salesTodayCount}
          value={String(todayTotals.salesCount)}
          deltaPercent={countDelta}
          deltaLabel={strings.vsYesterday}
          icon={Receipt}
          isLoading={isLoading}
        />
        <StatCard
          label={strings.salesTodayWeight}
          value={formatGramsAsKg(todayTotals.weightGrams)}
          deltaPercent={weightDelta}
          deltaLabel={strings.vsYesterday}
          icon={Scale}
          isLoading={isLoading}
        />
        <LowStockCard
          ingredients={lowIngredientCount}
          products={lowProductCount}
          negativeIngredients={negativeStock.length}
          negativeProducts={negativeProducts.length}
          ingredientsEnabled={stockEnabled}
          isLoading={(stockEnabled && stockResult.isLoading) || productStockResult.isLoading}
        />
      </div>

      {(negativeStock.length > 0 || negativeProducts.length > 0) && (
        <Card className="border-destructive/30 bg-destructive/5">
          <CardHeader>
            <CardTitle className="text-destructive flex items-center gap-2 text-sm font-medium">
              <AlertTriangle className="size-4" aria-hidden="true" />
              {strings.negativeStockTitle}
            </CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {negativeStock.map((level) => (
              <Badge
                key={level.ingredientId}
                variant="destructive"
                className="tabular-nums"
                render={<Link to="/catalog/ingredients" />}
              >
                {strings.negativeIngredient} · {level.name}: {formatGramsAsKg(level.currentGrams)}
              </Badge>
            ))}
            {negativeProducts.map((level) => (
              <Badge
                key={level.productId}
                variant="destructive"
                className="tabular-nums"
                render={<Link to="/catalog/products" />}
              >
                {strings.negativeProduct} · {level.name}: {formatUnits(level.currentUnits)}
              </Badge>
            ))}
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader className="border-b">
          <CardTitle className="font-display text-base font-semibold">
            {strings.sparklineTitle}
          </CardTitle>
          <CardDescription>{strings.sparklineSubtitle(branchName)}</CardDescription>
        </CardHeader>
        <CardContent className="px-2 sm:px-4">
          {sparklineResult.isLoading ? (
            <Skeleton className="h-[260px] w-full" />
          ) : (
            <TimeSeriesChart
              data={sparklineData}
              xKey="date"
              series={[{ key: "revenueMinor", label: strings.salesTodayRevenue }]}
              variant="area"
              height={260}
              valueFormatter={(value) => formatCurrencyMinor(value, currencyCode)}
            />
          )}
        </CardContent>
      </Card>
    </>
  )
}
