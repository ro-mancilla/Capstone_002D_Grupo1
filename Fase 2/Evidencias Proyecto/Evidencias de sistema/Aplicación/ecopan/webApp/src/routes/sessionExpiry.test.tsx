import { afterEach, describe, expect, it, vi } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { http as mswHttp, HttpResponse } from "msw"
import { renderApp } from "@/test/router-utils"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"
import { managerSession } from "@/test/fixtures"
import { clearSessionTokens, request, setSessionTokens } from "@/api/http"
import { storeSession } from "@/api/auth"

/** Issue #76: a dead session used to nest `/login?redirect=/login?redirect=...` (once per failed
 * request) and, after signing in again, land on the first page instead of the one left. */
describe("session expiry (issue #76)", () => {
  afterEach(() => clearSessionTokens())

  function seedSignedInSession() {
    storeSession(managerSession())
    setSessionTokens({ accessToken: "stale-token", refreshToken: "expired-refresh-token" })
  }

  it("redirects once, carrying the original page, however many requests 401 together", async () => {
    server.use(
      mswHttp.get(`${API_BASE_URL}/*`, () => HttpResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 }))
    )
    seedSignedInSession()
    const { router } = renderApp("/analytics/reconciliation")
    const navigate = vi.spyOn(router, "navigate")

    await waitFor(() => expect(router.state.location.pathname).toBe("/login"))
    // More failures arrive after the first one has already logged out.
    await Promise.allSettled([request("/a"), request("/b"), request("/c")])
    await new Promise((resolve) => setTimeout(resolve, 50))

    expect(router.state.location.href).toBe("/login?redirect=%2Fanalytics%2Freconciliation")
    expect(navigate.mock.calls.filter(([opts]) => opts.to === "/login")).toHaveLength(1)
  })

  it("returns to the original page after logging in again", async () => {
    server.use(
      mswHttp.get(`${API_BASE_URL}/*`, ({ request: req }) =>
        req.headers.get("Authorization") === "Bearer access-token-1"
          ? HttpResponse.json([])
          : HttpResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 })
      )
    )
    seedSignedInSession()
    const { router } = renderApp("/analytics/reconciliation")

    await waitFor(() => expect(router.state.location.pathname).toBe("/login"))
    const user = userEvent.setup()
    await screen.findByText(/iniciar sesión/i)
    await user.type(screen.getByLabelText(/panadería/i), "reference")
    await user.type(screen.getByLabelText(/usuario/i), "genmgr")
    await user.type(screen.getByLabelText(/contraseña/i), "correct-password")
    await user.click(screen.getByRole("button", { name: /ingresar/i }))

    await waitFor(() => expect(router.state.location.pathname).toBe("/analytics/reconciliation"))
  })

  it("sends no request before the page-load refresh finishes, so none 401s", async () => {
    storeSession(managerSession())
    // A reload: refresh token survives, the in-memory access token does not.
    window.localStorage.setItem("ecopan-web-refresh-token", "refresh-token-1")
    const seen: (string | null)[] = []
    server.use(
      mswHttp.get(`${API_BASE_URL}/probe`, ({ request: req }) => {
        const auth = req.headers.get("Authorization")
        seen.push(auth)
        return auth === "Bearer access-token-2"
          ? HttpResponse.json({ ok: true })
          : HttpResponse.json({ code: "SESSION_EXPIRED" }, { status: 401 })
      })
    )

    await Promise.all([request("/probe"), request("/probe"), request("/probe")])

    expect(seen).toEqual(Array(3).fill("Bearer access-token-2"))
  })

  it("keeps the session when the page-load refresh cannot reach the server", async () => {
    storeSession(managerSession())
    window.localStorage.setItem("ecopan-web-refresh-token", "refresh-token-1")
    server.use(mswHttp.post(`${API_BASE_URL}/auth/refresh`, () => HttpResponse.error()))

    await expect(request("/probe")).rejects.toMatchObject({ status: 0, code: "NETWORK_ERROR" })

    // A backend restart or a Wi-Fi blip is not a dead session: the refresh token is still there.
    expect(window.localStorage.getItem("ecopan-web-refresh-token")).toBe("refresh-token-1")
  })
})
