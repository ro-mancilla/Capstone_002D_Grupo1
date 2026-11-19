import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { act, renderHook } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { createElement, type ReactNode } from "react"
import { clearSessionTokens, setSessionTokens } from "@/api/http"
import { kpiKeys } from "./kpis"
import { inventoryKeys } from "./inventory"
import { tenantKeys } from "./tenant"
import { useServerEvents } from "./events"

vi.mock("@/features/auth/AuthContext", () => ({
  useAuth: () => ({ session: { branches: [] } }),
}))
vi.mock("@tanstack/react-router", () => ({
  useRouter: () => ({ navigate: vi.fn() }),
}))

class FakeEventSource {
  static instances: FakeEventSource[] = []
  closed = false
  private listeners = new Map<string, Set<() => void>>()
  constructor(public url: string) {
    FakeEventSource.instances.push(this)
  }
  addEventListener(type: string, fn: () => void) {
    if (!this.listeners.has(type)) this.listeners.set(type, new Set())
    this.listeners.get(type)!.add(fn)
  }
  removeEventListener(type: string, fn: () => void) {
    this.listeners.get(type)?.delete(fn)
  }
  close() {
    this.closed = true
  }
  emit(type: string) {
    this.listeners.get(type)?.forEach((fn) => fn())
  }
}

/** A JWT whose `exp` is ten minutes away; only its payload is read. */
function freshJwt(tag: string) {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, "")
  return `${b64({ alg: "none" })}.${b64({ exp: Math.floor(Date.now() / 1000) + 600, tag })}.sig`
}

describe("useServerEvents", () => {
  let queryClient: QueryClient
  const wrapper = ({ children }: { children: ReactNode }) => createElement(QueryClientProvider, { client: queryClient }, children)

  beforeEach(() => {
    queryClient = new QueryClient()
    FakeEventSource.instances = []
    vi.stubGlobal("EventSource", FakeEventSource)
    clearSessionTokens()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
    clearSessionTokens()
  })

  it("opens the stream once the access token is restored after a reload", () => {
    // A reload: the session is back (from storage) but the in-memory access token is not yet.
    renderHook(() => useServerEvents(), { wrapper })
    expect(FakeEventSource.instances).toHaveLength(0)

    act(() => setSessionTokens({ accessToken: freshJwt("a"), refreshToken: "r" }))

    expect(FakeEventSource.instances).toHaveLength(1)
    expect(FakeEventSource.instances[0].url).toContain(encodeURIComponent(freshJwt("a")))
  })

  it("closes the stream when the session's tokens are cleared", () => {
    setSessionTokens({ accessToken: freshJwt("a"), refreshToken: "r" })
    renderHook(() => useServerEvents(), { wrapper })
    expect(FakeEventSource.instances).toHaveLength(1)

    act(() => clearSessionTokens())

    expect(FakeEventSource.instances[0].closed).toBe(true)
  })

  it("a rotated token keeps the open stream (no reconnect churn)", () => {
    setSessionTokens({ accessToken: freshJwt("a"), refreshToken: "r" })
    renderHook(() => useServerEvents(), { wrapper })
    act(() => setSessionTokens({ accessToken: freshJwt("b"), refreshToken: "r2" }))
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(FakeEventSource.instances[0].closed).toBe(false)
  })

  it("on reopen refetches the live-data keys from the key factories, once", async () => {
    vi.useFakeTimers()
    setSessionTokens({ accessToken: freshJwt("a"), refreshToken: "r" })
    const invalidate = vi.spyOn(queryClient, "invalidateQueries")
    renderHook(() => useServerEvents(), { wrapper })

    FakeEventSource.instances[0].emit("error")
    await act(() => vi.advanceTimersByTimeAsync(1_000))
    FakeEventSource.instances[1].emit("open")

    const keys = invalidate.mock.calls.map(([filters]) => filters?.queryKey)
    expect(keys).toEqual([kpiKeys.all, inventoryKeys.stock, inventoryKeys.movements, tenantKeys.profile])
  })
})
