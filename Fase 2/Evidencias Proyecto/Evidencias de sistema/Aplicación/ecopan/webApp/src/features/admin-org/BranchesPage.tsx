import { useQuery } from "@tanstack/react-query"
import { PlusIcon } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import { GroupedBarChart } from "@/components/charts/GroupedBarChart"
import { salesByDayQuery, SSE_POLLING_FALLBACK } from "@/api/queries/kpis"
import { tenantProfileQuery } from "@/api/queries/tenant"
import { DashboardFilterBar } from "@/features/dashboard/DashboardFilterBar"
import { totalsByBranch } from "@/features/dashboard/aggregate"
import { useDashboardFilters } from "@/features/dashboard/filters"
import { useAuth } from "@/features/auth/AuthContext"
import { isEntitled } from "@/lib/destinations"
import { hasModule } from "@/lib/entitlements"
import { formatCurrencyMinor } from "@/lib/format"
import { t } from "@/lib/i18n"
import { can } from "@/lib/roles"
import { BranchFormDialog } from "./BranchFormDialog"

/** The one Sucursales page (`organization/branches`): the branch list, per-branch revenue, and branch
 * management, each shown to the audience entitled to it.
 *
 * - The list is for every manager who reaches the route (`MANAGE_BRANCH_STOCK`), read off
 *   `GET /tenant`'s (T1) `branches[]` -- no dedicated list endpoint (plan 07 §9).
 * - Revenue (filter bar, chart, per-branch columns) appears only when the session may open the
 *   dashboard. That is asked of the destination table (`isEntitled(.., "/analytics")`), not
 *   re-derived here, so the page cannot disagree with the Análisis section of the sidebar.
 * - Create/edit needs `MANAGE_FULL_ADMIN` *and* `MULTI_BRANCH_ROLES`. A full admin with the module
 *   off sees the list read-only with a notice (plan 07 §5); anyone else sees it read-only quietly. */
export function BranchesPage() {
  const strings = t.admin.branches
  const metricStrings = t.dashboard.branches
  const { session } = useAuth()
  const profileResult = useQuery(tenantProfileQuery())
  const isFullAdmin = can(session?.role, "MANAGE_FULL_ADMIN")
  const canManage = isFullAdmin && hasModule(session, "MULTI_BRANCH_ROLES")
  const showModuleOffNotice = isFullAdmin && !canManage
  const showMetrics = isEntitled(session, "/analytics")
  const branches = profileResult.data?.branches ?? []
  const currencyCode = session?.currencyCode ?? "CLP"

  const sessionBranches = session?.branches ?? []
  const showBranchSelect = can(session?.role, "SELECT_BRANCH") && sessionBranches.length > 1
  const { filters, setBranchId, setPreset, setCustomRange } = useDashboardFilters(
    session?.branchId ?? undefined
  )
  const salesResult = useQuery({
    ...salesByDayQuery({ branchId: filters.branchId, from: filters.from, to: filters.to }),
    ...SSE_POLLING_FALLBACK,
    enabled: showMetrics,
  })
  const totals = new Map(totalsByBranch(salesResult.data ?? []).map((total) => [total.branchId, total]))
  const branchNameById = new Map(branches.map((branch) => [branch.id, branch.name]))
  const chartData = [...totals.values()].map((total) => ({
    branch: branchNameById.get(total.branchId) ?? t.common.unnamed,
    revenueMinor: total.revenueMinor,
  }))
  const noMetric = metricStrings.noMetric

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-2">
        <h1 className="page-title">{strings.title}</h1>
        <div className="flex items-center gap-2">
          {showMetrics && (
            <DashboardFilterBar
              filters={filters}
              branches={sessionBranches}
              showBranchSelect={showBranchSelect}
              onBranchChange={setBranchId}
              onPresetChange={setPreset}
              onCustomRangeChange={setCustomRange}
            />
          )}
          {canManage && (
            <BranchFormDialog
              trigger={
                <Button>
                  <PlusIcon className="size-4" />
                  {strings.newBranch}
                </Button>
              }
            />
          )}
        </div>
      </div>

      {showModuleOffNotice && <p className="text-muted-foreground text-sm">{strings.moduleOffNotice}</p>}

      {showMetrics && !salesResult.isLoading && chartData.length > 0 && (
        <div>
          <h2 className="text-muted-foreground mb-2 text-sm">{metricStrings.chartTitle}</h2>
          <GroupedBarChart
            data={chartData}
            xKey="branch"
            series={[{ key: "revenueMinor", label: metricStrings.columns.revenue }]}
            valueFormatter={(value) => formatCurrencyMinor(value, currencyCode)}
          />
        </div>
      )}

      {profileResult.isLoading ? (
        <Skeleton className="h-48 w-full" />
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{strings.columns.name}</TableHead>
              <TableHead>{strings.columns.status}</TableHead>
              {showMetrics && <TableHead>{metricStrings.columns.revenue}</TableHead>}
              {showMetrics && <TableHead>{metricStrings.columns.salesCount}</TableHead>}
              {canManage && <TableHead className="text-right">{strings.columns.actions}</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {branches.map((branch) => {
              const total = totals.get(branch.id)
              return (
                <TableRow key={branch.id}>
                  <TableCell>{branch.name}</TableCell>
                  <TableCell>
                    <Badge variant={branch.active ? "success" : "outline"}>
                      {branch.active ? strings.statusActive : strings.statusInactive}
                    </Badge>
                  </TableCell>
                  {showMetrics && (
                    <TableCell className="ds-price">
                      {total ? formatCurrencyMinor(total.revenueMinor, currencyCode) : noMetric}
                    </TableCell>
                  )}
                  {showMetrics && <TableCell>{total ? total.salesCount : noMetric}</TableCell>}
                  {canManage && (
                    <TableCell className="text-right">
                      <BranchFormDialog
                        branch={branch}
                        trigger={<Button variant="outline">{t.common.edit}</Button>}
                      />
                    </TableCell>
                  )}
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      )}
    </div>
  )
}
