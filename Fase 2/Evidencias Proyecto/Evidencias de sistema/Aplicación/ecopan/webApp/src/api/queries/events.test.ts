import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"
import { QueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import { accessTokenNeedsRefresh } from "@/api/http"
import { applyServerEvent, openEventStream, resetBatchNoticeGroup } from "./events"
import { productsQuery } from "./catalog"

vi.mock("sonner", () => ({
  toast: Object.assign(vi.fn(), { success: vi.fn(), error: vi.fn() }),
}))

/**
 * Plan 07 §10's item-10 AC: "prove the invalidation-on-event-type logic works given a fake event
 * dispatch" -- exercised directly against `applyServerEvent` (the part `useServerEvents` delegates
 * to) rather than a real `EventSource`, which jsdom doesn't implement.
 */
function payload(overrides: Record<string, unknown> = {}) {
  return JSON.stringify({
    batchId: "batch-1",
    branchId: "branch-1",
    productId: "product-1",
    mixWeightGrams: 12_000,
    batchAt: 0,
    decrementStatus: "APPLIED",
    ...overrides,
  })
}

const NAMES = {
  productName: (id: string) => ({ "product-1": "Marraqueta", "product-2": "Hallulla", "product-3": "Berlín" })[id],
  branchName: (id: string) => (id === "branch-1" ? "Sucursal Principal" : id === "branch-2" ? "Sucursal Providencia" : undefined),
}

describe("applyServerEvent", () => {
  beforeEach(() => {
    vi.mocked(toast).mockClear()
    resetBatchNoticeGroup()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  it("batch-ready invalidates kpi/stock/movements and notifies with the product, mass and branch", async () => {
    const queryClient = new QueryClient()
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries")

    await applyServerEvent(queryClient, "batch-ready", payload(), NAMES)

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["kpi"] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["stock"] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["movements"] })
    expect(toast).toHaveBeenCalledWith(
      "Lote de Marraqueta listo · 12,000 kg de masa",
      expect.objectContaining({ description: "Sucursal Principal", duration: 6000 })
    )
  })

  it("names only the branch when the product cannot be resolved", async () => {
    await applyServerEvent(new QueryClient(), "batch-ready", payload(), { branchName: NAMES.branchName })

    expect(toast).toHaveBeenCalledWith("Lote listo en Sucursal Principal", expect.objectContaining({ description: undefined }))
  })

  it("batch-snapshot (the on-connect replay) repaints silently: invalidates but never toasts", async () => {
    const queryClient = new QueryClient()
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries")

    await applyServerEvent(queryClient, "batch-snapshot", payload(), NAMES)

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["kpi"] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["stock"] })
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["movements"] })
    expect(toast).not.toHaveBeenCalled()
  })

  it("a live batch-ready after a snapshot still toasts exactly once", async () => {
    const queryClient = new QueryClient()
    await applyServerEvent(queryClient, "batch-snapshot", payload(), NAMES)
    await applyServerEvent(queryClient, "batch-ready", payload(), NAMES)

    expect(toast).toHaveBeenCalledTimes(1)
  })

  it("uses neutral wording when nothing resolves and never shows an id", async () => {
    await applyServerEvent(new QueryClient(), "batch-ready", payload())

    expect(toast).toHaveBeenCalledWith("Hay un lote nuevo listo", expect.anything())
    expect(JSON.stringify(vi.mocked(toast).mock.calls)).not.toMatch(/branch-1|product-1|batch-1/)
  })

  it("resolves the product name from a cold cache via fetch", async () => {
    const queryClient = new QueryClient()
    const queryFn = vi.fn().mockResolvedValue([{ id: "product-1", name: "Marraqueta" }])
    vi.spyOn(queryClient, "ensureQueryData").mockImplementation(async () => queryFn())

    await applyServerEvent(queryClient, "batch-ready", payload(), {
      productName: async (id) => {
        const products = await queryClient.ensureQueryData(productsQuery(false))
        return (products as Array<{ id: string; name: string }>).find((p) => p.id === id)?.name
      },
    })

    expect(queryFn).toHaveBeenCalledTimes(1)
    expect(toast).toHaveBeenCalledWith("Lote de Marraqueta listo · 12,000 kg de masa", expect.anything())
  })

  it("survives a failing product fetch and keeps the known branch name", async () => {
    await applyServerEvent(new QueryClient(), "batch-ready", payload(), {
      productName: () => Promise.reject(new Error("offline")),
      branchName: NAMES.branchName,
    })

    expect(toast).toHaveBeenCalledWith("Lote listo en Sucursal Principal", expect.anything())
  })

  it("keeps the product and drops an unknown branch instead of showing the id", async () => {
    await applyServerEvent(new QueryClient(), "batch-ready", payload({ branchId: "branch-9" }), NAMES)

    expect(toast).toHaveBeenCalledWith(
      "Lote de Marraqueta listo · 12,000 kg de masa",
      expect.objectContaining({ description: undefined })
    )
  })

  it("groups batches arriving within 10 seconds into one notice, same toast id", async () => {
    vi.useFakeTimers()
    vi.setSystemTime(1_000_000)
    const queryClient = new QueryClient()
    await applyServerEvent(queryClient, "batch-ready", payload({ productId: "product-1" }), NAMES)
    vi.setSystemTime(1_004_000)
    await applyServerEvent(queryClient, "batch-ready", payload({ productId: "product-2" }), NAMES)
    vi.setSystemTime(1_008_000)
    await applyServerEvent(queryClient, "batch-ready", payload({ productId: "product-3" }), NAMES)

    const last = vi.mocked(toast).mock.calls.at(-1)!
    expect(last[0]).toBe("3 lotes nuevos listos")
    expect(last[1]).toEqual(
      expect.objectContaining({ id: "batch-ready", description: "Sucursal Principal · Marraqueta, Hallulla y 1 más" })
    )
    expect(new Set(vi.mocked(toast).mock.calls.map((call) => (call[1] as { id: string }).id)).size).toBe(1)
  })

  it("drops the branch from a grouped notice when the batches span branches", async () => {
    vi.useFakeTimers()
    vi.setSystemTime(1_000_000)
    const queryClient = new QueryClient()
    await applyServerEvent(queryClient, "batch-ready", payload(), NAMES)
    await applyServerEvent(queryClient, "batch-ready", payload({ branchId: "branch-2", productId: "product-2" }), NAMES)

    expect(toast).toHaveBeenLastCalledWith(
      "2 lotes nuevos listos",
      expect.objectContaining({ description: "Marraqueta y Hallulla" })
    )
  })

  it("starts a new notice once the 10 second window has passed", async () => {
    vi.useFakeTimers()
    vi.setSystemTime(1_000_000)
    const queryClient = new QueryClient()
    await applyServerEvent(queryClient, "batch-ready", payload(), NAMES)
    vi.setSystemTime(1_011_000)
    await applyServerEvent(queryClient, "batch-ready", payload({ productId: "product-2" }), NAMES)

    expect(toast).toHaveBeenLastCalledWith("Lote de Hallulla listo · 12,000 kg de masa", expect.anything())
  })

  it("'Ver producción' opens production for that branch and product", async () => {
    const openProduction = vi.fn()
    await applyServerEvent(new QueryClient(), "batch-ready", payload(), { ...NAMES, openProduction })

    const options = vi.mocked(toast).mock.calls.at(-1)![1] as { action: { label: string; onClick: () => void } }
    expect(options.action.label).toBe("Ver producción")
    options.action.onClick()
    expect(openProduction).toHaveBeenCalledWith({ branchId: "branch-1", productId: "product-1" })
  })

  it("still invalidates queries even with a malformed payload (invalidation isn't gated on the notice)", async () => {
    const queryClient = new QueryClient()
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries")

    await expect(applyServerEvent(queryClient, "batch-ready", "not-json")).resolves.toBeUndefined()
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["kpi"] })
    expect(toast).not.toHaveBeenCalled()
  })

  it("sync-available invalidates the tenant profile query", () => {
    const queryClient = new QueryClient()
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries")

    applyServerEvent(queryClient, "sync-available", "{}")

    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ["tenant"] })
  })

  it("heartbeat and unknown event types are no-ops", () => {
    const queryClient = new QueryClient()
    const invalidateSpy = vi.spyOn(queryClient, "invalidateQueries")

    applyServerEvent(queryClient, "heartbeat", "")
    applyServerEvent(queryClient, "some-future-event", "{}")

    expect(invalidateSpy).not.toHaveBeenCalled()
  })
})

