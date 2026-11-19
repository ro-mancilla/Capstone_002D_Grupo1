import { afterEach, describe, expect, it, vi } from "vitest"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import {
  ApiError,
  apiBaseUrl,
  clearSessionTokens,
  getAccessToken,
  getRefreshToken,
  refreshSession,
  request,
  setAuthFailureHandler,
  setSessionTokens,
} from "./http"

const PROTECTED_PATH = "/protected-resource"

afterEach(() => {
  clearSessionTokens()
  setAuthFailureHandler(null)
})

/**
 * A missing `.env` used to send every request to `http://localhost:5173/undefined/auth/login`: the
 * env var was returned raw and interpolated into a template literal, so it stringified to
 * `"undefined"` and Vite's own dev server answered the 404. Nothing in the resulting login error
 * pointed at configuration.
 */
describe("apiBaseUrl", () => {
  const original = import.meta.env.VITE_API_BASE_URL

  afterEach(() => {
    import.meta.env.VITE_API_BASE_URL = original
  })

  it("falls back to the local stack when unset, never the string 'undefined'", () => {
    // Deleted rather than assigned `undefined`: `import.meta.env` coerces assigned values to
    // strings, which would store the literal "undefined" and not reproduce an absent variable.
    delete import.meta.env.VITE_API_BASE_URL

    expect(apiBaseUrl()).toBe("http://localhost:8080")
    expect(`${apiBaseUrl()}/auth/login`).not.toContain("undefined")
  })

  it("treats a blank value the same as unset", () => {
    import.meta.env.VITE_API_BASE_URL = "   "

    expect(apiBaseUrl()).toBe("http://localhost:8080")
  })

  it("trims trailing slashes so paths don't double up", () => {
    import.meta.env.VITE_API_BASE_URL = "https://api.example.com/"

    expect(`${apiBaseUrl()}/auth/login`).toBe("https://api.example.com/auth/login")
  })
})

describe("http.ts refresh flow (plan 07 §2/§10)", () => {
  it("401 -> single-flight refresh -> retries the original request once, with the new token", async () => {
    setSessionTokens({ accessToken: "stale-token", refreshToken: "still-valid-refresh-token" })

    let call = 0
    const seenAuthHeaders: (string | null)[] = []
    server.use(
      mswHttp.get(`${API_BASE_URL}${PROTECTED_PATH}`, ({ request: req }) => {
        call += 1
        seenAuthHeaders.push(req.headers.get("Authorization"))
        if (call === 1) {
          return HttpResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 })
        }
        return HttpResponse.json({ ok: true })
      })
    )

    const result = await request<{ ok: boolean }>(PROTECTED_PATH)

    expect(result).toEqual({ ok: true })
    expect(call).toBe(2)
    expect(seenAuthHeaders).toEqual(["Bearer stale-token", "Bearer access-token-2"])
    expect(getAccessToken()).toBe("access-token-2")
  })

  it("dedupes concurrent 401s into one /auth/refresh call", async () => {
    setSessionTokens({ accessToken: "stale-token", refreshToken: "still-valid-refresh-token" })

    let refreshCalls = 0
    server.use(
      mswHttp.post(`${API_BASE_URL}/auth/refresh`, () => {
        refreshCalls += 1
        return HttpResponse.json({ accessToken: "access-token-2", refreshToken: "refresh-token-2" })
      }),
      mswHttp.get(`${API_BASE_URL}${PROTECTED_PATH}`, ({ request: req }) => {
        const isFresh = req.headers.get("Authorization") === "Bearer access-token-2"
        return isFresh
          ? HttpResponse.json({ ok: true })
          : HttpResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 })
      })
    )

    await Promise.all([request(PROTECTED_PATH), request(PROTECTED_PATH), request(PROTECTED_PATH)])

    expect(refreshCalls).toBe(1)
  })

  it("refresh failure -> hard logout: clears tokens and invokes the auth-failure handler", async () => {
    setSessionTokens({ accessToken: "stale-token", refreshToken: "expired-refresh-token" })

    const onAuthFailure = vi.fn()
    setAuthFailureHandler(onAuthFailure)

    server.use(
      mswHttp.get(`${API_BASE_URL}${PROTECTED_PATH}`, () =>
        HttpResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 })
      )
    )

    await expect(request(PROTECTED_PATH)).rejects.toBeInstanceOf(ApiError)

    expect(onAuthFailure).toHaveBeenCalledTimes(1)
    expect(getAccessToken()).toBeNull()
  })

  it("a throttled or failing refresh (429/5xx) keeps the session; only a refusal ends it", async () => {
    for (const status of [429, 503]) {
      setSessionTokens({ accessToken: "stale-token", refreshToken: "valid-refresh-token" })
      const onAuthFailure = vi.fn()
      setAuthFailureHandler(onAuthFailure)
      server.use(mswHttp.post(`${API_BASE_URL}/auth/refresh`, () => new HttpResponse(null, { status })))

      expect(await refreshSession()).toBe("unreachable")

      expect(onAuthFailure).not.toHaveBeenCalled()
      expect(getRefreshToken()).toBe("valid-refresh-token")
    }
  })

  it("surfaces a typed ApiError for non-2xx, non-401 responses", async () => {
    server.use(
      mswHttp.get(`${API_BASE_URL}${PROTECTED_PATH}`, () =>
        HttpResponse.json({ code: "MODULE_DISABLED", module: "INVENTORY_FORMULAS" }, { status: 403 })
      )
    )

    const error = await request(PROTECTED_PATH).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(403)
    expect(error.code).toBe("MODULE_DISABLED")
    expect(error.module).toBe("INVENTORY_FORMULAS")
  })
})
