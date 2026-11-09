import { expect, test } from "@playwright/test"
import { changePrice, createProduct, login, productNamed, pushBatch, recordSale, revokeSession, setModule } from "../../shared/api"

// The helper is what every suite uses to play the other clients (story 7); prove each verb works.
test.describe("shared API helper (story 7)", () => {
  test("plays the baker, the cashier and the manager", async () => {
    const manager = await login("manager")
    const bread = await createProduct(manager.accessToken, { name: `e2e helper ${Date.now()}`, priceMinor: 1200 })

    expect((await recordSale({ product: bread, quantity: 2 })).totalMinor).toBe(2400)
    const repriced = await changePrice(manager.accessToken, bread, { priceMinor: 1500 })
    expect(repriced.priceMinor).toBe(1500)

    const marraqueta = await productNamed(manager.accessToken, "Marraqueta")
    expect((await pushBatch({ productId: marraqueta.id })).batchId).toBeTruthy()

    await setModule(manager.accessToken, "DEMAND_FORECASTING", false)
    expect((await login("manager")).tenant.enabledModules).not.toContain("DEMAND_FORECASTING")
    await setModule(manager.accessToken, "DEMAND_FORECASTING", true)
    expect((await login("manager")).tenant.enabledModules).toContain("DEMAND_FORECASTING")
  })

  test("revokes a session so its refresh token stops working", async () => {
    const session = await login("manager")
    await revokeSession(session)
    const res = await fetch(`${process.env.ECOPAN_E2E_API ?? "http://localhost:18080"}/auth/refresh`, {
      method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken: session.refreshToken }),
    })
    expect(res.status).toBe(401)
  })
})
