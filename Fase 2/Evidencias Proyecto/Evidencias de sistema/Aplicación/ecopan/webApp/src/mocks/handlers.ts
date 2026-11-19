import { http, HttpResponse } from "msw"
import type {
  LoginRequest,
  LoginResponse,
  RefreshRequest,
  RefreshResponse,
  TenantProfileResponse,
} from "@/api/types"

/** Shared fixture values so tests don't hand-roll UUIDs/DTO shapes repeatedly. Field names match
 * the real backend DTOs exactly (see api/types.ts's doc comment for the source files). */
export const FIXTURE = {
  tenant: {
    id: "d9571e52-01f5-42dd-b27a-347d12dde4c5",
    name: "Panadería Reference",
    slug: "reference",
    currencyCode: "CLP",
    enabledModules: ["POS_SALES", "INVENTORY_FORMULAS", "ANALYTICS_DASHBOARD"] as const,
  },
  branch: { id: "d7026124-76e6-4907-aaee-d497a08f908e", name: "Main", active: true },
  genMgr: {
    id: "a6c6de63-667a-4b5d-93a7-1ab1e3533ec1",
    displayName: "Gina General Manager",
    role: "GENERAL_MANAGER" as const,
    branchId: null,
  },
  cashier: {
    id: "c97b3300-d11f-49ad-b7b8-278a8331ea47",
    displayName: "Cami Cashier",
    role: "CASHIER" as const,
    branchId: "d7026124-76e6-4907-aaee-d497a08f908e",
  },
}

export const API_BASE_URL = "http://localhost:8080"

function loginResponse(user: typeof FIXTURE.genMgr, accessToken = "access-token-1"): LoginResponse {
  return {
    accessToken,
    refreshToken: "refresh-token-1",
    user,
    tenant: FIXTURE.tenant as unknown as LoginResponse["tenant"],
    branches: [FIXTURE.branch],
  }
}

function tenantProfileResponse(): TenantProfileResponse {
  return {
    id: FIXTURE.tenant.id,
    name: FIXTURE.tenant.name,
    slug: FIXTURE.tenant.slug,
    currencyCode: FIXTURE.tenant.currencyCode,
    enabledModules: [...FIXTURE.tenant.enabledModules],
    branches: [FIXTURE.branch],
  }
}

export const handlers = [
  http.post<never, LoginRequest>(`${API_BASE_URL}/auth/login`, async ({ request }) => {
    const body = await request.json()
    if (body.password !== "correct-password") {
      return HttpResponse.json({ code: "INVALID_CREDENTIALS" }, { status: 401 })
    }
    const user = body.username === "cashier" ? FIXTURE.cashier : FIXTURE.genMgr
    return HttpResponse.json(loginResponse(user))
  }),

  http.post<never, RefreshRequest>(`${API_BASE_URL}/auth/refresh`, async ({ request }) => {
    const body = await request.json()
    if (body.refreshToken === "expired-refresh-token") {
      return HttpResponse.json({ code: "TOKEN_INVALID" }, { status: 401 })
    }
    const response: RefreshResponse = {
      accessToken: "access-token-2",
      refreshToken: "refresh-token-2",
    }
    return HttpResponse.json(response)
  }),

  http.post(`${API_BASE_URL}/auth/logout`, () => new HttpResponse(null, { status: 204 })),

  // T1: default handler so every authed-route test (AppShell mounts `useTenantProfileSync`
  // unconditionally) has something to hit -- override with `server.use(...)` for module-toggle
  // specific assertions.
  http.get(`${API_BASE_URL}/tenant`, () => HttpResponse.json(tenantProfileResponse())),

]
