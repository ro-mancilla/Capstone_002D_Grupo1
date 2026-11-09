import type { ErrorBodyDto, RefreshResponse } from "./types"

/**
 * Fetch wrapper for the entire app -- JSON in/out, base URL from `VITE_API_BASE_URL`, JWT auth
 * header, single-flight refresh-on-401 with retry-once, hard-logout-and-redirect on refresh
 * failure (plan 07 §2). Components never call `fetch` directly; everything goes through this
 * module (or `api/queries/*` built on top of it, items 4+).
 *
 * Token storage (01 §4 / 07 §2, reconciled with 02): access token (~15 min) lives in memory,
 * module-scoped, and is lost on page reload by design -- a reload always re-hydrates via the
 * refresh token. The refresh token is a body-carried opaque rotating token (no cookies in 02's
 * flow), so `localStorage` is the accepted trade-off here (documented XSS exposure).
 *
 * Tabs share that one refresh token, and it rotates: two tabs posting it at once means one of them
 * is refused. So a refresh holds a cross-tab Web Lock, and the last access token is also written to
 * `localStorage` so a tab that waited on the lock can adopt what the winner got instead of posting
 * a token that was already spent (#132). That adds no exposure the refresh token does not already
 * carry: whoever can read one can read the other.
 */

const REFRESH_TOKEN_STORAGE_KEY = "ecopan-web-refresh-token"
const ACCESS_TOKEN_HANDOFF_KEY = "ecopan-web-access-token"
const REFRESH_LOCK_NAME = "ecopan-web-refresh"

let accessToken: string | null = null

/** Single-flight guard: concurrent 401s across in-flight requests share one refresh call
 * instead of each independently racing `/auth/refresh` (which would rotate the token N times
 * and fail N-1 of them). */
let refreshInFlight: Promise<RefreshOutcome> | null = null

/**
 * Invoked once, exactly when a refresh attempt fails outright (hard logout). Wired from
 * `main.tsx`/`__root.tsx` to clear the Query cache and `router.navigate` to `/login` -- kept as
 * an injectable callback (rather than importing the router here) so this module has no
 * dependency on the routing layer.
 */
let onAuthFailure: (() => void) | null = null

export function setAuthFailureHandler(handler: (() => void) | null): void {
  onAuthFailure = handler
}

/**
 * Invoked whenever *any* request 403s with an `ErrorBody.module` field populated -- i.e. a
 * `requireModule` rejection (01 §7.2), regardless of which module name it names. This is the
 * generic half of the "module toggled off mid-session" story (plan 07 §3/§11 item 7): wired from
 * `main.tsx`/test harnesses to invalidate the `["tenant"]` query, which re-syncs `Session` (see
 * `features/auth/AuthContext.tsx`'s `syncFromTenantProfile`) so nav re-filters and route guards
 * see the current entitlement on the next navigation -- without any per-module-name code here.
 */
let onModuleDisabled: ((module: string) => void) | null = null

export function setModuleDisabledHandler(handler: ((module: string) => void) | null): void {
  onModuleDisabled = handler
}

export function getAccessToken(): string | null {
  return accessToken
}

const accessTokenListeners = new Set<() => void>()

/** Notifies on every access-token change (restored after a reload, rotated, cleared). Shaped for
 * `useSyncExternalStore`, which is how `useServerEvents` learns the token has arrived. */
export function subscribeAccessToken(listener: () => void): () => void {
  accessTokenListeners.add(listener)
  return () => accessTokenListeners.delete(listener)
}

function setAccessToken(token: string | null): void {
  if (token === accessToken) return
  accessToken = token
  for (const listener of accessTokenListeners) listener()
}

/** How close to expiry a token is treated as already spent: a stream opened with it would 401 on
 * the next reconnect anyway. */
const ACCESS_TOKEN_REFRESH_MARGIN_MS = 60_000

/**
 * True when [token] is expired or within a minute of it, or carries no readable `exp`. Only the
 * payload is decoded -- the signature is the server's business; this just avoids a needless
 * refresh (and rotation) when a stream drops with a token that is still good.
 */
