import { describe, expect, it, vi } from "vitest"
import { render, screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import type {
  CreateProductStockMovementRequest,
  FormulaDto,
  IngredientDto,
  ProductDto,
  ProductStockLevelDto,
} from "@/api/types"
import { t } from "@/lib/i18n"
import { ProductsPage } from "./ProductsPage"

const BRANCH_ID = "d7026124-76e6-4907-aaee-d497a08f908e"
const auth = vi.hoisted(() => ({
  session: { currencyCode: "CLP" } as Record<string, unknown>,
}))

vi.mock("@/features/auth/AuthContext", () => ({
  useAuth: () => ({ session: auth.session }),
}))

function signInAs(role: "GENERAL_MANAGER" | "BRANCH_MANAGER", withFormulas = false) {
  auth.session = {
    currencyCode: "CLP",
    enabledModules: withFormulas ? ["POS_SALES", "INVENTORY_FORMULAS"] : ["POS_SALES"],
    role,
    branchId: role === "BRANCH_MANAGER" ? BRANCH_ID : null,
    branches: [{ id: BRANCH_ID, name: "Casa Matriz" }],
  }
}

const base: ProductDto = {
  id: "",
  name: "",
  productType: "Pan",
  origin: "PANADERIA",
  soldBy: "WEIGHT",
  pricePerKgMinor: 2500,
  priceMinor: null,
  barcode: null,
  imageUrl: null,
  active: true,
  serverSeq: 1,
}

describe("ProductsPage Tipo column", () => {
  it("shows one Tipo column and no separate Origen", async () => {
    server.use(
      mswHttp.get(`${API_BASE_URL}/catalog/products`, () =>
        HttpResponse.json([
          { ...base, id: "a", name: "Marraqueta" },
          { ...base, id: "b", name: "Bebida cola", productType: "Almacén", origin: "ALMACEN", soldBy: "UNIT", pricePerKgMinor: null, priceMinor: 1500 },
        ])
      )
    )
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ProductsPage />
      </QueryClientProvider>
    )

    expect(await screen.findByRole("columnheader", { name: "Tipo" })).toBeInTheDocument()
    expect(screen.queryByRole("columnheader", { name: "Origen" })).not.toBeInTheDocument()
    const marraqueta = screen.getByText("Marraqueta").closest("tr")!
    const cola = screen.getByText("Bebida cola").closest("tr")!
    expect(within(marraqueta).getByText("Pan")).toBeInTheDocument()
    expect(within(cola).getByText("Almacén")).toBeInTheDocument()
    expect(screen.queryByText("ALMACEN")).not.toBeInTheDocument()
  })
})

const COLA: ProductDto = {
  ...base,
  id: "c0c0c0c0-0000-4000-8000-000000000001",
  name: "Bebida cola",
  productType: "Almacén",
  origin: "ALMACEN",
  soldBy: "UNIT",
  pricePerKgMinor: null,
  priceMinor: 1500,
}
const MARRAQUETA: ProductDto = { ...base, id: "a0a0a0a0-0000-4000-8000-000000000002", name: "Marraqueta" }

const COLA_LEVEL: ProductStockLevelDto = {
  productId: COLA.id,
  name: COLA.name,
  receivedUnits: 24,
  soldUnits: 20,
  currentUnits: 4,
  trackedSince: 1_700_000_000_000,
  lowStockThresholdUnits: 10,
  lowStock: true,
  active: true,
}

function renderPage() {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ProductsPage />
    </QueryClientProvider>
  )
}

function stubCatalog(level: ProductStockLevelDto | null = COLA_LEVEL) {
  server.use(
    mswHttp.get(`${API_BASE_URL}/catalog/products`, () => HttpResponse.json([MARRAQUETA, COLA])),
    mswHttp.get(`${API_BASE_URL}/inventory/product-stock`, () => HttpResponse.json(level ? [level] : [])),
    mswHttp.get(`${API_BASE_URL}/inventory/product-movements`, () => HttpResponse.json([]))
  )
}

