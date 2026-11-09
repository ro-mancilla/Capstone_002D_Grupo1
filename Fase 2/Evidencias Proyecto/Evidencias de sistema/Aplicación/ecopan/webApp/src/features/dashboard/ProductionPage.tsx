import { useEffect, useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { useSearch } from "@tanstack/react-router"
import { Croissant, CookingPot } from "lucide-react"
import type { LucideIcon } from "lucide-react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
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
import { productionSummaryQuery, SSE_POLLING_FALLBACK } from "@/api/queries/kpis"
import { useAuth } from "@/features/auth/AuthContext"
import { can } from "@/lib/roles"
import { formatGramsAsKg } from "@/lib/format"
import { t } from "@/lib/i18n"
import { DashboardFilterBar } from "./DashboardFilterBar"
import { ProductionBars } from "./ProductionBars"
import { useProductionFilters } from "./productionFilters"
import { foldBranchSeries, totalsByProductAndBranch, type ProductBranchTotal } from "./productionAggregate"
import { productBars, summarize } from "./productionSegments"

const OTHER_KEY = "__other__"
/** How long a row stays marked after arriving from a "lote listo" notice. */
const HIGHLIGHT_MS = 4000

const SIGNED = new Intl.NumberFormat("es-CL", { maximumFractionDigits: 2, signDisplay: "exceptZero" })
// Kilos to the gram, like every other weight on the dashboard (formatGramsAsKg): one precision per page.
const KG_PLAIN = new Intl.NumberFormat("es-CL", { minimumFractionDigits: 3, maximumFractionDigits: 3 })
const SIGNED_KG = new Intl.NumberFormat("es-CL", { minimumFractionDigits: 3, maximumFractionDigits: 3, signDisplay: "exceptZero" })

function Kpi({
  icon: Icon,
  label,
  value,
  unit,
  delta,
  deltaNote,
}: {
  icon: LucideIcon
  label: string
  value: string
  unit: string
  delta?: string
  deltaNote?: string
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-ink-muted flex items-center gap-2 text-sm font-medium">
          <Icon className="size-4" aria-hidden="true" />
          {label}
        </CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-2">
        <p className="ds-numeric text-[40px] leading-none font-semibold tabular-nums">
          {value} <span className="text-2xl font-medium">{unit}</span>
        </p>
        {delta && (
          <p className="flex flex-wrap items-center gap-2 text-sm">
            <Badge variant="secondary" className="tabular-nums">
              {delta}
            </Badge>
            <span className="text-ink-muted">{deltaNote}</span>
          </p>
        )}
      </CardContent>
    </Card>
  )
}

/** `dashboard/production` (HU-ANA-0003): dough kilos per product and branch for the chosen
 * range, against the same number of days right before it. The number is the batch's *mix*
 * weight, so every place it appears says "masa". */
export function ProductionPage() {
  const { session } = useAuth()
  const strings = t.dashboard.production
  const search = useSearch({ strict: false }) as { branchId?: string; today?: boolean; highlight?: string }
  const branches = session?.branches ?? []
  const showBranchSelect = can(session?.role, "SELECT_BRANCH") && branches.length > 1
  const { filters, range, previousRange, dayCount, setBranchId, setPreset, setCustomRange } = useProductionFilters(
    search.branchId ?? session?.branchId ?? undefined,
    search.today ? "today" : "7d"
  )

  const [highlightId, setHighlightId] = useState<string | undefined>(search.highlight)
  useEffect(() => {
    if (!highlightId) return
    const timer = setTimeout(() => setHighlightId(undefined), HIGHLIGHT_MS)
    return () => clearTimeout(timer)
  }, [highlightId])

  const result = useQuery({
    ...productionSummaryQuery({ branchId: filters.branchId, from: range.from, to: range.to }),
    ...SSE_POLLING_FALLBACK,
  })
  // The comparison is a second range query: the endpoint has no "previous period" mode.
  const previousResult = useQuery({
    ...productionSummaryQuery({ branchId: filters.branchId, from: previousRange.from, to: previousRange.to }),
    ...SSE_POLLING_FALLBACK,
  })

  const branchName = (id: string) =>
    id === OTHER_KEY ? strings.otherBranches : (branches.find((branch) => branch.id === id)?.name ?? strings.otherBranch)

  const totals = totalsByProductAndBranch(result.data ?? [])
  const previousTotals = totalsByProductAndBranch(previousResult.data ?? [])
  const productLabel = (name: string) => name.trim() || strings.unnamedProduct
  const { seriesBranchIds } = foldBranchSeries(totals, OTHER_KEY)
  const bars = productBars(totals, seriesBranchIds, OTHER_KEY, previousTotals)
  const current = summarize(totals)
  const previous = summarize(previousTotals)
  const hasComparison = previousResult.isSuccess && previous.batchCount > 0
  const doughDelta = (current.doughGrams - previous.doughGrams) / 1000
  const batchDelta = current.batchCount - previous.batchCount

  const byBranch = new Map<string, ProductBranchTotal[]>()
  for (const row of totals) byBranch.set(row.branchId, [...(byBranch.get(row.branchId) ?? []), row])
  const branchGroups = [...byBranch.entries()]
    .map(([branchId, rows]) => ({
      branchId,
      rows: [...rows].sort((a, b) => b.mixWeightGrams - a.mixWeightGrams),
      grams: rows.reduce((sum, row) => sum + row.mixWeightGrams, 0),
    }))
    .sort((a, b) => b.grams - a.grams)

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="grid gap-1">
          <h1 className="page-title">{strings.title}</h1>
          <p className="text-ink-muted text-sm">{strings.doughNote}</p>
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

      {result.isLoading ? (
        <>
          <Skeleton className="h-32 w-full" />
          <Skeleton className="h-72 w-full" />
        </>
      ) : result.isError ? (
        <p className="text-destructive text-sm">{t.dashboard.loadError}</p>
      ) : totals.length === 0 ? (
        <p className="text-muted-foreground text-sm">{t.dashboard.empty}</p>
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-2">
            <Kpi
              icon={CookingPot}
              label={strings.kpiDough}
              value={KG_PLAIN.format(current.doughGrams / 1000)}
              unit={`kg ${strings.doughUnit}`}
              delta={hasComparison ? strings.deltaDough(`${SIGNED_KG.format(doughDelta)} kg`) : undefined}
              deltaNote={strings.versusPrevious(dayCount, formatGramsAsKg(previous.doughGrams))}
            />
            <Kpi
              icon={Croissant}
              label={strings.kpiBatches}
              value={String(current.batchCount)}
              unit={strings.batchesUnit(current.batchCount)}
              delta={hasComparison ? strings.deltaBatches(SIGNED.format(batchDelta), batchDelta) : undefined}
              deltaNote={strings.versusPrevious(dayCount, String(previous.batchCount))}
            />
          </div>

          <Card>
            <CardHeader>
              <CardTitle className="font-display text-base font-semibold">{strings.chartTitle}</CardTitle>
            </CardHeader>
            <CardContent>
              <ProductionBars
                bars={bars}
                series={seriesBranchIds.map((id) => ({ key: id, label: branchName(id) }))}
                dayCount={dayCount}
                branchLabel={branchName}
                productLabel={productLabel}
                highlightProductId={highlightId}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="font-display text-base font-semibold">{strings.tableTitle}</CardTitle>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{strings.columns.product}</TableHead>
                    <TableHead className="w-40 text-right">{strings.columns.doughKg}</TableHead>
                    <TableHead className="w-24 text-right">{strings.columns.batchCount}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {branchGroups.flatMap((group) => [
                    <TableRow key={`h-${group.branchId}`} className="hover:bg-transparent">
                      <TableCell colSpan={3} className="text-sm font-semibold">
                        {branchName(group.branchId)}
                      </TableCell>
                    </TableRow>,
                    ...group.rows.map((row) => (
                      <TableRow
                        key={`${row.productId}|${row.branchId}`}
                        className={row.productId === highlightId ? "bg-peach-soft" : undefined}
                      >
                        <TableCell>{productLabel(row.productName)}</TableCell>
                        <TableCell className="text-right tabular-nums">{formatGramsAsKg(row.mixWeightGrams)}</TableCell>
                        <TableCell className="text-right tabular-nums">{row.batchCount}</TableCell>
                      </TableRow>
                    )),
                  ])}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
