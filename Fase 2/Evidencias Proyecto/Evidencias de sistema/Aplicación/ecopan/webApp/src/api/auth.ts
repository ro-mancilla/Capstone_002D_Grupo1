import { getRefreshToken, http, setSessionTokens, clearSessionTokens } from "./http"
import type { LoginRequest, LoginResponse, LogoutRequest, Session } from "./types"

/**
 * The `Session` (profile data assembled from login) is not secret -- it's the same data `GET
 * /tenant` would return -- so, unlike the access/refresh tokens, it's fine to persist verbatim.
 * Persisting it is what lets a page reload restore "logged in" UI immediately (the refresh
 * token in `http.ts` is what actually re-authenticates; this is purely so the app shell doesn't
 * flash a login screen while that happens).
 */
const SESSION_STORAGE_KEY = "ecopan-web-session"

export function getStoredSession(): Session | null {
  if (typeof window === "undefined") return null
  const raw = window.localStorage.getItem(SESSION_STORAGE_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as Session
  } catch {
    return null
  }
}

export function storeSession(session: Session): void {
  window.localStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(session))
}

export function clearStoredSession(): void {
  window.localStorage.removeItem(SESSION_STORAGE_KEY)
}

/** A1 (02 §3): the one public, unauthenticated login call. On success, stores both tokens
 * (`http.ts` owns storage) and returns the raw response for the caller to assemble a `Session`
 * from (see `sessionFromLoginResponse`). */
export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>("/auth/login", request, { skipAuth: true })
  setSessionTokens(response)
  return response
}

/** A3 (02 §3): best-effort logout -- invalidates the refresh token server-side. Always clears
 * local tokens regardless of whether the network call succeeds (offline logout must still work
 * locally per plan 07 §7's online-only posture: a dropped connection should not trap the user
 * in a "logged in" state they can't get out of). */
export async function logout(): Promise<void> {
  const refreshToken = getRefreshToken()
  try {
    if (refreshToken) {
      const body: LogoutRequest = { refreshToken }
      await http.post<void>("/auth/logout", body)
    }
  } catch {
    // Network/auth failure on logout is not actionable -- we're clearing local state either way.
  } finally {
    clearSessionTokens()
  }
}

/** Assembles the client's `Session` (plan 07 §3) from `LoginResponse.user` + `.tenant` +
 * `.branches`. `tenantSlug` isn't part of `TenantSummaryDto` (only `TenantProfileResponse`, T1,
 * carries it) -- the login form already knows it, so the caller threads it through. */
export function sessionFromLoginResponse(response: LoginResponse, tenantSlug: string): Session {
  return {
    userId: response.user.id,
    tenantId: response.tenant.id,
    tenantSlug,
    branchId: response.user.branchId,
    role: response.user.role,
    displayName: response.user.displayName,
    tenantName: response.tenant.name,
    currencyCode: response.tenant.currencyCode,
    enabledModules: response.tenant.enabledModules,
    branches: response.branches,
  }
}
