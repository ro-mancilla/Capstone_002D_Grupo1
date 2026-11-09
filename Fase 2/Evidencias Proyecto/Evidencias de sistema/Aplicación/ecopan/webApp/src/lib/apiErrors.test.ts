import { describe, expect, it } from "vitest"
import { ApiError } from "@/api/http"
import { classify, errorMessage, isModuleDisabledError } from "./apiErrors"
import { t } from "./i18n"

/**
 * Harvested from the backend, not from the map under test -- listing `t.errors.codes`' own keys
 * back at it would pass while a real code went untranslated, which is exactly what happened on
 * the first cut of this module (every `ServiceOutcome.Invalid` code was missing).
 *
 * Sourced by grepping `backend/app/src/main/kotlin` for `ErrorBody(code = "...")` literals and
 * `ServiceOutcome.Invalid("...")` codes, which routers forward verbatim.
 */
const BACKEND_CODES = [
  "ALMACEN_REQUIRES_UNIT", "UNKNOWN_PRODUCT_TYPE", "ORIGIN_IMMUTABLE",
  "BRANCH_FORBIDDEN", "BRANCH_ID_REQUIRED", "BRANCH_NOT_FOUND", "FORBIDDEN", "IMAGE_NOT_UPLOADED",
  "IMAGE_TOO_LARGE", "IMAGE_NOT_AN_IMAGE", "SYNC_UNAVAILABLE",
  "INGREDIENT_NOT_FOUND", "INVALID_BASIS_POINTS", "INVALID_BRANCH", "INVALID_CREDENTIALS",
  "INVALID_DELTA", "INVALID_ID", "INVALID_IMAGE_KEY", "INVALID_INGREDIENT", "INVALID_KIND",
  "INVALID_MODULE", "INVALID_PRODUCT", "INVALID_ROLE", "INVALID_SOLD_BY", "INVALID_SOURCE_BATCH", "INVALID_THRESHOLD",
  "MISSING_PARAMS", "MODULE_DISABLED", "NOT_FOUND", "PASSWORD_REQUIRED", "PRICE_PER_KG_REQUIRED",
  "PRICE_REQUIRED", "PRODUCT_NOT_FOUND", "PRODUCT_NOT_ALMACEN", "RANGE_REQUIRED",
  "OPENING_STOCK_REQUIRED", "INVALID_OPENING_STOCK", "OPENING_BRANCH_REQUIRED",
  "STORAGE_UNAVAILABLE", "TENANT_NOT_FOUND", "TOKEN_INVALID", "TOO_MANY_MUTATIONS",
  "UNKNOWN_ENTITY_TYPE", "UNSUPPORTED_IMAGE_TYPE", "USER_NOT_FOUND", "VALIDATION_ERROR",
]

/** Minted by `api/http.ts` rather than the backend, but they reach the same screens. */
const CLIENT_CODES = ["SESSION_EXPIRED", "NETWORK_ERROR", "UNKNOWN_ERROR"]

const LOOKS_LIKE_A_WIRE_CODE = /^[A-Z][A-Z0-9_]*$/

