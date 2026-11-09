import { expect, test } from "@playwright/test"
import { createProduct, login, productNamed, pushBatch, recordSale } from "../../shared/api"
import { firstNumber, pickOption, signIn, signedInManager } from "./support"

test.describe("analytics (stories 20-23, 25-28)", () => {
  test("the overview counts a sale pushed through the API (20)", async ({ page }) => {
    await signedInManager(page)
    const counter = async () => firstNumber((await page.locator("main").innerText()).split("Transacciones de hoy")[1])
    await expect(page.getByText("Ventas de hoy")).toBeVisible()
    await expect(page.getByText(/^\$[\d.]+$/).first()).toBeVisible() // the numbers have loaded
    const before = await counter()
    const manager = await login("manager")
    await recordSale({ product: await createProduct(manager.accessToken, { name: `e2e overview ${Date.now()}`, priceMinor: 1500 }), quantity: 2 })
    await page.reload()
    await expect.poll(counter).toBe(before + 1)
    await expect(page.getByText("Ventas de hoy")).toBeVisible()
  })

  test("Ventas shows revenue by day and product type, and reacts to a new sale (21, 27)", async ({ page }) => {
    await signedInManager(page)
    await page.goto("/analytics/sales")
    await expect(page.getByRole("heading", { name: "Ingresos por día" })).toBeVisible()
    await expect(page.getByRole("heading", { name: "Ingresos por tipo de producto" })).toBeVisible()
    const row = page.getByRole("row", { name: /^Pastelería/ })
    const unitsBefore = firstNumber(await row.getByRole("cell").nth(2).innerText())

    const manager = await login("manager")
    const cake = await createProduct(manager.accessToken, { name: `e2e torta ${Date.now()}`, productType: "Pastelería", priceMinor: 2000 })
    await recordSale({ product: cake, quantity: 3 })
    await page.reload()
    await expect.poll(async () => firstNumber(await row.getByRole("cell").nth(2).innerText())).toBe(unitsBefore + 3)

    // The range buttons change the window: "Hoy" holds only what the suite sold today.
    await page.getByRole("button", { name: "Hoy" }).click()
    await expect(page.getByRole("row", { name: /^Pastelería/ })).toBeVisible()
    await page.getByRole("button", { name: "30 días" }).click()
    await expect(page.getByRole("row", { name: /^Pan/ })).toBeVisible()
  })

  test("filtering by branch narrows what is shown (27)", async ({ page }) => {
    await signedInManager(page)
    await page.goto("/analytics/sales")
    const all = await page.getByRole("row", { name: /^Pan / }).first().innerText()
    await pickOption(page, "Sucursal", "Sucursal Providencia")
    await expect(page.getByRole("combobox", { name: "Sucursal" })).toContainText("Sucursal Providencia")
    await expect.poll(async () => page.getByRole("row", { name: /^Pan / }).first().innerText()).not.toBe(all)
  })

  test("Producción lists a batch for the day and branch (22)", async ({ page }) => {
    await signedInManager(page)
    const manager = await login("manager")
    const marraqueta = await productNamed(manager.accessToken, "Marraqueta")
    const { branchId } = await pushBatch({ productId: marraqueta.id, mixWeightGrams: 7_000 })
    expect(branchId).toBeTruthy()
    await page.goto("/analytics/production")
    await page.getByRole("button", { name: "Hoy" }).click()
    await expect(page.getByText("Marraqueta").first()).toBeVisible()
    await expect(page.getByText(/No hay datos para el período/)).toHaveCount(0)
  })

  test("Merma shows the waste estimate with its caveat (23)", async ({ page }) => {
    await signedInManager(page)
    await page.goto("/analytics/waste")
    await expect(page.getByRole("heading", { name: "Mermas estimadas" })).toBeVisible()
    await expect(page.getByText("Es una estimación", { exact: false })).toBeVisible()
    await expect(page.getByText("Merma estimada del período")).toBeVisible()
    await expect(page.getByRole("table")).toBeVisible()
  })

  test("Pronóstico labels its estimator, shows accuracy, and refreshes on request (25, 26)", async ({ page }) => {
    await signedInManager(page)
    await page.goto("/analytics/forecast")
    await expect(page.getByRole("heading", { name: "Pronóstico vs. real (kg)" })).toBeVisible()
    await expect(page.getByText(/Estimador: (Línea base|Modelo)/).first()).toBeVisible()
    await expect(page.getByText("¿Qué tan cerca estuvo el pronóstico?").first()).toBeVisible()
    await expect(page.getByText("Últimas 4 semanas", { exact: false }).first()).toBeVisible()
    await expect(page.getByRole("heading", { name: /Producción sugerida para mañana/ })).toBeVisible()

    const refresh = page.getByRole("button", { name: "Actualizar pronóstico" })
    await refresh.click()
    await expect(refresh).toBeEnabled({ timeout: 60_000 })
    await expect(page.getByText(/Actualizado/)).toBeVisible()
  })

  test("a branch manager sees only their branch and cannot pick another (28)", async ({ page }) => {
    await signedInManager(page)
    await page.goto("/analytics/sales")
    await expect(page.getByRole("combobox", { name: "Sucursal" })).toBeVisible()
    const allBranches = firstNumber(await page.getByRole("row", { name: /^Pan / }).first().getByRole("cell").nth(3).innerText())
    await page.getByRole("button", { name: /Todas las sucursales/ }).click()
    await page.getByRole("menuitem", { name: "Cerrar sesión" }).click()

    await signIn(page, { persona: "branchManager" })
    await expect(page).toHaveURL(/\/analytics/)
    await page.goto("/analytics/sales")
    await expect(page.getByRole("heading", { name: "Ingresos por día" })).toBeVisible()
    await expect(page.getByRole("combobox", { name: "Sucursal" })).toHaveCount(0)
    await expect(page.getByText("Sucursal Principal").first()).toBeVisible()
    const ownBranch = firstNumber(await page.getByRole("row", { name: /^Pan / }).first().getByRole("cell").nth(3).innerText())
    expect(ownBranch).toBeLessThan(allBranches)
  })
})