export function accessTokenNeedsRefresh(token: string, now: number = Date.now()): boolean {
  try {
    const payload = token.split(".")[1]
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"))
    const exp = (JSON.parse(json) as { exp?: unknown }).exp
    if (typeof exp !== "number") return true
    return exp * 1000 - now <= ACCESS_TOKEN_REFRESH_MARGIN_MS
  } catch {
    return true
  }
}

export function getRefreshToken(): string | null {
  if (typeof window === "undefined") return null
  return window.localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY)
}

export function setSessionTokens(tokens: { accessToken: string; refreshToken: string }): void {
  window.localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, tokens.refreshToken)
  window.localStorage.setItem(ACCESS_TOKEN_HANDOFF_KEY, tokens.accessToken)
  setAccessToken(tokens.accessToken)
}

export function clearSessionTokens(): void {
  window.localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY)
  window.localStorage.removeItem(ACCESS_TOKEN_HANDOFF_KEY)
  setAccessToken(null)
}

/** Runs [fn] holding the origin-wide refresh lock; without Web Locks, just runs it (the in-tab
 * single-flight in `refreshAccessToken` still applies). */
async function withRefreshLock<T>(fn: () => Promise<T>): Promise<T> {
  const locks = typeof navigator === "undefined" ? undefined : navigator.locks
  return locks ? locks.request(REFRESH_LOCK_NAME, fn) : fn()
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly module?: string

  constructor(status: number, body: ErrorBodyDto) {
    super(body.message ?? body.code)
    this.name = "ApiError"
    this.status = status
    this.code = body.code
    this.module = body.module
  }
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE"
  body?: unknown
  /** Login/refresh themselves must not attempt to attach a (possibly stale) auth header or
   * recurse into the refresh flow on their own 401. */
  skipAuth?: boolean
  /** Internal: set on the retry attempt so a second 401 fails hard instead of looping. */
  _isRetry?: boolean
}

/** Matches `.env.example`, so `npm run dev` against the local docker stack works with no `.env`. */
const DEFAULT_DEV_API_BASE_URL = "http://localhost:8080"

/**
 * Exported for `api/queries/events.ts` -- the SSE `EventSource` can't go through `request()`
 * (it isn't a `fetch` call), but it still needs the same base URL.
 *
 * Returning the env var raw meant a missing `.env` produced the *string* `"undefined"` once
 * interpolated, and requests went to `http://localhost:5173/undefined/auth/login` -- a 404 from
 * Vite's own dev server, which surfaces as a confusing generic login error rather than anything
 * pointing at configuration. Dev falls back to the documented default instead; a production build
 * fails loudly, because there is no sane default for a deployed dashboard and silently pointing it
 * at localhost would be worse than not starting.
 */
export function apiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL?.trim()
  if (configured) return configured.replace(/\/+$/, "")
  if (import.meta.env.PROD) {
    throw new Error("VITE_API_BASE_URL is not set — a production build needs an explicit backend URL.")
  }
  return DEFAULT_DEV_API_BASE_URL
}

async function parseErrorBody(response: Response): Promise<ErrorBodyDto> {
  try {
    const parsed = (await response.json()) as ErrorBodyDto
    if (parsed && typeof parsed.code === "string") return parsed
  } catch {
    // Body wasn't JSON (e.g. a proxy/network error page) -- fall through to the generic code.
  }
  return { code: response.status === 0 ? "NETWORK_ERROR" : "UNKNOWN_ERROR" }
}

/** `rejected`: the server refused the refresh token (or there is none) -- the session is over.
 * `unreachable`: the request never got an answer -- the session may be fine, so keep it. */
type RefreshOutcome = "refreshed" | "rejected" | "unreachable"

async function postRefresh(refreshToken: string): Promise<RefreshOutcome> {
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl()}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    })
  } catch {
    return "unreachable"
  }
  // Throttled (429) or a server hiccup (5xx) says nothing about the refresh token: keep the session.
  if (response.status === 429 || response.status >= 500) return "unreachable"
  if (!response.ok) return "rejected"
  try {
    const data = (await response.json()) as RefreshResponse
    setSessionTokens(data)
    return "refreshed"
  } catch {
    return "rejected"
  }
}

