import { describe, expect, it } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { renderApp, renderRouterAt } from "@/test/router-utils"
import { server } from "@/mocks/server"
import { API_BASE_URL, FIXTURE } from "@/mocks/handlers"
import { managerSession } from "@/test/fixtures"
import { isEntitled, type DestinationPath } from "@/lib/destinations"
import type { ModuleId } from "@/api/types"

describe("_authed beforeLoad guard (plan 07 §3)", () => {
  it("redirects an unauthenticated visit to /dashboard to /login?redirect=... and back after login", async () => {
    const { router } = renderApp("/analytics")

    await waitFor(() => expect(router.state.location.pathname).toBe("/login"))
    expect(router.state.location.search).toMatchObject({ redirect: expect.stringContaining("/analytics") })
    await screen.findByText(/iniciar sesión/i)

    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/panadería/i), "reference")
    await user.type(screen.getByLabelText(/usuario/i), "genmgr")
    await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
    await user.click(screen.getByRole("button", { name: /ingresar/i }))

    await waitFor(() => expect(router.state.location.pathname).toBe("/analytics"))
  })

  it("renders the app shell nav once authenticated as a GENERAL_MANAGER", async () => {
    renderApp("/analytics")

    const user = userEvent.setup()
    await screen.findByText(/iniciar sesión/i)
    await user.type(screen.getByLabelText(/panadería/i), "reference")
    await user.type(screen.getByLabelText(/usuario/i), "genmgr")
    await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
    await user.click(screen.getByRole("button", { name: /ingresar/i }))

    expect(await screen.findByText("Gina General Manager")).toBeInTheDocument()
  })
})

describe("entitlement redirects (issue #1)", () => {
  /** Whatever page we land on may fetch; this suite is about routing, not page data. */
  function stubReads() {
    server.use(mswHttp.get(`${API_BASE_URL}/*`, () => HttpResponse.json([])))
  }

  it("sends a manager with ANALYTICS_DASHBOARD off to a route the guards admit, not back into /dashboard", async () => {
    stubReads()
    const { router } = renderRouterAt("/analytics", managerSession({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }))

    await waitFor(() => expect(router.state.location.pathname).not.toBe("/analytics"))

    const landed = router.state.location.pathname
    expect(landed.startsWith("/analytics")).toBe(false)
    expect(isEntitled(managerSession({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }), landed as DestinationPath)).toBe(true)

    // The redirect settles instead of repeating: the destination is stable once reached.
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(router.state.location.pathname).toBe(landed)
  })

  // The admitted case is exercised by `ReconciliationPage.test.tsx`, which opens this route.
  it("refuses /analytics/reconciliation with INVENTORY_FORMULAS off", async () => {
    stubReads()
    const off = managerSession({ enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD"] })
    const { router } = renderRouterAt("/analytics/reconciliation", off)

    await waitFor(() => expect(router.state.location.pathname).not.toBe("/analytics/reconciliation"))
    expect(isEntitled(off, router.state.location.pathname as DestinationPath)).toBe(true)
  })

  it("forwards the app root to an entitled destination rather than a hard-coded /dashboard", async () => {
    stubReads()
    const { router } = renderRouterAt("/", managerSession({ enabledModules: ["POS_SALES", "INVENTORY_FORMULAS"] }))

    await waitFor(() => expect(router.state.location.pathname).not.toBe("/"))
    expect(router.state.location.pathname.startsWith("/analytics")).toBe(false)
  })

  it("still lands on the dashboard when ANALYTICS_DASHBOARD is on", async () => {
    stubReads()
    const { router } = renderRouterAt("/", managerSession({ enabledModules: ["POS_SALES", "ANALYTICS_DASHBOARD"] }))

    await waitFor(() => expect(router.state.location.pathname).toBe("/analytics"))
  })
})

describe("a session entitled to nothing (issue #1)", () => {
  /** With Almacén stock in the core catalog, `products` needs no module, so a branch manager
   * on a POS-only tenant is no longer stranded: they land on Productos. The "nothing at all" path is
   * still guarded (a role with no capabilities), so the redirect is exercised with one of those. */
  const stranded = managerSession({ role: "BAKER", enabledModules: ["POS_SALES"] })

  it("lands a POS-only branch manager on Productos instead of a dead end", async () => {
    const branchMgr = { ...FIXTURE.genMgr, role: "BRANCH_MANAGER" as const, branchId: FIXTURE.branch.id }
    const tenant = { ...FIXTURE.tenant, enabledModules: ["POS_SALES"] as ModuleId[] }

    server.use(
      mswHttp.post(`${API_BASE_URL}/auth/login`, () =>
        HttpResponse.json({
          accessToken: "access-token-1",
          refreshToken: "refresh-token-1",
          user: branchMgr,
          tenant,
          branches: [FIXTURE.branch],
        })
      ),
      mswHttp.get(`${API_BASE_URL}/tenant`, () =>
        HttpResponse.json({ ...tenant, branches: [FIXTURE.branch] })
      ),
      mswHttp.get(`${API_BASE_URL}/*`, () => HttpResponse.json([]))
    )

    const { router } = renderApp("/login")

    const user = userEvent.setup()
    await screen.findByText(/iniciar sesión/i)
    await user.type(screen.getByLabelText(/panadería/i), "reference")
    await user.type(screen.getByLabelText(/usuario/i), "branchmgr")
    await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
    await user.click(screen.getByRole("button", { name: /ingresar/i }))

    await waitFor(() => expect(router.state.location.pathname).toBe("/catalog/products"))
  })

  it("sends a session entitled to nothing away from a guarded route instead of looping on it", async () => {
    server.use(mswHttp.get(`${API_BASE_URL}/*`, () => HttpResponse.json([])))
    const { router } = renderRouterAt("/analytics", stranded)

    await waitFor(() => expect(router.state.location.pathname).toBe("/login"))
  })
})
