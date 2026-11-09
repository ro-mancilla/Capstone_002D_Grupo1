import { queryOptions, useMutation, useQueryClient } from "@tanstack/react-query"
import { http } from "@/api/http"
import type { ModuleId, TenantProfileResponse, UpdateModulesRequest } from "@/api/types"

/** Root key of the tenant profile; module toggles and the SSE stream invalidate it. */
export const tenantKeys = {
  profile: ["tenant"] as const,
}

/** T1 (02 §3 / plan 07 §9): any authenticated principal. Also the refetch target on
 * module-toggle / 403 (plan 07 §3) -- other features invalidate `["tenant"]`, they don't
 * duplicate this key. */
export function tenantProfileQuery() {
  return queryOptions({
    queryKey: tenantKeys.profile,
    queryFn: () => http.get<TenantProfileResponse>("/tenant"),
    staleTime: 30_000,
  })
}

/** T2: the entitlement demo switch (plan 07 §5/§11 item 7). Invalidating `["tenant"]` here is
 * what makes the toggle page's own change show up immediately -- `useTenantProfileSync`
 * (mounted app-wide from `AppShell`) picks up the refetched profile and pushes it into
 * `Session`, so nav re-filters without any other code path. `POS_SALES` pinning (always sent,
 * never removable) lives in the caller (`admin-modules/ModulesPage.tsx`), not here -- this hook
 * is a plain, module-name-agnostic wrapper over T2. */
export function useUpdateModules() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (enabledModules: ModuleId[]) => {
      const body: UpdateModulesRequest = { enabledModules }
      return http.put<TenantProfileResponse>("/tenant/modules", body)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: tenantKeys.profile }),
  })
}