describe("openEventStream", () => {
  class FakeSource {
    static instances: FakeSource[] = []
    closed = false
    private listeners = new Map<string, Set<(event: MessageEvent<string>) => void>>()
    constructor(public url: string) {
      FakeSource.instances.push(this)
    }
    addEventListener(type: string, fn: (event: MessageEvent<string>) => void) {
      if (!this.listeners.has(type)) this.listeners.set(type, new Set())
      this.listeners.get(type)!.add(fn)
    }
    removeEventListener(type: string, fn: (event: MessageEvent<string>) => void) {
      this.listeners.get(type)?.delete(fn)
    }
    close() {
      this.closed = true
    }
    emit(type: string, data = "") {
      this.listeners.get(type)?.forEach((fn) => fn({ data } as MessageEvent<string>))
    }
  }

  let token: string
  const refresh = vi.fn<() => Promise<"refreshed" | "rejected" | "unreachable">>()
  const onReconnect = vi.fn()
  const onMessage = vi.fn()
  const onStatus = vi.fn()

  /** An unsigned JWT whose `exp` is [secondsFromNow] away -- only the payload is ever read. */
  function jwtExpiringIn(secondsFromNow: number) {
    const exp = Math.floor(Date.now() / 1000) + secondsFromNow
    const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_")
    return `${b64({ alg: "none" })}.${b64({ exp })}.sig`
  }

  function open() {
    return openEventStream({
      url: (t) => `https://api/events/sse?access_token=${t}`,
      getToken: () => token,
      refresh,
      needsRefresh: (t) => accessTokenNeedsRefresh(t),
      create: (url) => new FakeSource(url),
      listeners: { "batch-ready": onMessage },
      onReconnect,
      onStatus,
    })
  }

  beforeEach(() => {
    vi.useFakeTimers()
    FakeSource.instances = []
    token = "old"
    refresh.mockReset()
    refresh.mockImplementation(async () => {
      token = "fresh"
      return "refreshed"
    })
    onReconnect.mockReset()
    onMessage.mockReset()
    onStatus.mockReset()
  })
  afterEach(() => vi.useRealTimers())

  it("opens one stream with the current token and forwards its events", () => {
    open()
    expect(FakeSource.instances.map((s) => s.url)).toEqual(["https://api/events/sse?access_token=old"])
    FakeSource.instances[0].emit("batch-ready", "x")
    expect(onMessage).toHaveBeenCalledTimes(1)
  })

  it("after an error, reopens with a freshly refreshed token", async () => {
    open()
    FakeSource.instances[0].emit("error")
    await vi.advanceTimersByTimeAsync(1_000)
    expect(FakeSource.instances[0].closed).toBe(true)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(FakeSource.instances[1].url).toBe("https://api/events/sse?access_token=fresh")
  })

  it("a dropped stream with a fresh token reconnects with that token, without refreshing", async () => {
    token = jwtExpiringIn(10 * 60)
    open()
    FakeSource.instances[0].emit("error")
    await vi.advanceTimersByTimeAsync(1_000)
    expect(refresh).not.toHaveBeenCalled()
    expect(FakeSource.instances).toHaveLength(2)
    expect(FakeSource.instances[1].url).toBe(FakeSource.instances[0].url)
  })

  it("a token within 60 s of expiry is refreshed before reconnecting", async () => {
    token = jwtExpiringIn(59)
    open()
    FakeSource.instances[0].emit("error")
    await vi.advanceTimersByTimeAsync(1_000)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(FakeSource.instances[1].url).toBe("https://api/events/sse?access_token=fresh")
  })

  it("an expired token is refreshed before reconnecting", async () => {
    token = jwtExpiringIn(-5)
    open()
    FakeSource.instances[0].emit("error")
    await vi.advanceTimersByTimeAsync(1_000)
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it("refetches data once the new stream is open, not before", async () => {
    open()
    FakeSource.instances[0].emit("error")
    await vi.advanceTimersByTimeAsync(1_000)
    expect(onReconnect).not.toHaveBeenCalled()
    FakeSource.instances[1].emit("open")
    expect(onReconnect).toHaveBeenCalledTimes(1)
  })

  it("does not call onReconnect for the first open", () => {
    open()
    FakeSource.instances[0].emit("open")
    expect(onReconnect).not.toHaveBeenCalled()
  })

  it("stops for good when the session was rejected", async () => {
    refresh.mockResolvedValue("rejected")
    open()
    FakeSource.instances[0].emit("error")
    await vi.advanceTimersByTimeAsync(120_000)
    expect(FakeSource.instances).toHaveLength(1)
  })

  it("keeps retrying with growing, capped delays while the server is unreachable", async () => {
    refresh.mockResolvedValue("unreachable")
    open()
    FakeSource.instances[0].emit("error")
    await vi.advanceTimersByTimeAsync(1_000)
    expect(refresh).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1_999)
    expect(refresh).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(refresh).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(10 * 60_000)
    // 1+2+4+8+16 s ramp, then one attempt per 30 s: ~24 in ten minutes, not ~600 (a 1 s spin)
    expect(refresh.mock.calls.length).toBeGreaterThan(15)
    expect(refresh.mock.calls.length).toBeLessThan(30)
  })

  it("the returned close stops reconnecting and closes the stream", async () => {
    const close = open()
    FakeSource.instances[0].emit("error")
    close()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(refresh).not.toHaveBeenCalled()
    expect(FakeSource.instances).toHaveLength(1)
    expect(FakeSource.instances[0].closed).toBe(true)
  })

  it("resets the backoff after a successful open", async () => {
    refresh.mockResolvedValueOnce("unreachable")
    open()
    FakeSource.instances[0].emit("error")
    await vi.advanceTimersByTimeAsync(1_000) // refresh 1: unreachable, next in 2 s
    await vi.advanceTimersByTimeAsync(2_000) // refresh 2: refreshed -> stream 2
    FakeSource.instances[1].emit("open")
    FakeSource.instances[1].emit("error")
    await vi.advanceTimersByTimeAsync(1_000)
    expect(FakeSource.instances).toHaveLength(3)
  })

  describe("paused status (#140)", () => {
    it("pauses on a throttled or unreachable refresh and stays paused while retrying", async () => {
      refresh.mockResolvedValue("unreachable")
      open()
      FakeSource.instances[0].emit("error")
      await vi.advanceTimersByTimeAsync(1_000)
      expect(onStatus).toHaveBeenLastCalledWith("paused")
      await vi.advanceTimersByTimeAsync(60_000)
      expect(onStatus).not.toHaveBeenCalledWith("live")
    })

    it("pauses when a reconnect with a still-fresh token fails again", async () => {
      token = jwtExpiringIn(600)
      open()
      FakeSource.instances[0].emit("error")
      expect(onStatus).not.toHaveBeenCalledWith("paused")
      await vi.advanceTimersByTimeAsync(1_000)
      FakeSource.instances[1].emit("error")
      expect(onStatus).toHaveBeenLastCalledWith("paused")
    })

    it("clears on the next successful open, which refetches once", async () => {
      refresh.mockResolvedValueOnce("unreachable")
      open()
      FakeSource.instances[0].emit("error")
      await vi.advanceTimersByTimeAsync(1_000)
      await vi.advanceTimersByTimeAsync(2_000)
      FakeSource.instances[1].emit("open")
      expect(onStatus).toHaveBeenLastCalledWith("live")
      expect(onReconnect).toHaveBeenCalledTimes(1)
    })

    it("a rejected refresh never shows paused (the session ends instead)", async () => {
      refresh.mockResolvedValue("rejected")
      open()
      FakeSource.instances[0].emit("error")
      await vi.advanceTimersByTimeAsync(120_000)
      expect(onStatus).not.toHaveBeenCalledWith("paused")
    })
  })
})
