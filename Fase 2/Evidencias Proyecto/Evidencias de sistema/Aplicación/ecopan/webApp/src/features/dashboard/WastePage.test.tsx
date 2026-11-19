import { describe, expect, it } from "vitest"
import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type { WasteEstimateDto } from "@/api/types"
import { renderApp } from "@/test/router-utils"

async function loginAsGeneralManager() {
  const user = userEvent.setup()
  await screen.findByText(/iniciar sesión/i)
  await user.type(screen.getByLabelText(/panadería/i), "reference")
  await user.type(screen.getByLabelText(/usuario/i), "genmgr")
  await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
  await user.click(screen.getByRole("button", { name: /ingresar/i }))
  return user
}

const ROWS: WasteEstimateDto[] = [
  { date: "2026-03-10", productType: "PAN", producedGrams: 30_000, soldGrams: 22_000, soldUnits: 0, estimatedWasteGrams: 8_000, estimated: true },
  { date: "2026-03-10", productType: "EMPANADA", producedGrams: 6_000, soldGrams: 0, soldUnits: 12, estimatedWasteGrams: null, estimated: true },
]

/** HU-ANA-0005: the waste page shows the estimate, says it is an estimate, and does not invent a
 * gram figure for a product sold by unit. */
describe("WastePage", () => {
  it("shows the estimated waste with the estimate notice, and no figure for unit-sold types", async () => {
    server.use(mswHttp.get(`${API_BASE_URL}/analytics/waste-estimate`, () => HttpResponse.json(ROWS)))

    renderApp("/analytics/waste")
    await loginAsGeneralManager()

    await screen.findByRole("heading", { name: "Mermas estimadas" })
    expect(screen.getByRole("note")).toHaveTextContent(/estimación/i)
    expect(await screen.findByText("Merma estimada del período")).toBeInTheDocument()
    // 30 kg produced, 22 kg sold: 8 kg estimated, the only comparable row.
    expect((await screen.findAllByText(/8(,0+)? kg/)).length).toBeGreaterThan(0)
    expect(screen.getByText(/se vende por unidad/i)).toBeInTheDocument()
  })

  it("shows the empty state when nothing was produced or sold in the range", async () => {
    server.use(mswHttp.get(`${API_BASE_URL}/analytics/waste-estimate`, () => HttpResponse.json([])))

    renderApp("/analytics/waste")
    await loginAsGeneralManager()

    await screen.findByRole("heading", { name: "Mermas estimadas" })
    expect(await screen.findByText(/no hay datos/i)).toBeInTheDocument()
  })
})
