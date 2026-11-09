import { expect, test, type Page } from "@playwright/test"
import { createProduct, login } from "../../shared/api"
import { signedInManager } from "./support"

// A 1x1 PNG: a real image the backend's magic-byte check accepts.
const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", "base64")

async function openProducts(page: Page) {
  await signedInManager(page)
  await page.goto("/catalog/products")
  await expect(page.getByRole("heading", { name: "Productos" })).toBeVisible()
}
const rowOf = (page: Page, name: string) => page.getByRole("row", { name: new RegExp(name) })
async function search(page: Page, name: string) {
  await page.getByPlaceholder("Buscar por nombre…").fill(name)
}

test.describe("catalog (stories 29-33)", () => {
  test("a manager creates, edits and deactivates a product (29)", async ({ page }) => {
    const name = `e2e pan ${Date.now()}`
    await openProducts(page)

    await page.getByRole("button", { name: "Nuevo producto" }).click()
    const dialog = page.getByRole("dialog")
    await dialog.getByLabel("Nombre").fill(name)
    await dialog.getByLabel("Tipo").click()
    await page.getByRole("option", { name: "Pan" }).click()
    await dialog.getByLabel("Se vende por").click()
    await page.getByRole("option", { name: "Unidad" }).click()
    await dialog.getByLabel("Precio unitario").fill("1350")
    await dialog.getByLabel("Código de barras").fill(`78${Date.now()}`.slice(0, 13))
    await dialog.getByRole("button", { name: "Crear producto" }).click()

    await search(page, name)
    await expect(rowOf(page, name)).toContainText("$1.350")
    await expect(rowOf(page, name)).toContainText("Unidad")

    await rowOf(page, name).getByRole("button", { name: "Editar", exact: true }).click()
    await page.getByRole("dialog").getByLabel("Precio unitario").fill("1500")
    await page.getByRole("dialog").getByRole("button", { name: "Guardar cambios" }).click()
    await expect(rowOf(page, name)).toContainText("$1.500")

    page.once("dialog", (confirm) => confirm.accept()) // the browser's confirm(): "¿Eliminar este producto?"
    await rowOf(page, name).getByRole("button", { name: "Eliminar" }).click()
    await expect(page.getByRole("row", { name: new RegExp(name) })).toHaveCount(0)
    await page.getByRole("switch", { name: "Mostrar inactivos" }).click()
    await expect(rowOf(page, name)).toContainText("Inactivo")
  })

  test("a product photo uploads straight to storage and shows; a bad one is refused and the old one stays (30, 31, 32)", async ({ page }) => {
    const manager = await login("manager")
    const product = await createProduct(manager.accessToken, { name: `e2e foto ${Date.now()}` })
    await openProducts(page)
    await search(page, product.name)

    const edit = async () => {
      await rowOf(page, product.name).getByRole("button", { name: "Editar", exact: true }).click()
      return page.getByRole("dialog")
    }
    const upload = (dialog: ReturnType<Page["getByRole"]>, file: { name: string; mimeType: string; buffer: Buffer }) =>
      dialog.locator('input[type="file"]').setInputFiles(file)

    let dialog = await edit()
    await upload(dialog, { name: "pan.png", mimeType: "image/png", buffer: PNG })
    await dialog.getByRole("button", { name: "Guardar cambios" }).click()
    await expect(rowOf(page, product.name).locator("img")).toHaveAttribute("src", /^http:\/\/localhost:11000\//, { timeout: 30_000 })
    const goodSrc = await rowOf(page, product.name).locator("img").getAttribute("src")

    // Wrong type, caught in the form before anything is uploaded.
    dialog = await edit()
    await upload(dialog, { name: "notas.txt", mimeType: "text/plain", buffer: Buffer.from("hola") })
    await expect(dialog.getByText("La foto debe ser JPG, PNG o WebP.")).toBeVisible()
    await page.keyboard.press("Escape")
    await expect(rowOf(page, product.name).locator("img")).toHaveAttribute("src", goodSrc!)

    // Too big, caught in the form.
    dialog = await edit()
    await upload(dialog, { name: "enorme.png", mimeType: "image/png", buffer: Buffer.alloc(6 * 1024 * 1024, 1) })
    await expect(dialog.getByText("La foto no puede superar 5 MB.")).toBeVisible()
    await page.keyboard.press("Escape")

    // Claims to be a PNG but is not: storage takes it, the backend refuses to confirm it.
    dialog = await edit()
    await upload(dialog, { name: "falso.png", mimeType: "image/png", buffer: Buffer.from("esto no es una imagen") })
    await dialog.getByRole("button", { name: "Guardar cambios" }).click()
    await expect(page.getByText("Ese archivo no es una foto JPEG, PNG o WebP.")).toBeVisible({ timeout: 30_000 })
    await page.keyboard.press("Escape")
    await expect(rowOf(page, product.name).locator("img")).toHaveAttribute("src", goodSrc!)

    // Remove the photo.
    dialog = await edit()
    await dialog.getByRole("button", { name: "Quitar foto" }).click()
    await dialog.getByRole("button", { name: "Guardar cambios" }).click()
    await expect(rowOf(page, product.name).locator("img")).toHaveCount(0, { timeout: 30_000 })
  })

  test("Almacén stock is shown per branch and a count is recorded (33)", async ({ page }) => {
    await openProducts(page)
    const cola = rowOf(page, "Bebida Cola 500 ml")
    await expect(cola).toContainText(/\d+ u\./)
    const before = Number((await cola.innerText()).match(/(\d+) u\./)![1])

    await cola.getByRole("button", { name: "Stock" }).click()
    const panel = page.getByRole("dialog")
    await expect(panel.getByText("Stock actual")).toBeVisible()
    await panel.getByLabel("Cantidad (unidades)").fill("4")
    const note = `e2e compra ${Date.now()}`
    await panel.getByLabel("Nota").first().fill(note)
    await panel.getByRole("button", { name: "Registrar ingreso" }).click()
    await expect(panel.getByRole("row", { name: new RegExp(note) })).toContainText("+4 u.")
    await expect(panel.getByText(`${before + 4} u.`).first()).toBeVisible()
    await page.keyboard.press("Escape")
    await expect(rowOf(page, "Bebida Cola 500 ml")).toContainText(`${before + 4} u.`)
  })
})
