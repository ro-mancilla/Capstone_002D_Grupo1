import { describe, expect, it } from "vitest"
import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL, FIXTURE } from "@/mocks/handlers"
import type { ModuleId, TenantProfileResponse, UpdateModulesRequest } from "@/api/types"
import { renderApp } from "@/test/router-utils"

/**
 * Plan 07 §11 item 7's mandated AC: toggling `INVENTORY_FORMULAS` off (a) hides the formula
 * editor from nav, (b) a direct URL to it redirects away, and (c) toggling back on restores it --
 * all driven by one profile refetch, not a full reload. Exercised end to end (real `AuthProvider`,
 * real router, MSW backing a mutable server-side module set) so this is provably not just
 * `nav.ts`'s pure-function test in isolation.
 */
async function loginAsGeneralManager() {
  const user = userEvent.setup()
  await screen.findByText(/iniciar sesión/i)
  await user.type(screen.getByLabelText(/panadería/i), "reference")
  await user.type(screen.getByLabelText(/usuario/i), "genmgr")
  await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
  await user.click(screen.getByRole("button", { name: /ingresar/i }))
  return user
}

describe("Module toggle (plan 07 §11 item 7 AC)", () => {
  it("toggling INVENTORY_FORMULAS off hides it from nav and redirects a direct URL; toggling back on restores it", async () => {
    let enabledModules: ModuleId[] = [...FIXTURE.tenant.enabledModules]

    server.use(
      mswHttp.get(`${API_BASE_URL}/tenant`, () => {
        const response: TenantProfileResponse = {
          id: FIXTURE.tenant.id,
          name: FIXTURE.tenant.name,
          slug: FIXTURE.tenant.slug,
          currencyCode: FIXTURE.tenant.currencyCode,
          enabledModules,
          branches: [FIXTURE.branch],
        }
        return HttpResponse.json(response)
      }),
      mswHttp.put<never, UpdateModulesRequest>(`${API_BASE_URL}/tenant/modules`, async ({ request }) => {
        const body = await request.json()
        enabledModules = body.enabledModules
        const response: TenantProfileResponse = {
          id: FIXTURE.tenant.id,
          name: FIXTURE.tenant.name,
          slug: FIXTURE.tenant.slug,
          currencyCode: FIXTURE.tenant.currencyCode,
          enabledModules,
          branches: [FIXTURE.branch],
        }
        return HttpResponse.json(response)
      })
    )

    const { router } = renderApp("/organization/modules")
    const user = await loginAsGeneralManager()

    await waitFor(() => expect(router.state.location.pathname).toBe("/organization/modules"))
    await screen.findByText("Fórmulas e inventario")

    // Nav shows the ingredients link while the module is on.
    const sidebar = document.querySelector('[data-slot="sidebar-content"]') as HTMLElement
    await waitFor(() => expect(within(sidebar).getByText("Insumos")).toBeInTheDocument())

    // Toggle INVENTORY_FORMULAS off.
    await user.click(screen.getByRole("switch", { name: "Fórmulas e inventario" }))

    await waitFor(() => expect(within(sidebar).queryByText("Insumos")).not.toBeInTheDocument())

    // Direct navigation to the now-disabled route redirects away (guard belt-and-suspenders).
    await router.navigate({ to: "/catalog/ingredients" })
    await waitFor(() => expect(router.state.location.pathname).not.toBe("/catalog/ingredients"))

    // Toggle back on -- restored without any full reload, just the profile refetch.
    await router.navigate({ to: "/organization/modules" })
    await user.click(screen.getByRole("switch", { name: "Fórmulas e inventario" }))
    await waitFor(() => expect(within(sidebar).getByText("Insumos")).toBeInTheDocument())
  })

  it("POS_SALES switch is always on and disabled, never sendable as a toggle-off", async () => {
    renderApp("/organization/modules")
    await loginAsGeneralManager()

    const posSwitch = await screen.findByRole("switch", { name: "Ventas (punto de venta)" })
    expect(posSwitch).toHaveAttribute("data-disabled")
    expect(posSwitch).toHaveAttribute("data-checked")
  })
})
