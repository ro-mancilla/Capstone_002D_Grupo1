/**
 * The other side of a cross-client journey (#145): any e2e suite plays the baker, the cashier or the
 * manager through the real HTTP API, the way scripts/acceptance/run.sh and scripts/demo-seed.py do.
 * Every call hits the throwaway stack (ECOPAN_E2E_API, default http://localhost:18080).
 */
import { randomUUID } from "node:crypto"

export const API = process.env.ECOPAN_E2E_API ?? "http://localhost:18080"
export const TENANT = "reference"

export const USERS = {
  manager: { username: "gerente", password: "gerente123" },
  branchManager: { username: "jefelocal", password: "jefelocal123" },
  baker: { username: "panadero", password: "panadero123" },
  cashier: { username: "cajero", password: "cajero123" },
} as const
export type Persona = keyof typeof USERS

export interface Session {
  accessToken: string
  refreshToken: string
  user: { id: string; branchId: string | null; role: string }
  branches: { id: string; name: string }[]
  tenant: { enabledModules: string[] }
}

export class ApiFailure extends Error {
  constructor(readonly status: number, readonly body: unknown, what: string) {
    super(`${what} -> HTTP ${status}: ${JSON.stringify(body)}`)
  }
}

async function call<T>(method: string, path: string, opts: { token?: string; body?: unknown; headers?: Record<string, string> } = {}): Promise<T> {
  const res = await fetch(API + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(opts.token ? { Authorization: `Bearer ${opts.token}` } : {}),
      ...opts.headers,
    },
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  })
  const text = await res.text()
  const json = text ? JSON.parse(text) : null
  if (!res.ok) throw new ApiFailure(res.status, json, `${method} ${path}`)
  return json as T
}

/** Signs in as a seeded persona. Each call is its own device so sessions never share a family. */
export function login(persona: Persona, deviceId: string = `e2e-${persona}-${randomUUID()}`): Promise<Session> {
  const { username, password } = USERS[persona]
  return call<Session>("POST", "/auth/login", {
    body: { tenantSlug: TENANT, username, password, deviceId, deviceName: "e2e" },
    // A fresh forwarded address per login keeps the suites clear of the auth rate limit (#129).
    headers: { "X-Forwarded-For": `203.0.113.${Math.floor(Math.random() * 250) + 1}` },
  })
}

export interface Product { id: string; name: string; soldBy: string; productType: string; origin: string; priceMinor: number | null; pricePerKgMinor: number | null }

export async function products(token: string): Promise<Product[]> {
  return call<Product[]>("GET", "/catalog/products", { token })
}

export async function productNamed(token: string, name: string): Promise<Product> {
  const found = (await products(token)).find((p) => p.name === name)
  if (!found) throw new Error(`the reference seed has no product "${name}"`)
  return found
}

/** A product of the test's own, so a test never depends on another's data. */
export async function createProduct(token: string, fields: { name: string; productType?: string; soldBy?: "UNIT" | "WEIGHT"; priceMinor?: number; pricePerKgMinor?: number }): Promise<Product> {
  const soldBy = fields.soldBy ?? "UNIT"
  return call<Product>("POST", "/catalog/products", {
    token,
    body: {
      id: randomUUID(),
      name: fields.name,
      productType: fields.productType ?? "Pan",
      soldBy,
      priceMinor: soldBy === "UNIT" ? (fields.priceMinor ?? 1000) : undefined,
      pricePerKgMinor: soldBy === "WEIGHT" ? (fields.pricePerKgMinor ?? 2500) : undefined,
    },
  })
}

/** The manager edits a product's price (PUT keeps every other field as it is). */
export async function changePrice(token: string, product: Product, price: { priceMinor?: number; pricePerKgMinor?: number }): Promise<Product> {
  return call<Product>("PUT", `/catalog/products/${product.id}`, {
    token,
    body: {
      name: product.name,
      productType: product.productType,
      soldBy: product.soldBy,
      priceMinor: price.priceMinor ?? product.priceMinor ?? undefined,
      pricePerKgMinor: price.pricePerKgMinor ?? product.pricePerKgMinor ?? undefined,
    },
  })
}

export async function push(token: string, deviceId: string, mutations: { entityType: string; payload: unknown }[]): Promise<string[]> {
  const now = Date.now()
  const res = await call<{ results: { status: string; error?: { code: string } }[] }>("POST", "/sync/push", {
    token,
    body: {
      deviceId,
      mutations: mutations.map((m) => ({ mutationId: randomUUID(), entityType: m.entityType, opType: "CREATE", clientTimestamp: now, payload: m.payload })),
    },
  })
  return res.results.map((r) => r.status + (r.error ? `:${r.error.code}` : ""))
}

/** The baker logs a batch from their phone; the backend decrements and announces it asynchronously. */
export async function pushBatch(opts: { productId: string; mixWeightGrams?: number; baker?: Session }): Promise<{ batchId: string; branchId: string }> {
  const baker = opts.baker ?? (await login("baker"))
  const branchId = baker.user.branchId ?? baker.branches[0].id
  const batchId = randomUUID()
  const status = await push(baker.accessToken, `e2e-baker-${randomUUID()}`, [{
    entityType: "PRODUCTION_BATCH",
    payload: { id: batchId, branchId, bakerId: baker.user.id, productId: opts.productId, mixWeightGrams: opts.mixWeightGrams ?? 10_000, batchAt: Date.now(), movementsMinted: false },
  }])
  if (status[0] !== "APPLIED") throw new Error(`batch push was ${status[0]}`)
  return { batchId, branchId }
}

/** The cashier records a one-line sale on their till. */
export async function recordSale(opts: { product: Product; quantity?: number; weightGrams?: number; cashier?: Session; paymentMethod?: string }): Promise<{ saleId: string; totalMinor: number }> {
  const cashier = opts.cashier ?? (await login("cashier"))
  const branchId = cashier.user.branchId ?? cashier.branches[0].id
  const byWeight = opts.product.soldBy === "WEIGHT"
  const quantity = opts.quantity ?? 1
  const grams = opts.weightGrams ?? 500
  const unit = byWeight ? opts.product.pricePerKgMinor! : opts.product.priceMinor!
  const lineTotal = byWeight ? Math.round((unit * grams) / 1000) : unit * quantity
  const saleId = randomUUID()
  const status = await push(cashier.accessToken, `e2e-till-${randomUUID()}`, [{
    entityType: "SALE",
    payload: {
      id: saleId, branchId, cashierId: cashier.user.id, soldAt: Date.now(), paymentMethod: opts.paymentMethod ?? "CASH", totalMinor: lineTotal,
      lines: [{ id: randomUUID(), productId: opts.product.id, soldBy: opts.product.soldBy, weightGrams: byWeight ? grams : undefined, quantity: byWeight ? undefined : quantity, unitPriceMinor: unit, lineTotalMinor: lineTotal }],
    },
  }])
  if (status[0] !== "APPLIED") throw new Error(`sale push was ${status[0]}`)
  return { saleId, totalMinor: lineTotal }
}

/** Ends a session server-side: its refresh token stops working. */
export async function revokeSession(session: Session): Promise<void> {
  await call("POST", "/auth/logout", { token: session.accessToken, body: { refreshToken: session.refreshToken } })
}

/** Turns one module on or off for the tenant. */
export async function setModule(token: string, module: string, enabled: boolean): Promise<void> {
  const current = (await call<{ enabledModules: string[] }>("GET", "/tenant", { token })).enabledModules
  const next = enabled ? [...new Set([...current, module])] : current.filter((m) => m !== module)
  await call("PUT", "/tenant/modules", { token, body: { enabledModules: next } })
}
