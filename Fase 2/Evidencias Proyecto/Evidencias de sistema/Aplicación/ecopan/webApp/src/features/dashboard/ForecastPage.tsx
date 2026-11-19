import { useMemo, useState } from "react"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
import { useQuery } from "@tanstack/react-query"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { forecastVsActualQuery } from "@/api/queries/forecast"
import { SSE_POLLING_FALLBACK } from "@/api/queries/kpis"
import { useAuth } from "@/features/auth/AuthContext"
import { can } from "@/lib/roles"
import { t } from "@/lib/i18n"
import { ForecastRefresh } from "./ForecastRefresh"
import { isAlmacenType } from "@/lib/productTypes"
import { useReportingToday } from "@/lib/useReportingToday"
import { computeAccuracy } from "./forecastAccuracy"
import { forecastRanges } from "./forecastRanges"
import { groupForecastByUnit, resolveFilter } from "./forecastSeries"
import { reportingDate, reportingDayStart } from "@/lib/reportingTime"
import { ForecastUnitSection, type AccuracyStatus } from "./ForecastUnitSection"
import { SuggestedProduction } from "./SuggestedProduction"

const ALL = "__all__"

/** `dashboard/forecast` (plan 07 §4/§9, `ANALYTICS_DASHBOARD` + `DEMAND_FORECASTING` --
 * `dashboard.tsx`'s layout guard covers the first, this route's own guard covers the second):
 * tomorrow's suggested production and the next-7-days forecast vs. recent actuals per
 * (branch, Panadería product), from F1 and K8 (`forecast-vs-actual`, which already joins the
 * forecast cache with actuals server-side). The series is the product; the type is a filter. The
 * **cold-start empty state is a designed state** ("recopilando historial…"), not an error, per
 * plan 07 §4.
 */
