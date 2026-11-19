import { useQuery } from "@tanstack/react-query"
import { Badge } from "@/components/ui/badge"
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
import { SSE_POLLING_FALLBACK, wasteEstimateQuery } from "@/api/queries/kpis"
import { useAuth } from "@/features/auth/AuthContext"
import { can } from "@/lib/roles"
import { formatGramsAsKg } from "@/lib/format"
import { t } from "@/lib/i18n"
import { DashboardFilterBar } from "./DashboardFilterBar"
import { useDashboardFilters } from "./filters"
import { totalWasteGrams, wasteByDay } from "./waste"

/** `dashboard/waste` (HU-ANA-0005): bread produced and not sold, per day and product type. Every
 * figure here is an estimate and the page says so beside the numbers, not in a tooltip: production
 * is logged as dough weight and sold as finished bread, so the difference overstates the loss. */
export function WastePage() {
  const { session } = useAuth()
  const strings = t.dashboard.waste
  const branches = session?.branches ?? []
  const showBranchSelect = can(session?.role, "SELECT_BRANCH") && branches.length > 1
  const { filters, setBranchId, setPreset, setCustomRange } = useDashboardFilters(
    session?.branchId ?? undefined
  )

  const result = useQuery({
    ...wasteEstimateQuery({ branchId: filters.branchId, from: filters.from, to: filters.to }),
    ...SSE_POLLING_FALLBACK,
  })

  const rows = result.data ?? []
  const chartData = wasteByDay(rows).map((day) => ({ date: day.date, wasteGrams: day.wasteGrams / 1000 }))

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

      <p className="text-muted-foreground text-sm" role="note">
        {strings.estimateNotice}
      </p>

      {result.isLoading ? (
        <Skeleton className="h-72 w-full" />
      ) : rows.length === 0 ? (
        <p className="text-muted-foreground text-sm">{t.dashboard.empty}</p>
      ) : (
        <>
          <div className="flex items-baseline gap-3">
            <span className="text-muted-foreground text-sm">{strings.totalLabel}</span>
            <span className="text-2xl font-semibold">{formatGramsAsKg(totalWasteGrams(rows))}</span>
            <Badge variant="secondary">{strings.estimatedBadge}</Badge>
          </div>

          <div>
            <h2 className="text-muted-foreground mb-2 text-sm">{strings.chartTitle}</h2>
            <TimeSeriesChart
              data={chartData}
              xKey="date"
              series={[{ key: "wasteGrams", label: strings.chartTitle }]}
              variant="area"
              valueFormatter={(value) => `${value.toLocaleString("es-CL", { maximumFractionDigits: 1 })} kg`}
            />
          </div>

          <div>
            <h2 className="text-muted-foreground mb-2 text-sm">{strings.tableTitle}</h2>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{strings.columns.date}</TableHead>
                  <TableHead>{strings.columns.productType}</TableHead>
                  <TableHead>{strings.columns.produced}</TableHead>
                  <TableHead>{strings.columns.sold}</TableHead>
                  <TableHead>{strings.columns.waste}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={`${row.date}-${row.productType}`}>
                    <TableCell>{row.date}</TableCell>
                    <TableCell>{row.productType}</TableCell>
                    <TableCell>{formatGramsAsKg(row.producedGrams)}</TableCell>
                    <TableCell>
                      {row.soldGrams > 0 || row.soldUnits === 0
                        ? formatGramsAsKg(row.soldGrams)
                        : `${row.soldUnits} ${t.dashboard.sales.soldByUnitSuffix}`}
                    </TableCell>
                    <TableCell>
                      {row.estimatedWasteGrams === null ? (
                        <span className="text-muted-foreground">{strings.soldByUnit}</span>
                      ) : (
                        formatGramsAsKg(row.estimatedWasteGrams)
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </>
      )}
    </div>
  )
}
