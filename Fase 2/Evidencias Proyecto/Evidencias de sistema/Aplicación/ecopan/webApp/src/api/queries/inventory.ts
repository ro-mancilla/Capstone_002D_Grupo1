import { queryOptions, useMutation, useQueryClient } from "@tanstack/react-query"
import { http } from "@/api/http"
import type {
  CreateIngredientRequest,
  CreateProductStockMovementRequest,
  CreateStockMovementRequest,
  CreateStockMovementResponse,
  IngredientDto,
  ProductStockLevelDto,
  ProductStockMovementDto,
  ReconciliationReportDto,
  StockLevelDto,
  StockMovementDto,
  UpdateIngredientRequest,
} from "@/api/types"

/** I1 (02 §3 / plan 07 §9). The ingredient routes are `INVENTORY_FORMULAS`-gated server-side
 * (`InventoryRouter.kt`); the product-stock queries at the bottom of this file are not -- Almacén
 * stock is core catalog. */
/** Roots of the ingredient-stock and movement queries; a batch decrement changes both. */
export const inventoryKeys = {
  stock: ["stock"] as const,
  movements: ["movements"] as const,
}

export function ingredientsQuery(includeInactive: boolean) {
  return queryOptions({
    queryKey: ["ingredients", { includeInactive }] as const,
    queryFn: () =>
      http.get<IngredientDto[]>(`/inventory/ingredients?includeInactive=${includeInactive}`),
    staleTime: 30_000,
  })
}

function invalidateIngredients(queryClient: ReturnType<typeof useQueryClient>) {
  return queryClient.invalidateQueries({ queryKey: ["ingredients"] })
}

export function useCreateIngredient() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateIngredientRequest) =>
      http.post<IngredientDto>("/inventory/ingredients", body),
    onSuccess: () => invalidateIngredients(queryClient),
  })
}

export function useUpdateIngredient() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateIngredientRequest }) =>
      http.put<IngredientDto>(`/inventory/ingredients/${id}`, body),
    onSuccess: () => invalidateIngredients(queryClient),
  })
}

/** I3: current Σ movements per ingredient for one branch, with low-stock flags. */
export function stockLevelsQuery(branchId: string) {
  return queryOptions({
    queryKey: [...inventoryKeys.stock, { branchId }] as const,
    queryFn: () => http.get<StockLevelDto[]>(`/inventory/stock?branchId=${branchId}`),
    staleTime: 0,
  })
}

export interface ReconciliationFilter {
  branchId: string
  /** `YYYY-MM-DD`, both ends inclusive; the server reads them in the reporting timezone. */
  from: string
  to: string
}

/** HU-ANA-0006: opening + purchases + decrements + adjustments = closing, per ingredient. */
export function reconciliationQuery(filter: ReconciliationFilter) {
  const params = new URLSearchParams({ branchId: filter.branchId, from: filter.from, to: filter.to })
  return queryOptions({
    queryKey: ["reconciliation", filter] as const,
    queryFn: () =>
      http.get<ReconciliationReportDto>(`/inventory/reconciliation?${params.toString()}`),
    staleTime: 0,
    // One retry, like the KPI reads: enough for a network blip, short enough that a real
    // failure reaches the page's retry button quickly.
    retry: 1,
  })
}

export interface MovementsFilter {
  branchId: string
  ingredientId?: string
  from?: string
  to?: string
  limit?: number
  offset?: number
}

/** I4: ledger view, optionally scoped to one ingredient/date range, paged via `limit`/`offset`
 * (server defaults: limit 100, offset 0, per `InventoryRouter.kt`). */
export function movementsQuery(filter: MovementsFilter) {
  const params = new URLSearchParams({ branchId: filter.branchId })
  if (filter.ingredientId) params.set("ingredientId", filter.ingredientId)
  if (filter.from) params.set("from", filter.from)
  if (filter.to) params.set("to", filter.to)
  if (filter.limit !== undefined) params.set("limit", String(filter.limit))
  if (filter.offset !== undefined) params.set("offset", String(filter.offset))

  return queryOptions({
    queryKey: [...inventoryKeys.movements, filter] as const,
    queryFn: () => http.get<StockMovementDto[]>(`/inventory/movements?${params.toString()}`),
    staleTime: 0,
  })
}

/** I5: append-only -- used for both purchase entry and manual signed adjustment (plan 07 §5:
 * "never edits a stored quantity, always appends"). Invalidates both the ledger and the stock
 * levels so the new total shows immediately without a manual refetch. */
export function useAppendStockMovement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateStockMovementRequest) =>
      http.post<CreateStockMovementResponse>("/inventory/movements", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: inventoryKeys.movements })
      queryClient.invalidateQueries({ queryKey: inventoryKeys.stock })
      queryClient.invalidateQueries({ queryKey: ["reconciliation"] })
    },
  })
}

// -- Almacén product stock (not module-gated) --------------------------------

/** Current stock of every Almacén product in one branch (`GET /inventory/product-stock`). */
export function productStockLevelsQuery(branchId: string) {
  return queryOptions({
    queryKey: ["product-stock", { branchId }] as const,
    queryFn: () =>
      http.get<ProductStockLevelDto[]>(`/inventory/product-stock?branchId=${branchId}`),
    staleTime: 0,
  })
}

export interface ProductMovementsFilter {
  branchId: string
  productId?: string
  limit?: number
  offset?: number
}

/** Unit ledger view, optionally scoped to one product, paged via `limit`/`offset`. */
export function productMovementsQuery(filter: ProductMovementsFilter) {
  const params = new URLSearchParams({ branchId: filter.branchId })
  if (filter.productId) params.set("productId", filter.productId)
  if (filter.limit !== undefined) params.set("limit", String(filter.limit))
  if (filter.offset !== undefined) params.set("offset", String(filter.offset))

  return queryOptions({
    queryKey: ["product-movements", filter] as const,
    queryFn: () =>
      http.get<ProductStockMovementDto[]>(`/inventory/product-movements?${params.toString()}`),
    staleTime: 0,
  })
}

/** Sets (a number) or clears (`null`) a unit product's reorder level; the dashboard's low-stock
 * flag is derived from it server-side, so only the unit stock levels need refetching. */
export function useSetProductLowStockThreshold() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ productId, thresholdUnits }: { productId: string; thresholdUnits: number | null }) =>
      http.put<{ productId: string; lowStockThresholdUnits: number | null }>(
        `/inventory/product-stock/${productId}/threshold`,
        { thresholdUnits }
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["product-stock"] }),
  })
}

/** Append-only, like `useAppendStockMovement` -- purchase entry and manual adjustment both go
 * through here. Invalidates the unit ledger and the unit stock levels together. */
export function useAppendProductStockMovement() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateProductStockMovementRequest) =>
      http.post<ProductStockMovementDto>("/inventory/product-movements", body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["product-movements"] })
      queryClient.invalidateQueries({ queryKey: ["product-stock"] })
    },
  })
}
