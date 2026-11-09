import { expect, test, type Page } from "@playwright/test"
import { login, setModule } from "../../shared/api"
import { signedInManager } from "./support"

const rowOf = (page: Page, text: string) => page.getByRole("row", { name: new RegExp(text) })

test.describe("organization and modules (stories 38-42)", () => {
  test("a manager creates, edits and deactivates a user with a role and branch (38)", async ({ page }) => {
    const username = `e2e${Date.now()}`
    await signedInManager(page)
    await page.goto("/organization/users")

    await page.getByRole("button", { name: "Nuevo usuario" }).click()
    let dialog = page.getByRole("dialog")
    await dialog.getByLabel("Usuario").fill(username)
    await dialog.getByLabel("Nombre para mostrar").fill("Persona de Prueba")
    await dialog.getByRole("combobox", { name: "Rol" }).click()
    await page.getByRole("option", { name: "Cajero" }).click()
    await dialog.getByRole("combobox", { name: "Sucursal" }).click()
    await page.getByRole("option", { name: "Sucursal Providencia" }).click()
    await dialog.getByLabel("Contraseña").fill("clave-de-prueba-1")
    await dialog.getByLabel("PIN").fill("2468")
    await dialog.getByRole("button", { name: "Crear usuario" }).click()
    await expect(rowOf(page, username)).toContainText("Cajero")
    await expect(rowOf(page, username)).toContainText("Sucursal Providencia")

    await rowOf(page, username).getByRole("button", { name: "Editar" }).click()
    dialog = page.getByRole("dialog")
    await dialog.getByLabel("Nombre para mostrar").fill("Persona Editada")
    await dialog.getByRole("button", { name: "Guardar cambios" }).click()
    await expect(rowOf(page, username)).toContainText("Persona Editada")

    await rowOf(page, username).getByRole("button", { name: "Editar" }).click()
    dialog = page.getByRole("dialog")
    await dialog.getByRole("switch", { name: "Activo" }).click()
    await dialog.getByRole("button", { name: "Guardar cambios" }).click()
    await expect(rowOf(page, username)).toContainText("Inactivo")
  })

  test("a manager creates and renames a branch (39)", async ({ page }) => {
    const name = `Sucursal e2e ${Date.now()}`
    await signedInManager(page)
    await page.goto("/organization/branches")

    await page.getByRole("button", { name: "Nueva sucursal" }).click()
    let dialog = page.getByRole("dialog")
    await dialog.getByLabel("Nombre").fill(name)
    await dialog.getByRole("button", { name: "Crear sucursal" }).click()
    await expect(rowOf(page, name)).toContainText("Activa")

    await rowOf(page, name).getByRole("button", { name: "Editar" }).click()
    dialog = page.getByRole("dialog")
    await dialog.getByLabel("Nombre").fill(`${name} renombrada`)
    await dialog.getByRole("button", { name: "Guardar cambios" }).click()
    await expect(rowOf(page, `${name} renombrada`)).toBeVisible()
  })

  test("the tenant page names the bakery, its currency, modules and branches (40)", async ({ page }) => {
    await signedInManager(page)
    await page.goto("/organization/tenant")
    await expect(page.getByRole("heading", { name: "Panadería", exact: true })).toBeVisible()
    await expect(page.getByText("Panadería El Sol")).toBeVisible()
    await expect(page.getByText("reference", { exact: true })).toBeVisible()
    await expect(page.getByRole("heading", { name: "Módulos habilitados" })).toBeVisible()
    await expect(page.getByRole("listitem").filter({ hasText: "Sucursal Principal" })).toBeVisible()
  })

  test("a module switched off disappears from navigation and its page explains itself, then returns (41, 42)", async ({ page }) => {
    const manager = await login("manager")
    await signedInManager(page)
    const nav = page.getByRole("link", { name: "Pronóstico" })
    await expect(nav).toBeVisible()

    await page.goto("/organization/modules")
    try {
      await page.getByRole("switch", { name: "Pronóstico de demanda" }).click()
      await expect(page.getByRole("switch", { name: "Pronóstico de demanda" })).not.toBeChecked()
      await expect(nav).toHaveCount(0)

      await page.goto("/analytics/forecast")
      // The guard turns a deep link away to a page the session can open, not to an error page.
      await expect(page).not.toHaveURL(/forecast/)
      await expect(page.getByRole("heading", { name: "Resumen" }).first()).toBeVisible()
    } finally {
      await setModule(manager.accessToken, "DEMAND_FORECASTING", true)
    }
    await page.goto("/analytics")
    await expect(page.getByRole("link", { name: "Pronóstico" })).toBeVisible()
  })

  test("a page whose module is off explains that instead of erroring (42)", async ({ page }) => {
    const manager = await login("manager")
    await signedInManager(page)
    await setModule(manager.accessToken, "INVENTORY_FORMULAS", false)
    try {
      await page.goto("/catalog/ingredients")
      await expect(page.getByText(/Este módulo no está habilitado para tu panadería|Un administrador debe habilitar este módulo/).first()).toBeVisible({ timeout: 15_000 })
    } finally {
      await setModule(manager.accessToken, "INVENTORY_FORMULAS", true)
    }
  })
})