export function ForecastPage() {
  const { session } = useAuth()
  const strings = t.dashboard.forecast
  const branches = session?.branches ?? []
  const canSelectBranch = can(session?.role, "SELECT_BRANCH")
  const [branchId, setBranchId] = useState<string>(session?.branchId ?? branches[0]?.id ?? "")
  const [productType, setProductType] = useState<string>(ALL)
  const [productId, setProductId] = useState<string>(ALL)

  // Reporting-zone day windows (see forecastRanges.ts). Keyed on the reporting date, not on
  // `Date.now()`, so the query keys are stable within a day (no refetch loop) and roll over at
  // midnight even if the page stays open.
  const today = useReportingToday()
  // Derived from `today` alone (noon UTC of the reporting date is the same reporting day), so the
  // memo's only input is the value it recomputes on.
  const tomorrow = reportingDate(reportingDayStart(Date.parse(`${today}T12:00:00Z`)) + 36 * 3_600_000)
  const ranges = useMemo(() => forecastRanges(Date.parse(`${today}T12:00:00Z`)), [today])

  // Unfiltered on the wire: the type and product filters are applied client-side so the
  // dropdowns keep listing every option while one is selected.
  const forecastResult = useQuery({
    ...forecastVsActualQuery({ branchId, ...ranges.overlay }),
    enabled: Boolean(branchId),
    ...SSE_POLLING_FALLBACK,
  })

  // Almacén is resold, never forecast; the API omits it, and this keeps a stray row out too.
  const rows = (forecastResult.data ?? []).filter((row) => !isAlmacenType(row.productType))
  const productTypes = [...new Set(rows.map((row) => row.productType))].sort()
  // A type filter the current branch has no rows for (switched branch, type vanished) reads as
  // "all" -- never a stale value the Select has no item for.
  const activeProductType = resolveFilter(productType, productTypes, ALL)
  const products = [...new Map(rows.map((row) => [row.productId, row] as const)).values()]
    .filter((row) => activeProductType === ALL || row.productType === activeProductType)
    .sort((a, b) => a.productName.localeCompare(b.productName, "es-CL"))
  // A product filter that no longer fits the chosen type (or vanished) reads as "all".
  const activeProductId = resolveFilter(productId, products.map((row) => row.productId), ALL)

  const matchesSelection = (row: { productId: string; productType: string }) =>
    (activeProductType === ALL || row.productType === activeProductType) &&
    (activeProductId === ALL || row.productId === activeProductId)

  // Grams and units are never summed: one chart per unit (see forecastSeries.ts).
  const unitGroups = groupForecastByUnit(rows.filter(matchesSelection))

  // Accuracy: last 4 whole days-of-history, scored for the same selection as the charts.
  const accuracyResult = useQuery({
    ...forecastVsActualQuery({ branchId, ...ranges.accuracy }),
    enabled: Boolean(branchId),
    ...SSE_POLLING_FALLBACK,
  })
  // Cheap (a few hundred rows), so computed per render rather than memoised on closures.
  const accuracy = computeAccuracy(
    (accuracyResult.data ?? []).filter((row) => !isAlmacenType(row.productType)).filter(matchesSelection),
    today
  )
  const accuracyStatus: AccuracyStatus = accuracyResult.isError
    ? "error"
    : accuracyResult.isPending
      ? "loading"
      : "ready"

  const hasAnyForecast = rows.some((row) => row.predictedGrams != null || row.predictedUnits != null)
  // A selected type may have sales but no forecast: say so rather than rendering nothing.
  const selectionHasNoForecast = hasAnyForecast && unitGroups.length === 0

  // Label maps for the triggers -- Base UI renders the raw value without them.
  const branchItems = branches.map((branch) => ({ value: branch.id, label: branch.name }))
  const productTypeItems = [
    { value: ALL, label: strings.allProductTypes },
    ...productTypes.map((type) => ({ value: type, label: type })),
  ]
  const productItems = [
    { value: ALL, label: strings.allProducts },
    ...products.map((row) => ({ value: row.productId, label: row.productName })),
  ]

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="page-title">{strings.title}</h1>
          <p className="text-muted-foreground text-sm">{strings.subtitle}</p>
        </div>
        <ForecastRefresh key={branchId} branchId={branchId} />
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <div className="flex flex-wrap items-center gap-2">
          {canSelectBranch && branches.length > 1 && (
            <ChoiceSelect
              options={branchItems}
              value={branchId}
              onValueChange={setBranchId}
              className="w-56"
            />
          )}
          <ChoiceSelect
            options={productTypeItems}
            value={activeProductType}
            onValueChange={setProductType}
            className="w-48"
            aria-label={strings.productTypeLabel}
            placeholder={strings.productTypeLabel}
          />
          <ChoiceSelect
            options={productItems}
            value={activeProductId}
            onValueChange={setProductId}
            className="w-56"
            aria-label={strings.productLabel}
            placeholder={strings.productLabel}
          />
        </div>
      </div>

      <SuggestedProduction
        branchId={branchId}
        matches={(row) => !isAlmacenType(row.productType) && matchesSelection(row)}
      />

      {forecastResult.isLoading ? (
        <Skeleton className="h-72 w-full" />
      ) : forecastResult.isError ? (
        <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed p-10 text-center">
          <p className="text-destructive text-sm font-medium">{strings.loadError}</p>
          <Button variant="outline" size="sm" onClick={() => void forecastResult.refetch()}>
            {t.common.retry}
          </Button>
        </div>
      ) : !hasAnyForecast ? (
        <div className="flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed p-10 text-center">
          <p className="text-sm font-medium">{strings.coldStartTitle}</p>
          <p className="text-muted-foreground max-w-sm text-sm">{strings.coldStartBody}</p>
        </div>
      ) : selectionHasNoForecast ? (
        <div className="flex flex-col items-start gap-3">
          <p className="text-muted-foreground text-sm">{strings.noForecastForSelection}</p>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              setProductType(ALL)
              setProductId(ALL)
            }}
          >
            {strings.clearFilters}
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-6">
          {unitGroups.map((group) => (
            <ForecastUnitSection
              key={group.unit}
              group={group}
              accuracy={accuracy}
              accuracyStatus={accuracyStatus}
              today={today}
              tomorrow={tomorrow}
            />
          ))}
        </div>
      )}
    </div>
  )
}
