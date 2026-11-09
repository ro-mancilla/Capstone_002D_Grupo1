import { expect, test } from "@playwright/test"
import { signIn } from "./support"

test.describe("session (stories 9, 10, 19, 20)", () => {
  test("a manager signs in, sees the day, and signs out", async ({ page }) => {
    await signIn(page)
    await expect(page).toHaveURL(/\/analytics/)
    await expect(page.getByRole("heading", { name: "Resumen" }).first()).toBeVisible()
    await expect(page.getByText("Ventas de hoy")).toBeVisible()
    await expect(page.getByText("Transacciones de hoy")).toBeVisible()

    await page.getByRole("button", { name: /Todas las sucursales/ }).click()
    await page.getByRole("menuitem", { name: "Cerrar sesión" }).click()
    await expect(page).toHaveURL(/\/login/)
    await expect(page.getByRole("button", { name: "Ingresar" })).toBeVisible()
  })

  test("a wrong password shows the Spanish message and stays on sign-in", async ({ page }) => {
    await signIn(page, { password: "no-es-la-clave" })
    await expect(page.getByText("Usuario, contraseña o panadería incorrectos.")).toBeVisible()
    await expect(page).toHaveURL(/\/login/)
  })
})
