import { useEffect } from "react"
import { useQuery } from "@tanstack/react-query"
import { tenantProfileQuery } from "@/api/queries/tenant"
import { useAuth } from "@/features/auth/AuthContext"

/**
 * Keeps the in-memory `Session` (nav/guards' source of truth for entitlements) live-synced to
 * `GET /tenant` (T1) -- mounted once from `AppShell` so it applies to every authenticated route,
 * not just `admin/modules`. Two things invalidate `["tenant"]` and thus re-trigger this:
 *   1. `useUpdateModules`'s own `onSuccess` (the toggle page itself, plan 07 §5).
 *   2. `api/http.ts`'s generic module-disabled handler, whenever *any* request 403s with a
 *      populated `ErrorBody.module` (a stale tab whose module was toggled off elsewhere).
 * Either path re-fetches the profile and this hook pushes the result into `Session`, so nav
 * re-filters and the next route guard check sees the current entitlement -- "restores them
 * without a full page reload beyond the profile refetch" (plan 07 §11 item 7's AC).
 */
export function useTenantProfileSync(): void {
  const { session, syncFromTenantProfile } = useAuth()
  const { data } = useQuery({ ...tenantProfileQuery(), enabled: session !== null })

  useEffect(() => {
    if (data) syncFromTenantProfile(data)
  }, [data, syncFromTenantProfile])
}
