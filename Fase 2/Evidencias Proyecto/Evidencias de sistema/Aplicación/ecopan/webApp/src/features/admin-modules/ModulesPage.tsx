import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Skeleton } from "@/components/ui/skeleton"
import { Switch } from "@/components/ui/switch"
import { tenantProfileQuery, useUpdateModules } from "@/api/queries/tenant"
import { errorMessage } from "@/lib/apiErrors"
import type { ModuleId } from "@/api/types"
import { t } from "@/lib/i18n"

/** All toggleable modules, in display order. `POS_SALES` is first and permanently on -- it is
 * still a real module name (present in `enabledModules`), just never removable from the set this
 * page sends (plan 07 §5: "pinned on/disabled ... don't even send it in the toggle payload if
 * that's how the backend expects it" -- `TenantServiceImpl.updateModules` replaces the whole set
 * verbatim with no server-side POS_SALES special-casing, so this client must always include it). */
const ALL_MODULES: ModuleId[] = [
  "POS_SALES",
  "INVENTORY_FORMULAS",
  "DEMAND_FORECASTING",
  "MULTI_BRANCH_ROLES",
  "ANALYTICS_DASHBOARD",
]

/**
 * `admin/modules` (plan 07 §5 row 7 / §11 item 7): "the entitlement demo switch". Each switch
 * saves immediately (no separate submit) -- `useUpdateModules`'s `onSuccess` invalidates
 * `["tenant"]`, which `AppShell`'s `useTenantProfileSync` picks up so nav re-filters live. The
 * mechanism is generic across module names (`ALL_MODULES` is just a display list); only
 * `POS_SALES`'s switch is hardcoded disabled+checked, per plan.
 */
export function ModulesPage() {
  const strings = t.admin.modules
  const [error, setError] = useState<string | null>(null)
  const profileResult = useQuery(tenantProfileQuery())
  const updateModules = useUpdateModules()

  const enabledModules = new Set(profileResult.data?.enabledModules ?? [])

  async function handleToggle(module: ModuleId, checked: boolean) {
    if (module === "POS_SALES") return // pinned on, never sent as a change.
    setError(null)
    const next = new Set(enabledModules)
    if (checked) next.add(module)
    else next.delete(module)
    // POS_SALES always included -- this client never sends a toggle payload without it.
    next.add("POS_SALES")
    try {
      await updateModules.mutateAsync([...next])
    } catch (err) {
      setError(errorMessage(err, strings.genericError))
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div>
        <h1 className="page-title">{strings.title}</h1>
        <p className="text-muted-foreground text-sm">{strings.subtitle}</p>
      </div>

      {profileResult.isLoading ? (
        <Skeleton className="h-64 w-full max-w-lg" />
      ) : (
        <div className="flex max-w-lg flex-col divide-y rounded-lg border">
          {ALL_MODULES.map((module) => {
            const isPosSales = module === "POS_SALES"
            return (
              <div key={module} className="flex items-start justify-between gap-4 p-4">
                <div className="flex flex-col gap-1">
                  <span className="text-sm font-medium">{strings.names[module]}</span>
                  <span className="text-muted-foreground text-sm">
                    {isPosSales ? strings.posSalesLockedNote : strings.descriptions[module]}
                  </span>
                </div>
                <Switch
                  checked={isPosSales || enabledModules.has(module)}
                  disabled={isPosSales || updateModules.isPending}
                  onCheckedChange={(checked) => handleToggle(module, checked)}
                  aria-label={strings.names[module]}
                />
              </div>
            )
          })}
        </div>
      )}

      {error && (
        <p role="alert" className="text-destructive text-sm">
          {error}
        </p>
      )}
    </div>
  )
}
