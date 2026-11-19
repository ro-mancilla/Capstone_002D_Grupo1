import { ApiError } from "@/api/http"
import { t } from "./i18n"

/**
 * True for exactly the 403 shape `Route.requireModule` produces (`ErrorBody{ code, module }`,
 * 01 §7.2) -- pages with a module-gated query check this to render `ModuleDisabledState` instead
 * of an empty/broken list when a stale tab's module gets toggled off elsewhere (plan 07 §11
 * item 7). Not tied to any specific module name.
 */
export function isModuleDisabledError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 403 && Boolean(error.module)
}

/** What went wrong, coarsely enough for a screen to decide how to react. */
export type ErrorKind =
  | "moduleDisabled"
  | "forbidden"
  | "unauthorized"
  | "notFound"
  | "network"
  | "rateLimited"
  | "unknown"

export function classify(error: unknown): ErrorKind {
  if (isModuleDisabledError(error)) return "moduleDisabled"

  if (error instanceof ApiError) {
    // `http.ts` mints `ApiError(0, NETWORK_ERROR)` when `fetch` itself rejects, so an
    // unreachable server arrives here as an ApiError rather than as a TypeError.
    if (error.status === 0) return "network"
    if (error.status === 401) return "unauthorized"
    if (error.status === 403) return "forbidden"
    if (error.status === 404) return "notFound"
    if (error.status === 429) return "rateLimited"
    return "unknown"
  }

  // Only reached by a caller outside `http.ts`, which rejects with a TypeError of its own.
  if (error instanceof TypeError) return "network"

  return "unknown"
}

/** One sentence per kind, for a failure whose code carries no sentence of its own. */
const KIND_MESSAGE: Record<Exclude<ErrorKind, "unknown">, string> = {
  moduleDisabled: t.errors.moduleDisabled,
  forbidden: t.errors.codes.FORBIDDEN,
  unauthorized: t.errors.unauthorized,
  notFound: t.errors.notFound,
  network: t.errors.network,
  rateLimited: t.errors.tooManyRequests,
}

/**
 * The one place a failure becomes a sentence a manager can read (plan 07 §8: this dashboard is
 * es-CL only, and CLAUDE.md's rule is that no wire vocabulary reaches a screen).
 *
 * `ApiError.message` is deliberately *not* used: it is `body.message ?? body.code`, and the
 * backend usually sends only a code, so passing it through rendered literal `FORBIDDEN` and
 * `PRODUCT_NOT_FOUND` into the UI. When a message is present it is backend-authored and not in
 * Spanish, so it is no better. The code is translated here or not shown at all.
 *
 * Resolution order: the code's own sentence, then its kind's, then the caller's `fallback`, then
 * the generic one. The code wins over `fallback` on purpose -- "Debes ingresar el precio por
 * kilo." is worth more to a baker than a form's "No se pudo guardar el producto."
 *
 * The lookup is cast at the point of use, as `labels.ts` does, so the map keeps its literal keys
 * (a typo in `t.errors.codes.FORBIDEN` stays a type error) while an unknown code is honestly
 * `undefined` rather than `string`.
 */
export function errorMessage(error: unknown, fallback?: string): string {
  const kind = classify(error)

  // A throttled response has no body of ours, so its code is the catch-all UNKNOWN_ERROR, which
  // has a sentence of its own that would win below. The status is the signal here.
  if (kind === "rateLimited") return KIND_MESSAGE.rateLimited

  if (error instanceof ApiError) {
    const codes = t.errors.codes as Record<string, string | undefined>
    const translated = codes[error.code]
    if (translated) return translated
  }

  if (kind !== "unknown") return KIND_MESSAGE[kind]

  return fallback ?? t.errors.generic
}
