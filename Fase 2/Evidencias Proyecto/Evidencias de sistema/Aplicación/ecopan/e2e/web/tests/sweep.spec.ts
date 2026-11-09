import { expect, test, type Page } from "@playwright/test"
import { signedInManager } from "./support"

// Every dashboard page, in both themes and at phone width. A raw wire code ("BRANCH_MANAGER",
// "DEMAND_FORECASTING") or an id on screen means a label is missing from the Spanish tables.
const PAGES = [
  "/analytics", "/analytics/sales", "/analytics/forecast", "/analytics/production", "/analytics/waste", "/analytics/reconciliation",
  "/catalog/products", "/catalog/ingredients",
  "/organization/users", "/organization/branches", "/organization/tenant", "/organization/modules",
]
const WIRE_CODE = /\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+\b/
const UUID = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/i
const ENGLISH_UI = /\b(Loading|Submit|Cancel|Save|Delete|Error|Something went wrong|No data)\b/

async function sweep(page: Page, label: string) {
  for (const path of PAGES) {
    await page.goto(path)
    await expect(page.locator("main")).toBeVisible()
    await page.waitForLoadState("networkidle")
    const text = await page.locator("body").innerText()
    expect(text, `${label} ${path}: a raw wire code`).not.toMatch(WIRE_CODE)
    expect(text, `${label} ${path}: a raw id`).not.toMatch(UUID)
    expect(text, `${label} ${path}: English UI text`).not.toMatch(ENGLISH_UI)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), `${label} ${path}: horizontal scroll`).toBe(true)
  }
}

test.describe("cross-cutting UI (stories 43-45)", () => {
  test("every page works in the light and the dark theme, in Spanish, with no raw codes (43, 44)", async ({ page }) => {
    await signedInManager(page)
    for (const [theme, dark] of [["Claro", false], ["Oscuro", true]] as const) {
      await page.getByRole("button", { name: "Cambiar tema" }).click()
      await page.getByRole("menuitem", { name: theme }).click()
      await expect(page.locator("html")).toHaveClass(dark ? /dark/ : /^(?!.*dark).*$/)
      await sweep(page, theme)
    }
  })

  test.describe("at phone width (45)", () => {
    test.use({ viewport: { width: 390, height: 844 } })
    test("the dashboard stays usable with the collapsed sidebar", async ({ page }) => {
      await signedInManager(page)
      await page.getByRole("button", { name: "Alternar barra lateral" }).click()
      await expect(page.getByRole("link", { name: "Ventas" })).toBeVisible()
      await page.getByRole("link", { name: "Ventas" }).click()
      await expect(page).toHaveURL(/\/analytics\/sales/)
      await sweep(page, "phone")
    })
  })
})
