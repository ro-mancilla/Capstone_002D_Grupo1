import { expect, type Page } from "@playwright/test"
import { TENANT, USERS, type Persona } from "../../shared/api"

export async function signIn(page: Page, opts: { persona?: Persona; password?: string; forwardedFor?: string } = {}) {
  const user = USERS[opts.persona ?? "manager"]
  // The browser is the "client address" the auth rate limit keys on. Every sign-in gets its own address so a
  // long suite never trips the limit; a test that wants the limit passes a fixed one and fills its bucket.
  const address = opts.forwardedFor ?? `203.0.113.${1 + Math.floor(Math.random() * 250)}`
  await page.route("**/auth/login", (route) => route.continue({ headers: { ...route.request().headers(), "x-forwarded-for": address } }))
  await page.goto("/login")
  await page.getByLabel("Panadería").fill(TENANT)
  await page.getByLabel("Usuario").fill(user.username)
  await page.getByLabel("Contraseña").fill(opts.password ?? user.password)
  await page.getByRole("button", { name: "Ingresar" }).click()
}

export async function signedInManager(page: Page) {
  await signIn(page)
  await expect(page).toHaveURL(/\/analytics/)
}

export const refreshTokenOf = (page: Page) => page.evaluate(() => window.localStorage.getItem("ecopan-web-refresh-token"))

/** First integer in the text that follows a label, e.g. numberAfter("Cantidad vendida 397 u.") -> 397 (es-CL "1.234" -> 1234). */
export function firstNumber(text: string): number {
  const m = text.match(/-?\d[\d.]*/)
  if (!m) throw new Error(`no number in "${text}"`)
  return parseInt(m[0].replace(/\./g, ""), 10)
}

export async function pickOption(page: Page, combobox: string | RegExp, option: string | RegExp) {
  await page.getByRole("combobox", { name: combobox }).click()
  await page.getByRole("option", { name: option }).click()
}