describe("errorMessage", () => {
  it("never renders a wire code, for any code the backend emits today", () => {
    for (const code of [...BACKEND_CODES, ...CLIENT_CODES]) {
      const message = errorMessage(new ApiError(400, { code }))

      expect(message, code).not.toMatch(LOOKS_LIKE_A_WIRE_CODE)
      expect(message, code).not.toContain(code)
    }
  })

  it("explains a rejected photo upload (422) in Spanish, naming the limit or the formats", () => {
    expect(errorMessage(new ApiError(422, { code: "IMAGE_TOO_LARGE" }))).toBe(t.errors.codes.IMAGE_TOO_LARGE)
    expect(t.errors.codes.IMAGE_TOO_LARGE).toContain("5 MB")
    expect(errorMessage(new ApiError(422, { code: "IMAGE_NOT_AN_IMAGE" }))).toBe(t.errors.codes.IMAGE_NOT_AN_IMAGE)
    expect(t.errors.codes.IMAGE_NOT_AN_IMAGE).toMatch(/JPEG.*PNG.*WebP/)
  })

  it("never renders a wire code for a code that does not exist yet", () => {
    const message = errorMessage(new ApiError(500, { code: "SOME_FUTURE_CODE" }))

    expect(message).toBe(t.errors.generic)
    expect(message).not.toContain("SOME_FUTURE_CODE")
  })

  it("does not pass through a backend-authored message, which is not in Spanish", () => {
    const message = errorMessage(new ApiError(404, { code: "PRODUCT_NOT_FOUND", message: "Product not found" }))

    expect(message).not.toContain("Product not found")
    expect(message).toBe(t.errors.codes.PRODUCT_NOT_FOUND)
  })

  it("tells a throttled request apart from a wrong password and from being offline", () => {
    const throttled = errorMessage(new ApiError(429, { code: "UNKNOWN_ERROR" }))

    expect(throttled).toBe(t.errors.tooManyRequests)
    expect(throttled).not.toBe(t.errors.network)
    expect(throttled).not.toBe(t.errors.generic)
  })

  it("distinguishes permission, not-found and module-disabled from one another", () => {
    const forbidden = errorMessage(new ApiError(403, { code: "FORBIDDEN" }))
    const notFound = errorMessage(new ApiError(404, { code: "PRODUCT_NOT_FOUND" }))
    const disabled = errorMessage(new ApiError(403, { code: "MODULE_DISABLED", module: "INVENTORY_FORMULAS" }))

    expect(new Set([forbidden, notFound, disabled]).size).toBe(3)
  })

  it("gives every code the backend emits a sentence of its own, not the generic one", () => {
    const untranslated = BACKEND_CODES.filter(
      (code) => errorMessage(new ApiError(400, { code })) === t.errors.generic
    )

    // Fails loudly naming the codes, so adding a backend code without Spanish copy is caught
    // here rather than shipping as a vague "Ocurrió un error".
    expect(untranslated).toEqual([])
  })

  it("names an unreachable server rather than falling back to the form's message", () => {
    // `http.ts` mints this when `fetch` itself rejects -- it is an ApiError, not a TypeError,
    // which is why the kind must be reachable through the ApiError path.
    const offline = new ApiError(0, { code: "NETWORK_ERROR" })

    expect(classify(offline)).toBe("network")
    expect(errorMessage(offline, "No se pudo guardar el producto.")).toBe(t.errors.network)
  })

  it("falls back only when nothing more specific is known", () => {
    expect(errorMessage(new ApiError(500, { code: "SOME_FUTURE_CODE" }), "Algo propio")).toBe("Algo propio")
    expect(errorMessage("not even an error")).toBe(t.errors.generic)
    expect(errorMessage(new TypeError("Failed to fetch"))).toBe(t.errors.network)
  })
})

describe("classify", () => {
  it("names the kind behind each failure", () => {
    expect(classify(new ApiError(403, { code: "MODULE_DISABLED", module: "X" }))).toBe("moduleDisabled")
    expect(classify(new ApiError(403, { code: "FORBIDDEN" }))).toBe("forbidden")
    expect(classify(new ApiError(401, { code: "SESSION_EXPIRED" }))).toBe("unauthorized")
    expect(classify(new ApiError(404, { code: "PRODUCT_NOT_FOUND" }))).toBe("notFound")
    expect(classify(new TypeError("Failed to fetch"))).toBe("network")
    expect(classify("nope")).toBe("unknown")
  })

  it("treats a 403 carrying a module as disabled, not merely forbidden", () => {
    // `isModuleDisabledError` already drew this line for the read side; `classify` must agree
    // with it, or the same 403 would be described two different ways.
    const disabled = new ApiError(403, { code: "MODULE_DISABLED", module: "DEMAND_FORECASTING" })

    expect(isModuleDisabledError(disabled)).toBe(true)
    expect(classify(disabled)).toBe("moduleDisabled")
  })
})
