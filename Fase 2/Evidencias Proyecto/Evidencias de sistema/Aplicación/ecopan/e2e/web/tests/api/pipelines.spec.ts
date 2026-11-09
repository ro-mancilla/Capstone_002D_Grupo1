import { randomUUID } from "node:crypto"
import { expect, test } from "@playwright/test"
import { API, login, productNamed, pushBatch } from "../../../shared/api"
import { restartServices, sql, startService, stopService, waitForApi } from "../../../shared/stack"

// Faults and timing in the production-batch pipeline, on the real stack (#135, #137, restarts).
const DAY_MS = 24 * 60 * 60 * 1000

async function until<T>(what: string, read: () => T | Promise<T>, done: (v: T) => boolean, timeoutMs = 120_000): Promise<T> {
  const deadline = Date.now() + timeoutMs
  for (;;) {
    const v = await read()
    if (done(v)) return v
    if (Date.now() > deadline) throw new Error(`timed out waiting for ${what} (last: ${JSON.stringify(v)})`)
    await new Promise((r) => setTimeout(r, 1_000))
  }
}

const decrementStatus = (batchId: string) => sql(`select decrement_status from production_batches where id = '${batchId}'`)

test.describe("production batches through the real pipeline", () => {
  test("a batch that syncs days late books its ingredient movements on the day it was mixed (#137)", async () => {
    const baker = await login("baker")
    const marraqueta = await productNamed((await login("manager")).accessToken, "Marraqueta")
    const batchAt = Date.now() - 3 * DAY_MS
    const batchId = randomUUID()
    const res = await fetch(`${API}/sync/push`, {
      method: "POST",
      headers: { Authorization: `Bearer ${baker.accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({ deviceId: `e2e-late-${randomUUID()}`, mutations: [{
        mutationId: randomUUID(), entityType: "PRODUCTION_BATCH", opType: "CREATE", clientTimestamp: batchAt,
        payload: { id: batchId, branchId: baker.user.branchId ?? baker.branches[0].id, bakerId: baker.user.id, productId: marraqueta.id, mixWeightGrams: 10_000, batchAt, movementsMinted: false },
      }] }),
    })
    expect((await res.json()).results[0].status).toBe("APPLIED")

    await until("the decrement", () => decrementStatus(batchId), (s) => s === "APPLIED")
    const movements = sql(`select count(*), min(extract(epoch from occurred_at) * 1000)::bigint, max(extract(epoch from occurred_at) * 1000)::bigint from stock_movements where source_batch_id = '${batchId}'`).split("|")
    expect(Number(movements[0])).toBeGreaterThan(0)
    // Booked at the mix time (to the second), not when the worker got to it.
    expect(Math.abs(Number(movements[1]) - batchAt)).toBeLessThan(2_000)
    expect(Math.abs(Number(movements[2]) - batchAt)).toBeLessThan(2_000)
  })

  test("a database failure during push is a 503, not a rejected batch, and the same push applies once the database is back (#135)", async () => {
    const baker = await login("baker")
    const marraqueta = await productNamed((await login("manager")).accessToken, "Marraqueta")
    const batchId = randomUUID()
    const mutationId = randomUUID()
    const body = JSON.stringify({ deviceId: `e2e-503-${randomUUID()}`, mutations: [{
      mutationId, entityType: "PRODUCTION_BATCH", opType: "CREATE", clientTimestamp: Date.now(),
      payload: { id: batchId, branchId: baker.user.branchId ?? baker.branches[0].id, bakerId: baker.user.id, productId: marraqueta.id, mixWeightGrams: 10_000, batchAt: Date.now(), movementsMinted: false },
    }] })
    const push = () => fetch(`${API}/sync/push`, { method: "POST", headers: { Authorization: `Bearer ${baker.accessToken}`, "Content-Type": "application/json" }, body })

    stopService("db")
    try {
      const down = await push()
      expect(down.status).toBe(503)
    } finally {
      await startService("db")
    }
    // The API's pool reconnects on its own; until it does the answer is still a 503, never a REJECTED item.
    const back = await until("the push to be accepted", async () => { const r = await push(); return { status: r.status, json: r.status === 200 ? await r.json() : null } }, (r) => r.status === 200)
    expect(back.json.results[0].status).toBe("APPLIED")
    expect(sql(`select count(*) from production_batches where id = '${batchId}'`)).toBe("1")
  })

  test("after the API and the worker restart together, a batch is still decremented and announced (bootstrap race, reconnects)", async () => {
    restartServices("web", "worker")
    await waitForApi()
    const marraqueta = await productNamed((await login("manager")).accessToken, "Marraqueta")
    const { batchId } = await pushBatch({ productId: marraqueta.id })
    await until("the decrement after the restart", () => decrementStatus(batchId), (s) => s === "APPLIED", 180_000)
    expect(Number(sql(`select count(*) from stock_movements where source_batch_id = '${batchId}'`))).toBeGreaterThan(0)
  })

  test("a broker restart does not strand batches: consumers reconnect (#144 fix)", async () => {
    const marraqueta = await productNamed((await login("manager")).accessToken, "Marraqueta")
    stopService("rabbitmq")
    let batchId: string
    try {
      batchId = (await pushBatch({ productId: marraqueta.id })).batchId
    } finally {
      await startService("rabbitmq")
    }
    await until("the decrement after the broker is back", () => decrementStatus(batchId), (s) => s === "APPLIED", 180_000)
    // And a batch pushed after the reconnect flows too, without anything being restarted.
    const later = (await pushBatch({ productId: marraqueta.id })).batchId
    await until("the next batch", () => decrementStatus(later), (s) => s === "APPLIED", 120_000)
  })
})
