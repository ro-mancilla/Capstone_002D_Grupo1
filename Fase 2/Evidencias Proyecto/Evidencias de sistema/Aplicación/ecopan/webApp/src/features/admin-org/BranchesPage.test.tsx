import { describe, expect, it } from "vitest"
import { render, screen } from "@testing-library/react"
import { http as mswHttp, HttpResponse } from "msw"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type { Session, TenantProfileResponse } from "@/api/types"
import { storeSession } from "@/api/auth"
import { setSessionTokens } from "@/api/http"
import { AuthProvider } from "@/features/auth/AuthContext"
import { BranchesPage } from "./BranchesPage"

function session(enabledModules: Session["enabledModules"], role: Session["role"] = "GENERAL_MANAGER"): Session {
  return {
    userId: "u1",
    tenantId: "t1",
    tenantSlug: "reference",
    branchId: null,
    role,
    displayName: "Gina",
    tenantName: "Panadería Reference",
    currencyCode: "CLP",
    enabledModules,
    branches: [{ id: "b1", name: "Centro" }],
  }
}

function renderPage(enabledModules: Session["enabledModules"], role: Session["role"] = "GENERAL_MANAGER") {
  setSessionTokens({ accessToken: "token", refreshToken: "refresh" })
  storeSession(session(enabledModules, role))

  server.use(
    mswHttp.get(`${API_BASE_URL}/analytics/sales-by-day`, () =>
      HttpResponse.json([
        { date: "2026-10-01", branchId: "b1", productType: null, salesCount: 3, weightGrams: 0, units: 3, revenueMinor: 15000 },
      ])
    ),
    mswHttp.get(`${API_BASE_URL}/tenant`, () => {
      const response: TenantProfileResponse = {
        id: "t1",
        name: "Panadería Reference",
        slug: "reference",
        currencyCode: "CLP",
        enabledModules,
        branches: [{ id: "b1", name: "Centro", active: true }],
      }
      return HttpResponse.json(response)
    })
  )

  const queryClient = new QueryClient()
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BranchesPage />
      </AuthProvider>
    </QueryClientProvider>
  )
}

/** Plan 07 §5 row 5: with `MULTI_BRANCH_ROLES` off, `admin/branches` stays reachable and renders
 * read-only over T1's `branches[]` -- no create/edit affordance, but the list is still there. */
describe("BranchesPage (module-off read-only rendering, plan 07 §5)", () => {
  it("hides create/edit actions and shows the module-off notice when MULTI_BRANCH_ROLES is off", async () => {
    renderPage(["POS_SALES", "ANALYTICS_DASHBOARD"])

    expect(await screen.findByRole("cell", { name: "Centro" })).toBeInTheDocument()
    expect(screen.getByText(/de solo lectura/)).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Nueva sucursal" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Editar" })).not.toBeInTheDocument()
  })

  it("shows create/edit actions when MULTI_BRANCH_ROLES is on", async () => {
    renderPage(["POS_SALES", "ANALYTICS_DASHBOARD", "MULTI_BRANCH_ROLES"])

    expect(await screen.findByRole("cell", { name: "Centro" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Nueva sucursal" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Editar" })).toBeInTheDocument()
  })

  it("shows per-branch revenue next to the list when the dashboard is open to the session", async () => {
    renderPage(["POS_SALES", "ANALYTICS_DASHBOARD", "MULTI_BRANCH_ROLES"])

    expect(await screen.findByRole("cell", { name: "Centro" })).toBeInTheDocument()
    expect(await screen.findByRole("columnheader", { name: "Ingresos" })).toBeInTheDocument()
    expect(await screen.findByText(/15\.000/)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Nueva sucursal" })).toBeInTheDocument()
  })

  it("omits the revenue columns and filters when ANALYTICS_DASHBOARD is off", async () => {
    renderPage(["POS_SALES", "MULTI_BRANCH_ROLES"])

    expect(await screen.findByRole("cell", { name: "Centro" })).toBeInTheDocument()
    expect(screen.queryByRole("columnheader", { name: "Ingresos" })).not.toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Editar" })).toBeInTheDocument()
  })

  it("gives a branch manager the list and revenue read-only, without the module-off notice", async () => {
    renderPage(["POS_SALES", "ANALYTICS_DASHBOARD", "MULTI_BRANCH_ROLES"], "BRANCH_MANAGER")

    expect(await screen.findByRole("cell", { name: "Centro" })).toBeInTheDocument()
    expect(await screen.findByRole("columnheader", { name: "Ingresos" })).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Nueva sucursal" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Editar" })).not.toBeInTheDocument()
    expect(screen.queryByText(/de solo lectura/)).not.toBeInTheDocument()
  })
})
