import { describe, expect, it, vi } from "vitest"
import { screen, waitFor, within } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL, FIXTURE } from "@/mocks/handlers"
import type {
  CreateIngredientRequest,
  CreateStockMovementRequest,
  IngredientDto,
  StockLevelDto,
  UpdateIngredientRequest,
} from "@/api/types"
import { managerSession } from "@/test/fixtures"
import { renderRouterAt } from "@/test/router-utils"

const BRANCH_ID = FIXTURE.branch.id
const FLOUR: IngredientDto = { id: "f1e6de63-667a-4b5d-93a7-1ab1e3533ec1", name: "Harina", lowStockThresholdGrams: 5000, active: true }
const YEAST: IngredientDto = { id: "9a1c0e11-0000-4000-8000-000000000002", name: "Levadura", lowStockThresholdGrams: 1000, active: true }
const SALT: IngredientDto = { id: "9a1c0e11-0000-4000-8000-000000000003", name: "Sal", lowStockThresholdGrams: null, active: true }
const OLD: IngredientDto = { id: "9a1c0e11-0000-4000-8000-000000000004", name: "Centeno", lowStockThresholdGrams: null, active: false }

const LEVELS: StockLevelDto[] = [
  { ingredientId: FLOUR.id, name: "Harina", currentGrams: 20000, lowStock: false },
  { ingredientId: YEAST.id, name: "Levadura", currentGrams: 400, lowStock: true },
  { ingredientId: SALT.id, name: "Sal", currentGrams: -250, lowStock: false },
]

const auth = vi.hoisted(() => ({ session: null as unknown }))

// The page reads the session from `useAuth`; the router (real route tree, so the empty-state link
// resolves) gets the same session for its guards.
vi.mock("@/features/auth/AuthContext", () => ({
  useAuth: () => ({
    session: auth.session,
    login: vi.fn(),
    logout: vi.fn(),
    clearSession: vi.fn(),
    syncFromTenantProfile: vi.fn(),
  }),
}))

function stubApi(ingredients: IngredientDto[] = [FLOUR, YEAST, SALT, OLD], levels: StockLevelDto[] = LEVELS) {
  server.use(
    mswHttp.get(`${API_BASE_URL}/inventory/ingredients`, () => HttpResponse.json(ingredients)),
    mswHttp.get(`${API_BASE_URL}/inventory/stock`, () => HttpResponse.json(levels)),
    mswHttp.get(`${API_BASE_URL}/inventory/movements`, () => HttpResponse.json([]))
  )
}

function renderPage(role: "GENERAL_MANAGER" | "BRANCH_MANAGER" = "GENERAL_MANAGER") {
  const session = managerSession({ role, branchId: role === "BRANCH_MANAGER" ? BRANCH_ID : null })
  auth.session = session
  return renderRouterAt("/catalog/ingredients", session)
}

