import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { http as mswHttp, HttpResponse } from "msw"
import { server } from "@/mocks/server"
import { API_BASE_URL } from "@/mocks/handlers"

/**
 * #132: two dashboard tabs share one refresh token (localStorage) but each has its own copy of
 * `http.ts`. Each tab is simulated as a fresh module instance; Web Locks is faked with an in-memory
 * queue shared by both, as the browser's is shared by every tab of the origin.
 */
type Http = typeof import("./http")

async function openTab(): Promise<Http> {
  vi.resetModules()
  return import("./http")
}

function fakeLocks() {
  const queues = new Map<string, Promise<unknown>>()
  return {
    request: <T>(name: string, callback: () => Promise<T>): Promise<T> => {
      const previous = queues.get(name) ?? Promise.resolve()
      const run = previous.then(callback, callback)
      queues.set(name, run.catch(() => undefined))
      return run
    },
  }
}

describe("refresh across tabs (#132)", () => {
  let refreshCalls: string[]

  beforeEach(() => {
    refreshCalls = []
    let n = 0
    server.use(
      mswHttp.post(`${API_BASE_URL}/auth/refresh`, async ({ request }) => {
        const { refreshToken } = (await request.json()) as { refreshToken: string }
        refreshCalls.push(refreshToken)
        // Rotation: only the current token is accepted, and only once.
        if (refreshToken !== `refresh-${n}`) return HttpResponse.json({ code: "INVALID_REFRESH_TOKEN" }, { status: 401 })
        n += 1
        await new Promise((resolve) => setTimeout(resolve, 20))
        return HttpResponse.json({ accessToken: `access-${n}`, refreshToken: `refresh-${n}` })
      })
    )
    window.localStorage.clear()
  })

  afterEach(() => {
    Reflect.deleteProperty(navigator, "locks")
    window.localStorage.clear()
  })

  it("two tabs refreshing at once post the refresh token exactly once, and both keep the session", async () => {
    Object.defineProperty(navigator, "locks", { value: fakeLocks(), configurable: true })
    const tabA = await openTab()
    tabA.setSessionTokens({ accessToken: "access-0", refreshToken: "refresh-0" })
    const tabB = await openTab()
    const failures = vi.fn()
    tabA.setAuthFailureHandler(failures)
    tabB.setAuthFailureHandler(failures)

    const [a, b] = await Promise.all([tabA.refreshSession(), tabB.refreshSession()])

    expect(refreshCalls).toEqual(["refresh-0"])
    expect([a, b]).toEqual(["refreshed", "refreshed"])
    expect(tabA.getAccessToken()).toBe("access-1")
    expect(tabB.getAccessToken()).toBe("access-1")
    expect(window.localStorage.getItem("ecopan-web-refresh-token")).toBe("refresh-1")
    expect(failures).not.toHaveBeenCalled()
  })

  it("without Web Locks each tab still single-flights its own refreshes", async () => {
    const tab = await openTab()
    tab.setSessionTokens({ accessToken: "access-0", refreshToken: "refresh-0" })

    const outcomes = await Promise.all([tab.refreshSession(), tab.refreshSession(), tab.refreshSession()])

    expect(refreshCalls).toEqual(["refresh-0"])
    expect(outcomes).toEqual(["refreshed", "refreshed", "refreshed"])
  })
})