/** Almacén stock lives on the product: seen in its row, recorded from its row (ticket #24). */
describe("ProductsPage Almacén stock", () => {
  it("shows current stock and a low-stock flag on Almacén rows only", async () => {
    signInAs("GENERAL_MANAGER")
    stubCatalog()
    renderPage()

    expect(await screen.findByRole("columnheader", { name: "Stock" })).toBeInTheDocument()
    const cola = (await screen.findByText("Bebida cola")).closest("tr")!
    expect(await within(cola).findByText("4 u.")).toBeInTheDocument()
    expect(within(cola).getByText("Stock bajo")).toBeInTheDocument()
    const marraqueta = screen.getByText("Marraqueta").closest("tr")!
    expect(within(marraqueta).queryByText(/u\./)).not.toBeInTheDocument()
    expect(within(marraqueta).queryByRole("button", { name: "Stock" })).not.toBeInTheDocument()
  })

  it("says Agotado at exactly 0 units, Stock bajo above 0 up to the reorder level (the till's states)", async () => {
    signInAs("GENERAL_MANAGER")
    stubCatalog({ ...COLA_LEVEL, currentUnits: 0, soldUnits: 24, lowStock: true })
    renderPage()

    const cola = (await screen.findByText("Bebida cola")).closest("tr")!
    expect(await within(cola).findByText("Agotado")).toBeInTheDocument()
    expect(within(cola).queryByText("Stock bajo")).not.toBeInTheDocument()
  })

  it("warns, in red, when stock is negative -- and never blocks anything", async () => {
    signInAs("GENERAL_MANAGER")
    stubCatalog({ ...COLA_LEVEL, currentUnits: -3, soldUnits: 27, lowStock: true })
    renderPage()

    const cola = (await screen.findByText("Bebida cola")).closest("tr")!
    expect(await within(cola).findByText("-3 u.")).toBeInTheDocument()
    expect(within(cola).getByText("Stock negativo")).toBeInTheDocument()
    expect(within(cola).queryByText("Stock bajo")).not.toBeInTheDocument()
  })

  it("records a purchase from the product's own Stock panel", async () => {
    signInAs("GENERAL_MANAGER")
    stubCatalog()
    let sent: CreateProductStockMovementRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/inventory/product-movements`, async ({ request }) => {
        sent = (await request.json()) as CreateProductStockMovementRequest
        return HttpResponse.json({ ...sent, createdBy: null, note: null }, { status: 201 })
      })
    )
    renderPage()
    const user = userEvent.setup()

    const cola = (await screen.findByText("Bebida cola")).closest("tr")!
    await user.click(await within(cola).findByRole("button", { name: "Stock" }))
    expect(await screen.findByRole("heading", { name: "Stock de Bebida cola" })).toBeInTheDocument()
    expect(await screen.findByTestId("current-stock")).toHaveTextContent("4 u.")
    expect(screen.getByText("Nivel de reorden:")).toBeInTheDocument()

    await user.type(screen.getByLabelText("Cantidad (unidades)"), "12")
    await user.click(screen.getByRole("button", { name: "Registrar ingreso" }))

    await waitFor(() => expect(sent).not.toBeNull())
    expect(sent!.productId).toBe(COLA.id)
    expect(sent!.branchId).toBe(BRANCH_ID)
    expect(sent!.kind).toBe("PURCHASE")
    expect(sent!.deltaUnits).toBe(12)
  })

  it("shows Agotado in the panel at 0 units", async () => {
    signInAs("GENERAL_MANAGER")
    stubCatalog({ ...COLA_LEVEL, currentUnits: 0, soldUnits: 24, lowStock: true })
    renderPage()
    const user = userEvent.setup()

    const cola = (await screen.findByText("Bebida cola")).closest("tr")!
    await user.click(await within(cola).findByRole("button", { name: "Stock" }))
    expect(await screen.findByTestId("current-stock")).toHaveTextContent("0 u.")
    expect(screen.getAllByText("Agotado").length).toBeGreaterThan(0)
    expect(screen.queryByText("Stock bajo")).not.toBeInTheDocument()
  })

  it("shows a negative-stock warning in the panel", async () => {
    signInAs("GENERAL_MANAGER")
    stubCatalog({ ...COLA_LEVEL, currentUnits: -5, soldUnits: 29 })
    renderPage()
    const user = userEvent.setup()

    const cola = (await screen.findByText("Bebida cola")).closest("tr")!
    await user.click(await within(cola).findByRole("button", { name: "Stock" }))

    const alert = await screen.findByText("Se vendieron más unidades de las registradas.", { exact: false })
    expect(alert).toBeInTheDocument()
    expect(screen.getByTestId("current-stock")).toHaveTextContent("-5 u.")
  })

  it("edits the reorder level from the panel", async () => {
    signInAs("GENERAL_MANAGER")
    stubCatalog()
    let sent: unknown = null
    server.use(
      mswHttp.put(`${API_BASE_URL}/inventory/product-stock/${COLA.id}/threshold`, async ({ request }) => {
        sent = await request.json()
        return HttpResponse.json({ productId: COLA.id, lowStockThresholdUnits: 8 })
      })
    )
    renderPage()
    const user = userEvent.setup()

    const cola = (await screen.findByText("Bebida cola")).closest("tr")!
    await user.click(await within(cola).findByRole("button", { name: "Stock" }))
    await user.click(await screen.findByRole("button", { name: "Editar nivel" }))
    const field = screen.getByLabelText("Nivel de reorden (unidades)")
    await user.clear(field)
    await user.type(field, "8")
    await user.click(screen.getByRole("button", { name: "Guardar nivel" }))

    await waitFor(() => expect(sent).toEqual({ thresholdUnits: 8 }))
  })

  it("lets a branch manager record stock but not create, edit or delete products", async () => {
    signInAs("BRANCH_MANAGER")
    stubCatalog()
    renderPage()

    const cola = (await screen.findByText("Bebida cola")).closest("tr")!
    expect(await within(cola).findByRole("button", { name: "Stock" })).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Nuevo producto" })).not.toBeInTheDocument()
    expect(within(cola).queryByRole("button", { name: "Editar" })).not.toBeInTheDocument()
    expect(within(cola).queryByRole("button", { name: "Eliminar" })).not.toBeInTheDocument()
  })

  it("gives a general manager every product action in the row", async () => {
    signInAs("GENERAL_MANAGER")
    stubCatalog()
    renderPage()

    const cola = (await screen.findByText("Bebida cola")).closest("tr")!
    expect(within(cola).getByRole("button", { name: "Stock" })).toBeInTheDocument()
    expect(within(cola).getByRole("button", { name: "Editar" })).toBeInTheDocument()
    expect(within(cola).getByRole("button", { name: "Eliminar" })).toBeInTheDocument()
    expect(screen.getByRole("button", { name: "Nuevo producto" })).toBeInTheDocument()
  })
})

/** A Panadería product's formula is edited from its own row (ticket #25); an Almacén one has none. */
describe("ProductsPage formula editing", () => {
  const FLOUR: IngredientDto = { id: "flour", name: "Harina", lowStockThresholdGrams: null, active: true }
  const FORMULA: FormulaDto = {
    id: "f1",
    productId: MARRAQUETA.id,
    lines: [{ id: "l1", ingredientId: FLOUR.id, basisPoints: 10000 }],
  }
  const DOBLADA: ProductDto = { ...base, id: "a0a0a0a0-0000-4000-8000-000000000003", name: "Doblada" }

  function stubFormulas(formulas: FormulaDto[] = [FORMULA]) {
    server.use(
      mswHttp.get(`${API_BASE_URL}/catalog/products`, () => HttpResponse.json([MARRAQUETA, DOBLADA, COLA])),
      mswHttp.get(`${API_BASE_URL}/inventory/product-stock`, () => HttpResponse.json([])),
      mswHttp.get(`${API_BASE_URL}/catalog/formulas`, () => HttpResponse.json(formulas)),
      mswHttp.get(`${API_BASE_URL}/inventory/ingredients`, () => HttpResponse.json([FLOUR]))
    )
  }

  it("offers the formula editor on Panadería rows only, labelled by whether a formula exists", async () => {
    signInAs("GENERAL_MANAGER", true)
    stubFormulas()
    renderPage()

    const marraqueta = (await screen.findByText("Marraqueta")).closest("tr")!
    expect(await within(marraqueta).findByRole("button", { name: t.admin.formulas.editFormula })).toBeInTheDocument()
    const doblada = screen.getByText("Doblada").closest("tr")!
    expect(within(doblada).getByRole("button", { name: t.admin.formulas.newFormula })).toBeInTheDocument()
    const cola = screen.getByText("Bebida cola").closest("tr")!
    expect(within(cola).queryByRole("button", { name: /fórmula/i })).not.toBeInTheDocument()
  })

  it("opens the editor from the row", async () => {
    signInAs("GENERAL_MANAGER", true)
    stubFormulas()
    renderPage()
    const user = userEvent.setup()

    const marraqueta = (await screen.findByText("Marraqueta")).closest("tr")!
    await user.click(await within(marraqueta).findByRole("button", { name: t.admin.formulas.editFormula }))

    expect(await screen.findByRole("heading", { name: new RegExp(t.admin.formulas.dialog.title) })).toBeInTheDocument()
  })

  it("shows no formula action without the module, or to a branch manager, and never asks for formulas", async () => {
    let asked = false
    stubFormulas()
    server.use(
      mswHttp.get(`${API_BASE_URL}/catalog/formulas`, () => {
        asked = true
        return HttpResponse.json([])
      })
    )

    signInAs("GENERAL_MANAGER", false)
    const first = render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ProductsPage />
      </QueryClientProvider>
    )
    await screen.findByText("Marraqueta")
    expect(screen.queryByRole("button", { name: /fórmula/i })).not.toBeInTheDocument()
    first.unmount()

    signInAs("BRANCH_MANAGER", true)
    renderPage()
    await screen.findByText("Marraqueta")
    expect(screen.queryByRole("button", { name: /fórmula/i })).not.toBeInTheDocument()
    expect(asked).toBe(false)
  })

  it("keeps the product list when the formula read fails", async () => {
    signInAs("GENERAL_MANAGER", true)
    stubFormulas()
    server.use(
      mswHttp.get(`${API_BASE_URL}/catalog/formulas`, () =>
        HttpResponse.json({ code: "MODULE_DISABLED", module: "INVENTORY_FORMULAS" }, { status: 403 })
      )
    )
    renderPage()

    expect(await screen.findByText("Marraqueta")).toBeInTheDocument()
    expect(screen.queryByRole("button", { name: /fórmula/i })).not.toBeInTheDocument()
  })
})

/** A failed read must not read as an empty bakery (issue #11, ported from the retired Fórmulas page). */
describe("ProductsPage read outcomes", () => {
  it("shows a Spanish failure with a retry instead of the empty state", async () => {
    signInAs("GENERAL_MANAGER")
    server.use(
      mswHttp.get(`${API_BASE_URL}/catalog/products`, () => HttpResponse.json({ code: "TENANT_NOT_FOUND" }, { status: 404 })),
      mswHttp.get(`${API_BASE_URL}/inventory/product-stock`, () => HttpResponse.json([]))
    )
    renderPage()

    expect(await screen.findByText(t.errors.codes.TENANT_NOT_FOUND)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: t.common.retry })).toBeInTheDocument()
    expect(screen.queryByText(t.admin.products.empty)).not.toBeInTheDocument()
  })
})