/** Posts to `/auth/refresh` and, on success, stores the rotated tokens (02 A2: refresh tokens
 * rotate on every use). Deduplicated via `refreshInFlight` so concurrent 401s await one call. */
async function refreshAccessToken(): Promise<RefreshOutcome> {
  if (refreshInFlight) return refreshInFlight

  const flight: Promise<RefreshOutcome> = (async (): Promise<RefreshOutcome> => {
    const seenRefreshToken = getRefreshToken()
    if (!seenRefreshToken) return "rejected"
    return withRefreshLock(async () => {
      const refreshToken = getRefreshToken()
      // Another tab signed out, or had this session refused, while we queued.
      if (!refreshToken) return "rejected"
      // Another tab refreshed while we queued: its tokens are the live ones, and posting our copy
      // of the refresh token would spend a value the server has already rotated away.
      const handedOff = window.localStorage.getItem(ACCESS_TOKEN_HANDOFF_KEY)
      if (refreshToken !== seenRefreshToken && handedOff) {
        setAccessToken(handedOff)
        return "refreshed"
      }
      return postRefresh(refreshToken)
    })
  })().finally(() => {
    refreshInFlight = null
  })

  refreshInFlight = flight
  return flight
}

/** Refreshes the session for callers outside `request` (the SSE stream); a rejected refresh ends
 * the session exactly as a failed request would. */
export async function refreshSession(): Promise<RefreshOutcome> {
  const outcome = await refreshAccessToken()
  if (outcome === "rejected") hardLogout()
  return outcome
}

function hardLogout(): void {
  clearSessionTokens()
  onAuthFailure?.()
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, skipAuth = false, _isRetry = false } = options

  // A reload drops the in-memory access token but keeps the refresh token, so the first requests
  // of a page load have nothing to authenticate with. Sending them anyway guarantees a 401 each
  // (console errors, every first call doubled). Wait for the one shared refresh instead.
  if (!skipAuth && !accessToken && getRefreshToken()) {
    const outcome = await refreshAccessToken()
    // A backend restart or a dropped connection during page load is not a dead session.
    if (outcome === "unreachable") throw new ApiError(0, { code: "NETWORK_ERROR" })
    if (outcome === "rejected") {
      hardLogout()
      throw new ApiError(401, { code: "SESSION_EXPIRED" })
    }
  }

  const headers: Record<string, string> = {}
  if (body !== undefined) headers["Content-Type"] = "application/json"
  if (!skipAuth && accessToken) headers["Authorization"] = `Bearer ${accessToken}`

  let response: Response
  try {
    response = await fetch(`${apiBaseUrl()}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, { code: "NETWORK_ERROR" })
  }

  if (response.status === 401 && !skipAuth && !_isRetry) {
    const outcome = await refreshAccessToken()
    if (outcome === "refreshed") {
      return request<T>(path, { ...options, _isRetry: true })
    }
    if (outcome === "unreachable") throw new ApiError(0, { code: "NETWORK_ERROR" })
    hardLogout()
    throw new ApiError(401, { code: "SESSION_EXPIRED" })
  }

  if (!response.ok) {
    const errorBody = await parseErrorBody(response)
    if (response.status === 403 && errorBody.module) {
      onModuleDisabled?.(errorBody.module)
    }
    throw new ApiError(response.status, errorBody)
  }

  // 202 (F2 forecast refresh) carries no body, like 204.
  if (response.status === 204 || response.status === 202) {
    return undefined as T
  }

  return (await response.json()) as T
}

export const http = {
  get: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "POST", body }),
  put: <T>(path: string, body?: unknown, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "PUT", body }),
  delete: <T>(path: string, options?: Omit<RequestOptions, "method" | "body">) =>
    request<T>(path, { ...options, method: "DELETE" }),
}
