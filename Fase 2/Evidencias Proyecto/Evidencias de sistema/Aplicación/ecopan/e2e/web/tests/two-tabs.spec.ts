import { expect, test } from "@playwright/test"
import { refreshTokenOf, signedInManager } from "./support"

// Story 17: two tabs share one refresh (Web Locks) instead of racing and revoking each other's family.
test("two tabs both survive a token refresh", async ({ context, page }) => {
  test.setTimeout(180_000)
  await signedInManager(page)
  const second = await context.newPage()
  await second.goto("/analytics")
  await expect(second.getByRole("heading", { name: "Resumen" }).first()).toBeVisible()
  const before = await refreshTokenOf(page)

  // Access tokens live one minute on the e2e stack (scripts/e2e/compose.yml). The clock is the thing under test, so the
  // only way to cross an expiry is to let it pass.
  const TOKEN_TTL_PLUS_MARGIN_MS = 65_000
  await page.waitForTimeout(TOKEN_TTL_PLUS_MARGIN_MS)
  await Promise.all([page.reload(), second.reload()])

  for (const tab of [page, second]) {
    await expect(tab).toHaveURL(/\/analytics/)
    await expect(tab.getByRole("heading", { name: "Resumen" }).first()).toBeVisible()
  }
  expect(await refreshTokenOf(page)).not.toBe(before)
  expect(await refreshTokenOf(second)).toBe(await refreshTokenOf(page))
})
