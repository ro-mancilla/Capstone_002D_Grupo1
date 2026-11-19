import { queryOptions, useMutation, useQueryClient } from "@tanstack/react-query"
import { http } from "@/api/http"
import type { UpsertUserRequest, UserListItemDto } from "@/api/types"

/** T3/T4 (02 §3 / plan 07 §9): always-on user CRUD (`USERS_MANAGE` permission only, never
 * module-gated -- 01 §4). `password?`/`pin?` in `UpsertUserRequest` are *set* fields only: the
 * server hashes and never returns them, so `usersQuery()`'s `UserListItemDto` has no such field
 * to accidentally populate a form from (see `userForm.ts`'s doc comment). */
export function usersQuery() {
  return queryOptions({
    queryKey: ["users"] as const,
    queryFn: () => http.get<UserListItemDto[]>("/tenant/users"),
    staleTime: 30_000,
  })
}

function invalidateUsers(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: ["users"] })
}

export function useCreateUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpsertUserRequest) => http.post<UserListItemDto>("/tenant/users", body),
    onSuccess: () => invalidateUsers(queryClient),
  })
}

export function useUpdateUser() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpsertUserRequest }) =>
      http.put<UserListItemDto>(`/tenant/users/${id}`, body),
    onSuccess: () => invalidateUsers(queryClient),
  })
}
