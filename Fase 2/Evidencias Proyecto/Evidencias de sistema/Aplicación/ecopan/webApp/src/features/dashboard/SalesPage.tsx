import { useQuery } from "@tanstack/react-query"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { TimeSeriesChart } from "@/components/charts/TimeSeriesChart"
import { GroupedBarChart } from "@/components/charts/GroupedBarChart"
import { salesByDayQuery, SSE_POLLING_FALLBACK } from "@/api/queries/kpis"
import { useAuth } from "@/features/auth/AuthContext"
import { can } from "@/lib/roles"
import { formatCurrencyMinor, formatGramsAsKg } from "@/lib/format"
import { t } from "@/lib/i18n"

/** Categorical palette slots available for stacked series -- see `index.css`'s `--chart-*`. */
const MAX_STACK_SERIES = 5
import { DashboardFilterBar } from "./DashboardFilterBar"
import { useDashboardFilters } from "./filters"
import { fillDayTotals, stackedRevenueByDayAndType, totalsByDate, totalsByProductType } from "./aggregate"

/** `dashboard/sales` (plan 07 §4): revenue-over-time (line/area) + revenue-by-product-type
 * (stacked bar) + a totals table -- weight-sold product types show kg, unit-sold show pieces
 * (both always feed `revenueMinor`, so the money side never depends on `soldBy`). This page is
 * the sales-KPI-rendering mandated test surface (plan 07 §10 #2). */
export function SalesPage() {
  const { session } = useAuth()
  const strings = t.dashboard.sales
  const branches = session?.branches ?? []
  const showBranchSelect = can(session?.role, "SELECT_BRANCH") && branches.length > 1
  const { filters, setBranchId, setPreset, setCustomRange } = useDashboardFilters(
    session?.branchId ?? undefined
  )
  const currencyCode = session?.currencyCode ?? "CLP"

  const salesResult = useQuery({
    ...salesByDayQuery({ branchId: filters.branchId, from: filters.from, to: filters.to, groupByProductType: true }),
    ...SSE_POLLING_FALLBACK,
  })

  const rows = salesResult.data ?? []
  const lineData = fillDayTotals(filters.from, filters.to, totalsByDate(rows))
  const days = lineData.map((row) => row.date)

  // The categorical palette has five slots and is never cycled: a sixth product type would reuse
  // slot 1's ember and read as the first type all over again. So the five biggest sellers keep
  // their own colour and the tail folds into one "Otros" band -- which is also the more useful
  // chart, since a stack of fifteen 2%-slivers communicates nothing. Slots are assigned
  // alphabetically rather than by rank so a colour stays attached to a product type as the date
  // filter moves, instead of the survivors repainting whenever the ordering shifts.
  const revenueByType = new Map<string, number>()
  for (const row of rows) {
    const type = row.productType ?? "—"
    revenueByType.set(type, (revenueByType.get(type) ?? 0) + row.revenueMinor)
  }
  const byRevenue = [...revenueByType.entries()].sort((a, b) => b[1] - a[1]).map(([type]) => type)
  const folded = byRevenue.length > MAX_STACK_SERIES
  const kept = new Set(byRevenue.slice(0, MAX_STACK_SERIES))
  const otherLabel = t.dashboard.otherProductTypes

  const chartRows = folded
    ? rows.map((row) =>
        kept.has(row.productType ?? "—") ? row : { ...row, productType: otherLabel }
      )
    : rows
  const productTypes = folded
    ? [...byRevenue.slice(0, MAX_STACK_SERIES).sort(), otherLabel]
    : byRevenue.sort()

  const stackedData = stackedRevenueByDayAndType(chartRows, days, productTypes)
  const typeTotals = totalsByProductType(rows)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-2">
        <h1 className="page-title">{strings.title}</h1>
        <DashboardFilterBar
          filters={filters}
          branches={branches}
          showBranchSelect={showBranchSelect}
          onBranchChange={setBranchId}
          onPresetChange={setPreset}
          onCustomRangeChange={setCustomRange}
        />
      </div>

      {salesResult.isLoading ? (
        <Skeleton className="h-72 w-full" />
      ) : rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">{t.dashboard.empty}</p>
      ) : (
        <>
          <div>
            <h2 className="text-muted-foreground mb-2 text-sm">{strings.chartTitle}</h2>
            <TimeSeriesChart
              data={lineData}
              xKey="date"
              series={[{ key: "revenueMinor", label: strings.chartTitle }]}
              variant="area"
              valueFormatter={(value) => formatCurrencyMinor(value, currencyCode)}
            />
          </div>

          <div>
            <h2 className="text-muted-foreground mb-2 text-sm">{strings.byTypeChartTitle}</h2>
            <GroupedBarChart
              data={stackedData}
              xKey="date"
              series={productTypes.map((type) => ({
                key: type,
                label: type,
                stackId: "revenue",
              }))}
              valueFormatter={(value) => formatCurrencyMinor(value, currencyCode)}
            />
          </div>
        </>
      )}

      <div>
        <h2 className="text-muted-foreground mb-2 text-sm">{strings.tableTitle}</h2>
        {salesResult.isLoading ? (
          <Skeleton className="h-32 w-full" />
        ) : typeTotals.length === 0 ? (
          <p className="text-muted-foreground text-sm">{t.dashboard.empty}</p>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{strings.columns.productType}</TableHead>
                <TableHead>{strings.columns.revenue}</TableHead>
                <TableHead>{strings.columns.quantity}</TableHead>
                <TableHead>{strings.columns.salesCount}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {typeTotals.map((row) => (
                <TableRow key={row.productType}>
                  <TableCell>{row.productType}</TableCell>
                  <TableCell className="ds-price">{formatCurrencyMinor(row.revenueMinor, currencyCode)}</TableCell>
                  <TableCell>
                    {row.weightGrams > 0
                      ? formatGramsAsKg(row.weightGrams)
                      : row.units > 0
                        ? `${row.units} ${strings.soldByUnitSuffix}`
                        : strings.noQuantity}
                  </TableCell>
                  <TableCell>{row.salesCount}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
    </div>
  )
}
