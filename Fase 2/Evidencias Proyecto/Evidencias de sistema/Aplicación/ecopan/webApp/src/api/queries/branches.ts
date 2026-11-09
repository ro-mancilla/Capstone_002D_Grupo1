import { useMutation, useQueryClient } from "@tanstack/react-query"
import { http } from "@/api/http"
import type { BranchDto, UpsertBranchRequest } from "@/api/types"

/**
 * T5 (02 §3 / plan 07 §9): branch CRUD, `MULTI_BRANCH_ROLES`-gated server-side. There is no
 * dedicated `GET /tenant/branches` list endpoint -- `admin/branches` reads the list straight off
 * `GET /tenant`'s (T1) `branches[]` (`tenantProfileQuery()`, already the nav/session source of
 * truth), per plan 07 §5's own resolution of that open question. Both mutations invalidate
 * `["tenant"]` (not a separate `["branches"]` key) so the one list they feed re-syncs.
 */
function invalidateTenant(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: ["tenant"] })
}

export function useCreateBranch() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpsertBranchRequest) => http.post<BranchDto>("/tenant/branches", body),
    onSuccess: () => invalidateTenant(queryClient),
  })
}

export function useUpdateBranch() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpsertBranchRequest }) =>
      http.put<BranchDto>(`/tenant/branches/${id}`, body),
    onSuccess: () => invalidateTenant(queryClient),
  })
}
