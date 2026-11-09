import { expect, test, type Page } from "@playwright/test"
import { createProduct, login, pushBatch } from "../../shared/api"
import { signedInManager } from "./support"

const kg = (text: string) => parseFloat(text.replace(/−/g, "-").replace(/\./g, "").replace(",", ".").replace(/[^\d.-]/g, ""))
const rowOf = (page: Page, name: string) => page.getByRole("row", { name: new RegExp(`^${name}( |$)`) })

async function openIngredients(page: Page) {
  await signedInManager(page)
  await page.goto("/catalog/ingredients")
  await expect(page.getByRole("heading", { name: "Insumos" })).toBeVisible()
}

test.describe("ingredients, formulas and reconciliation (stories 24, 34-37)", () => {
  test("ingredients are created and edited with a low-stock level; stock states follow the ledger (34, 35, 37)", async ({ page }) => {
    const name = `e2e insumo ${Date.now()}`
    await openIngredients(page)

    await page.getByRole("button", { name: "Nuevo insumo" }).click()
    let dialog = page.getByRole("dialog")
    await dialog.getByLabel("Nombre").fill(name)
    await dialog.getByLabel("Nivel de reorden (kg)").fill("10")
    await dialog.getByRole("button", { name: "Crear insumo" }).click()
    await expect(rowOf(page, name)).toContainText("10,000 kg")

    await rowOf(page, name).getByRole("button", { name: "Editar" }).click()
    dialog = page.getByRole("dialog")
    await dialog.getByLabel("Nivel de reorden (kg)").fill("12")
    await dialog.getByRole("button", { name: "Guardar cambios" }).click()
    await expect(rowOf(page, name)).toContainText("12,000 kg")

    // A purchase of 5 kg is below the level of 12: "Stock bajo".
    await rowOf(page, name).getByRole("button", { name: /^(Stock|Registrar compra)$/ }).click()
    dialog = page.getByRole("dialog")
    const purchaseNote = `e2e compra ${Date.now()}`
    await dialog.getByLabel("Cantidad (kg)").fill("5")
    await dialog.getByLabel("Nota").first().fill(purchaseNote)
    await dialog.getByRole("button", { name: "Registrar ingreso" }).click()
    await expect(dialog.getByRole("row", { name: new RegExp(purchaseNote) })).toContainText("+5,000 kg")
    await expect(dialog.getByText("5,000 kg").first()).toBeVisible()
    await page.keyboard.press("Escape")
    await expect(rowOf(page, name)).toContainText("Stock bajo")

    // A manual adjustment of -8 kg takes it below zero: "Stock negativo".
    await rowOf(page, name).getByRole("button", { name: /^(Stock|Registrar compra)$/ }).click()
    dialog = page.getByRole("dialog")
    const reason = `e2e conteo ${Date.now()}`
    await dialog.getByLabel("Diferencia (kg, puede ser negativa)").fill("-8")
    await dialog.getByLabel("Motivo").fill(reason)
    await dialog.getByRole("button", { name: "Registrar ajuste" }).click()
    await expect(dialog.getByRole("row", { name: new RegExp(reason) })).toContainText("-8,000 kg")
    await expect(dialog.getByText("Stock negativo").first()).toBeVisible()
    await page.keyboard.press("Escape")
    await expect(rowOf(page, name)).toContainText("Stock negativo")
  })

  test("a product's formula is edited and shows its preview (36)", async ({ page }) => {
    const manager = await login("manager")
    const product = await createProduct(manager.accessToken, { name: `e2e fórmula ${Date.now()}`, soldBy: "WEIGHT", pricePerKgMinor: 2000 })
    await signedInManager(page)
    await page.goto("/catalog/products")
    await page.getByPlaceholder("Buscar por nombre…").fill(product.name)

    await page.getByRole("row", { name: new RegExp(product.name) }).getByRole("button", { name: "Crear fórmula" }).click()
    const dialog = page.getByRole("dialog")
    await dialog.getByRole("combobox", { name: "Agregar insumo" }).click()
    await page.getByRole("option", { name: "Agua" }).click()
    await dialog.getByLabel("Kg por kilo de harina -- Agua").fill("0.7")
    await dialog.getByLabel("Harina a usar (kg)").fill("10")
    await expect(dialog.getByRole("listitem").filter({ hasText: "Agua" })).toContainText("7,000 kg")
    await expect(dialog.getByRole("listitem").filter({ hasText: "Masa total" })).toContainText("17,000 kg")
    await dialog.getByRole("button", { name: "Guardar" }).click()
    await expect(dialog).toBeHidden()

    // The edit button now reads "Editar fórmula" and the saved percentage is still there.
    await page.getByRole("row", { name: new RegExp(product.name) }).getByRole("button", { name: "Editar fórmula" }).click()
    await expect(page.getByRole("dialog").getByLabel("Kg por kilo de harina -- Agua")).toHaveValue("0.7")
  })

  test("a batch pushed through the API balances in the day's reconciliation (24)", async ({ page }) => {
    const manager = await login("manager")
    const marraqueta = (await (await fetch(`${process.env.ECOPAN_E2E_API ?? "http://localhost:18080"}/catalog/products`, { headers: { Authorization: `Bearer ${manager.accessToken}` } })).json() as { id: string; name: string }[]).find((p) => p.name === "Marraqueta")!
    await signedInManager(page)
    await page.goto("/analytics/reconciliation")
    await page.getByRole("button", { name: "Este mes" }).click()
    const flour = rowOf(page, "Harina")
    const used = async () => Math.abs(kg(await flour.getByRole("cell").nth(3).innerText()))
    const batches = async () => kg(await flour.getByRole("cell").nth(6).innerText())
    await expect(flour).toBeVisible()
    const [usedBefore, batchesBefore] = [await used(), await batches()]

    // 16,8 kg of dough from the seeded Marraqueta formula uses 10 kg of flour; the worker books it asynchronously.
    await pushBatch({ productId: marraqueta.id, mixWeightGrams: 16_800 })
    await expect.poll(async () => {
      await page.reload()
      await page.getByRole("button", { name: "Este mes" }).click()
      return (await batches()) - batchesBefore
    }, { timeout: 90_000, intervals: [2_000] }).toBe(1)
    expect((await used()) - usedBefore).toBeCloseTo(10, 2)

    const rows = page.getByRole("table").getByRole("row")
    const count = await rows.count()
    for (let i = 1; i < count; i++) {
      const cells = await rows.nth(i).getByRole("cell").allInnerTexts()
      if (cells.length < 6) continue
      const [opening, purchases, formula, adjustments, closing] = cells.slice(1, 6).map(kg)
      // Apertura + Compras − Descuento por fórmula ± Ajustes = Cierre, to the gram.
      expect(opening + purchases - Math.abs(formula) + adjustments, `${cells[0]} balances`).toBeCloseTo(closing, 3)
    }
  })
})
