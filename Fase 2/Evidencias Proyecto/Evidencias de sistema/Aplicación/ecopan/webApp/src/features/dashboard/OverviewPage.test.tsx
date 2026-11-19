import { describe, expect, it } from "vitest"
import { screen, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type { ProductStockLevelDto, StockLevelWarningDto } from "@/api/types"
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

const FLOUR_LOW: StockLevelWarningDto = {
  ingredientId: "i1",
  name: "Harina",
  currentGrams: 2_000,
  lowStock: true,
  negativeStock: false,
}

const level = (overrides: Partial<ProductStockLevelDto>): ProductStockLevelDto => ({
  productId: "p1",
  name: "Bebida cola",
  receivedUnits: 24,
  soldUnits: 20,
  currentUnits: 4,
  trackedSince: 1_700_000_000_000,
  lowStockThresholdUnits: 10,
  lowStock: true,
  active: true,
  ...overrides,
})

function stubOverview(ingredients: StockLevelWarningDto[], products: ProductStockLevelDto[]) {
  server.use(
    mswHttp.get(`${API_BASE_URL}/analytics/sales-by-day`, () => HttpResponse.json([])),
    mswHttp.get(`${API_BASE_URL}/analytics/stock-levels`, () => HttpResponse.json(ingredients)),
    mswHttp.get(`${API_BASE_URL}/inventory/product-stock`, () => HttpResponse.json(products))
  )
}

/** The overview's low-stock card counts ingredients and Almacén products, and says how many of each. */
describe("OverviewPage low-stock card", () => {
  it("counts a low Almacén product beside a low ingredient and breaks the total down", async () => {
    stubOverview([FLOUR_LOW], [level({})])

    renderApp("/analytics")
    await loginAsGeneralManager()

    const card = (await screen.findByText("Por reponer")).closest("[data-slot=card]") as HTMLElement
    expect(await within(card).findByText("2")).toBeInTheDocument()
    expect(within(card).getByText("1 insumo · 1 producto de Almacén")).toBeInTheDocument()
  })

  it("says nothing is pending, in words, when everything is above its reorder point", async () => {
    stubOverview([], [level({ currentUnits: 40, lowStock: false })])

    renderApp("/analytics")
    await loginAsGeneralManager()

    const card = (await screen.findByText("Por reponer · sin pendientes")).closest("[data-slot=card]") as HTMLElement
    expect(
      within(card).getByText("Todos los insumos y productos de Almacén están sobre su punto de reposición.")
    ).toBeInTheDocument()
    expect(within(card).queryByRole("link")).not.toBeInTheDocument()
  })

  it("adds a danger line and a link to the full list when something is below zero", async () => {
    stubOverview([{ ...FLOUR_LOW, negativeStock: true, currentGrams: -500 }], [level({})])

    renderApp("/analytics")
    await loginAsGeneralManager()

    const card = (await screen.findByText("Por reponer")).closest("[data-slot=card]") as HTMLElement
    expect(await within(card).findByText("1 insumo con stock negativo")).toBeInTheDocument()
    expect(within(card).getByRole("link", { name: "Ver los 2" })).toHaveAttribute("href", "/catalog/ingredients")
  })

  it("does not count an inactive Almacén product that is out of stock", async () => {
    stubOverview(
      [],
      [
        level({ productId: "p1", currentUnits: 0, soldUnits: 24 }),
        level({ productId: "p2", name: "Bebida", currentUnits: 0, soldUnits: 24, lowStock: false, active: false }),
      ]
    )

    renderApp("/analytics")
    await loginAsGeneralManager()

    const card = (await screen.findByText("Por reponer")).closest("[data-slot=card]") as HTMLElement
    expect(await within(card).findByText("1")).toBeInTheDocument()
    expect(within(card).getByText("0 insumos · 1 producto de Almacén")).toBeInTheDocument()
  })

  it("lists a negative Almacén product in the negative-stock warning, labelled as Almacén", async () => {
    stubOverview([], [level({ currentUnits: -3, soldUnits: 27 })])

    renderApp("/analytics")
    await loginAsGeneralManager()

    expect(await screen.findByText("Almacén · Bebida cola: -3 u.")).toBeInTheDocument()
  })

  it("points each negative-stock badge at the page where the stock is fixed", async () => {
    stubOverview(
      [{ ...FLOUR_LOW, negativeStock: true, currentGrams: -500 }],
      [level({ currentUnits: -3, soldUnits: 27 })]
    )

    renderApp("/analytics")
    await loginAsGeneralManager()

    const product = await screen.findByRole("link", { name: /Almacén · Bebida cola/ })
    expect(product).toHaveAttribute("href", "/catalog/products")
    expect(screen.getByRole("link", { name: /Insumo · / })).toHaveAttribute("href", "/catalog/ingredients")
  })
})

describe("OverviewPage chart and shell polish", () => {
  it("names the revenue chart's scope after the branch filter", async () => {
    stubOverview([], [])

    renderApp("/analytics")
    await loginAsGeneralManager()

    // The general manager has no branch lock, so the filter starts on every branch.
    expect(await screen.findByText("Ingresos diarios de todas las sucursales.")).toBeInTheDocument()
  })

  it("has one main landmark and a Spanish sidebar toggle", async () => {
    stubOverview([], [])

    renderApp("/analytics")
    await loginAsGeneralManager()

    await screen.findByText("Por reponer · sin pendientes")
    expect(screen.getAllByRole("main")).toHaveLength(1)
    expect(screen.getByRole("button", { name: "Alternar barra lateral" })).toBeInTheDocument()
    expect(screen.queryByText("Toggle Sidebar")).not.toBeInTheDocument()
  })
})
