import { expect, test } from "@playwright/test"
import { API, login, productNamed, pushBatch } from "../../shared/api"
import { startService, stopService, waitForApi } from "../../shared/stack"
import { refreshTokenOf, signIn, signedInManager } from "./support"

test.describe("session and live updates (stories 11-18)", () => {
  test("a throttled sign-in says to wait, distinct from a wrong password (11)", async ({ page }) => {
    const address = `198.51.100.${100 + Math.floor(Math.random() * 100)}`
    for (let i = 0; i < 40; i++) {
      const res = await fetch(`${API}/auth/login`, {
        method: "POST", headers: { "Content-Type": "application/json", "X-Forwarded-For": address },
        body: JSON.stringify({ tenantSlug: "reference", username: "nadie", password: "mal" }),
      })
      if (res.status === 429) break
    }
    await signIn(page, { forwardedFor: address })
    await expect(page.getByText(/Demasiados intentos|Espera un momento|demasiad/i)).toBeVisible()
    await expect(page.getByText("Usuario, contraseña o panadería incorrectos.")).toHaveCount(0)
  })

  test("a reload keeps the session and live updates resume; a batch raises Lote listo (12-15)", async ({ page }) => {
    await signedInManager(page)
    await page.reload()
    await expect(page).toHaveURL(/\/analytics/)
    await expect(page.getByRole("heading", { name: "Resumen" }).first()).toBeVisible()

    const manager = await login("manager")
    const marraqueta = await productNamed(manager.accessToken, "Marraqueta")
    await pushBatch({ productId: marraqueta.id, mixWeightGrams: 12_000 })

    const title = page.getByText(/Lote de Marraqueta listo · 12,000 kg de masa/)
    await expect(title).toBeVisible({ timeout: 30_000 })
    // The branch is named inside this very notice (the notice is the nearest ancestor holding its action button).
    const notice = title.locator("xpath=ancestor::*[.//button[normalize-space()='Ver producción']][1]")
    await expect(notice).toContainText("Sucursal Principal")
    await notice.getByRole("button", { name: "Ver producción" }).click()
    // The production page opens on today, for the baker's branch, marking that product.
    await expect(page).toHaveURL(/\/analytics\/production/)
    const url = new URL(page.url())
    expect(url.searchParams.get("highlight")).toBe(marraqueta.id)
    expect(url.searchParams.get("branchId")).toBeTruthy()
    expect(url.searchParams.get("today")).toBe("true")
  })

  test("En vivo en pausa shows while the backend is down and clears when it returns (16)", async ({ page }) => {
    await signedInManager(page)
    stopService("web")
    try {
      await expect(page.getByText("En vivo en pausa")).toBeVisible({ timeout: 45_000 })
    } finally {
      await startService("web")
    }
    await expect(page.getByText("En vivo en pausa")).toHaveCount(0, { timeout: 60_000 })
    await waitForApi()
    // And the stream really is live again: a batch pushed now raises its notice without a reload.
    const manager = await login("manager")
    const marraqueta = await productNamed(manager.accessToken, "Marraqueta")
    await pushBatch({ productId: marraqueta.id, mixWeightGrams: 13_000 })
    await expect(page.getByText(/Lote de Marraqueta listo · 13,000 kg de masa/)).toBeVisible({ timeout: 60_000 })
  })

  test("a revoked session returns to sign-in (18)", async ({ page }) => {
    test.setTimeout(180_000)
    await signedInManager(page)
    const refreshToken = await refreshTokenOf(page)
    expect(refreshToken).toBeTruthy()
    const access = (await login("manager")).accessToken
    await fetch(`${API}/auth/logout`, { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${access}` }, body: JSON.stringify({ refreshToken }) })
    // The access token lives one minute; once it expires the refresh is rejected and the dashboard lets go. Reload
    // until it does, rather than sleeping for a guessed time.
    await expect.poll(async () => {
      await page.reload()
      await page.waitForLoadState("networkidle")
      return new URL(page.url()).pathname
    }, { timeout: 150_000, intervals: [5_000] }).toBe("/login")
  })
})