describe("Insumos page", () => {
  it("lists each ingredient with its stock, reorder level and status", async () => {
    stubApi()
    renderPage()

    const flour = (await screen.findByText("Harina")).closest("tr")!
    expect(within(flour).getByText("20,000 kg")).toBeInTheDocument()
    expect(within(flour).getByText("5,000 kg")).toBeInTheDocument()
    // A healthy row says nothing: no "OK" badge, and the plain Stock button.
    expect(within(flour).queryByText("OK")).not.toBeInTheDocument()
    expect(within(flour).queryByText(/Stock (bajo|negativo)/)).not.toBeInTheDocument()
    expect(within(flour).getByRole("button", { name: "Stock" })).toBeInTheDocument()

    await waitFor(() => expect(within(screen.getByText("Levadura").closest("tr")!).getByText("Stock bajo")).toBeInTheDocument())
    const salt = screen.getByText("Sal").closest("tr")!
    expect(within(salt).getByText("Stock negativo")).toBeInTheDocument()
    expect(within(salt).getByText("Sin punto")).toBeInTheDocument()
    // Flagged rows offer the fix directly.
    expect(within(salt).getByRole("button", { name: "Registrar compra" })).toBeInTheDocument()
    expect(within(screen.getByText("Levadura").closest("tr")!).getByRole("button", { name: "Registrar compra" })).toBeInTheDocument()
    // An inactive ingredient is never flagged low.
    expect(within(screen.getByText("Centeno").closest("tr")!).queryByRole("button", { name: "Registrar compra" })).not.toBeInTheDocument()
    expect(within(screen.getByText("Centeno").closest("tr")!).getByText("Inactivo")).toBeInTheDocument()
  })

  it("creates an ingredient with its reorder level from the page", async () => {
    stubApi()
    let body: CreateIngredientRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/inventory/ingredients`, async ({ request }) => {
        body = (await request.json()) as CreateIngredientRequest
        return HttpResponse.json({ ...FLOUR, id: body.id, name: body.name }, { status: 201 })
      })
    )
    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole("button", { name: "Nuevo insumo" }))
    await user.type(await screen.findByLabelText("Nombre"), "Azúcar")
    await user.type(screen.getByLabelText("Nivel de reorden (kg)"), "2")
    await user.click(screen.getByRole("button", { name: "Crear insumo" }))

    await waitFor(() => expect(body).not.toBeNull())
    expect(body!.name).toBe("Azúcar")
    expect(body!.lowStockThresholdGrams).toBe(2000)
  })

  it("edits an ingredient's reorder level", async () => {
    stubApi()
    let body: UpdateIngredientRequest | null = null
    server.use(
      mswHttp.put(`${API_BASE_URL}/inventory/ingredients/${FLOUR.id}`, async ({ request }) => {
        body = (await request.json()) as UpdateIngredientRequest
        return HttpResponse.json(FLOUR)
      })
    )
    renderPage()
    const user = userEvent.setup()

    const row = (await screen.findByText("Harina")).closest("tr")!
    await user.click(within(row).getByRole("button", { name: "Editar" }))
    const level = await screen.findByLabelText("Nivel de reorden (kg)")
    await user.clear(level)
    await user.type(level, "8")
    await user.click(screen.getByRole("button", { name: "Guardar cambios" }))

    await waitFor(() => expect(body).not.toBeNull())
    expect(body!.lowStockThresholdGrams).toBe(8000)
  })

  it("records a purchase for the row's ingredient without asking which ingredient", async () => {
    stubApi()
    let body: CreateStockMovementRequest | null = null
    server.use(
      mswHttp.post(`${API_BASE_URL}/inventory/movements`, async ({ request }) => {
        body = (await request.json()) as CreateStockMovementRequest
        return HttpResponse.json({ serverSeq: 1 }, { status: 201 })
      })
    )
    renderPage()
    const user = userEvent.setup()

    const row = (await screen.findByText("Harina")).closest("tr")!
    await user.click(within(row).getByRole("button", { name: "Stock" }))
    expect(await screen.findByText("Stock de Harina")).toBeInTheDocument()
    expect(screen.queryByLabelText("Insumo")).not.toBeInTheDocument()

    await user.type(await screen.findByLabelText("Cantidad (kg)"), "2.5")
    await user.click(screen.getByRole("button", { name: "Registrar ingreso" }))

    await waitFor(() => expect(body).not.toBeNull())
    expect(body).toMatchObject({ kind: "PURCHASE", deltaGrams: 2500, ingredientId: FLOUR.id, branchId: BRANCH_ID })
  })

  it("warns about negative stock inside the stock panel", async () => {
    stubApi()
    renderPage()
    const user = userEvent.setup()

    const row = (await screen.findByText("Sal")).closest("tr")!
    await user.click(within(row).getByRole("button", { name: "Registrar compra" }))

    expect(await screen.findByRole("alert")).toHaveTextContent("Stock negativo")
  })

  it("gives a branch manager the list and stock entry, but no create or edit controls", async () => {
    stubApi()
    renderPage("BRANCH_MANAGER")

    const row = (await screen.findByText("Harina")).closest("tr")!
    expect(within(row).getByRole("button", { name: "Stock" })).toBeInTheDocument()
    expect(within(row).queryByRole("button", { name: "Editar" })).not.toBeInTheDocument()
    expect(screen.queryByRole("button", { name: "Nuevo insumo" })).not.toBeInTheDocument()
  })

  it("points an empty page at where to create ingredients and where products live", async () => {
    stubApi([], [])
    renderPage()

    expect(await screen.findByText("Aún no hay insumos.")).toBeInTheDocument()
    expect(screen.getByText(/Crea el primero con «Nuevo insumo»/)).toBeInTheDocument()
    expect(screen.getByRole("link", { name: "Ir a Productos" })).toHaveAttribute("href", "/catalog/products")
  })

  it("tells a branch manager to ask an administrator when there are no ingredients", async () => {
    stubApi([], [])
    renderPage("BRANCH_MANAGER")

    expect(await screen.findByText("Pide a un administrador que cree los insumos.")).toBeInTheDocument()
  })
})
