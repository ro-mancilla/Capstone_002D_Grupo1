import { queryOptions, useMutation, useQueryClient } from "@tanstack/react-query"
import { http } from "@/api/http"
import type { FormulaDto, PutFormulaRequest } from "@/api/types"

/** C5 (02 §3 / plan 07 §9): every formula for the tenant, one row per product that has one.
 * `INVENTORY_FORMULAS`-gated server-side (`CatalogRouter.kt`'s `formulas` route) -- a 403 here
 * surfaces via `ApiError`/the module-disabled handler (`api/http.ts`), not a thrown crash. */
export function formulasQuery() {
  return queryOptions({
    queryKey: ["formulas"] as const,
    queryFn: () => http.get<FormulaDto[]>("/catalog/formulas"),
    staleTime: 30_000,
  })
}

function invalidateFormulas(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: ["formulas"] })
}

/** C6: replace-all lines for one product's formula. */
export function usePutFormula() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ productId, body }: { productId: string; body: PutFormulaRequest }) =>
      http.put<FormulaDto>(`/catalog/products/${productId}/formula`, body),
    onSuccess: () => invalidateFormulas(queryClient),
  })
}
