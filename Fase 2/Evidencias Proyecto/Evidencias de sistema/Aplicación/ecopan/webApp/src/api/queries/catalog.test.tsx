import { describe, expect, it } from "vitest"
import { renderHook, waitFor } from "@testing-library/react"
import { http as mswHttp, HttpResponse } from "msw"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import type { ReactNode } from "react"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import { useCreateProduct, useDeleteProduct, useUpdateProduct } from "./catalog"

const BRANCH = "b1b1b1b1-0000-4000-8000-000000000001"

function setup() {
  const queryClient = new QueryClient()
  queryClient.setQueryData(["product-stock", { branchId: BRANCH }], [])
  queryClient.setQueryData(["product-movements", { branchId: BRANCH }], [])
  queryClient.setQueryData(["products", { includeInactive: false }], [])
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  const stale = () => ({
    stock: queryClient.getQueryState(["product-stock", { branchId: BRANCH }])!.isInvalidated,
    movements: queryClient.getQueryState(["product-movements", { branchId: BRANCH }])!.isInvalidated,
    products: queryClient.getQueryState(["products", { includeInactive: false }])!.isInvalidated,
  })
  return { wrapper, stale }
}

describe("product mutations stale the Almacén stock caches", () => {
  it("create, update and delete each invalidate products, product-stock and product-movements", async () => {
    server.use(
      mswHttp.post(`${API_BASE_URL}/catalog/products`, () => HttpResponse.json({ id: "p1" }, { status: 201 })),
      mswHttp.put(`${API_BASE_URL}/catalog/products/p1`, () => HttpResponse.json({ id: "p1" })),
      mswHttp.delete(`${API_BASE_URL}/catalog/products/p1`, () => new HttpResponse(null, { status: 204 }))
    )
    const runs = [
      { hook: useCreateProduct, vars: { id: "p1" } },
      { hook: useUpdateProduct, vars: { id: "p1", body: {} } },
      { hook: useDeleteProduct, vars: "p1" },
    ] as const
    for (const { hook, vars } of runs) {
      const { wrapper, stale } = setup()
      const { result } = renderHook(() => hook() as never as { mutateAsync: (v: unknown) => Promise<unknown> }, { wrapper })
      await result.current.mutateAsync(vars)
      await waitFor(() => expect(stale()).toEqual({ stock: true, movements: true, products: true }))
    }
  })
})
