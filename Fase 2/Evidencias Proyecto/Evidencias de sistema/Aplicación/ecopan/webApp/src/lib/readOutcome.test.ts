import { describe, expect, it, vi } from "vitest"
import { ApiError } from "@/api/http"
import { readOutcome } from "./readOutcome"
import { t } from "./i18n"

const ready = { isLoading: false, error: null }
const loading = { isLoading: true, error: null }

describe("readOutcome", () => {
  it("is ready when nothing is loading and nothing failed", () => {
    expect(readOutcome(ready, ready)).toEqual({ status: "ready" })
  })

  it("distinguishes loading from empty", () => {
    // The bug this closes: a page with no rows yet and a page whose read failed both rendered
    // the same "no hay datos" empty state.
    expect(readOutcome(loading).status).toBe("loading")
    expect(readOutcome(ready).status).toBe("ready")
  })

  it("reports a disabled module as its own outcome, ahead of any other failure", () => {
    const disabled = { isLoading: false, error: new ApiError(403, { code: "MODULE_DISABLED", module: "X" }) }
    const other = { isLoading: false, error: new ApiError(500, { code: "BOOM" }) }

    expect(readOutcome(other, disabled).status).toBe("moduleDisabled")
    expect(readOutcome(disabled, other).status).toBe("moduleDisabled")
  })

  it("explains other failures in Spanish, never as a code", () => {
    const outcome = readOutcome({ isLoading: false, error: new ApiError(404, { code: "PRODUCT_NOT_FOUND" }) })

    expect(outcome).toMatchObject({ status: "failed", message: t.errors.codes.PRODUCT_NOT_FOUND })
    expect(outcome.status === "failed" && outcome.message).not.toMatch(/^[A-Z_]+$/)
  })

  it("offers a retry for a failure retrying could fix, and not for one it cannot", () => {
    const refetch = vi.fn()

    const network = readOutcome({ isLoading: false, error: new ApiError(0, { code: "NETWORK_ERROR" }), refetch })
    expect(network.status === "failed" && network.retry).toBe(refetch)

    const forbidden = readOutcome({ isLoading: false, error: new ApiError(403, { code: "FORBIDDEN" }), refetch })
    expect(forbidden.status === "failed" && forbidden.retry).toBeUndefined()
  })

  it("keeps reporting the failure while a retry is in flight", () => {
    const outcome = readOutcome({ isLoading: true, error: new ApiError(500, { code: "BOOM" }) })

    expect(outcome.status).toBe("failed")
  })
})
