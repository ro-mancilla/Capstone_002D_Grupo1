import { classify, errorMessage, isModuleDisabledError } from "./apiErrors"

/** The minimum of a TanStack Query result this needs, so tests need not build a whole one. */
export interface ReadResult {
  isLoading: boolean
  error: unknown
  refetch?: () => unknown
}

export type ReadOutcome =
  | { status: "loading" }
  | { status: "moduleDisabled" }
  | { status: "failed"; message: string; retry: (() => void) | undefined }
  | { status: "ready" }

/**
 * What a page should show for one or more reads (plan 07 §3).
 *
 * Pages used to write `const rows = result.data ?? []` and fall through to their empty state, so
 * a 403 for a disabled module, a permission failure and a genuinely empty bakery all rendered as
 * "no hay datos". Of the dashboard's reads only a handful inspected `error` at all.
 *
 * Module-disabled outranks every other failure: it is the one a manager can act on, and
 * `api/http.ts` has already kicked off a tenant refetch by the time it renders.
 *
 * Loading is reported only while nothing has failed, so a slow retry after an error does not
 * flicker the page back to a spinner.
 */
export function readOutcome(...results: ReadResult[]): ReadOutcome {
  // Scanned across every read, not just the first that failed: a page combining three queries
  // can have an unrelated 500 arrive ahead of the disabled-module 403, and the manager needs to
  // be told the actionable one.
  if (results.some((result) => isModuleDisabledError(result.error))) return { status: "moduleDisabled" }

  const failed = results.find((result) => result.error != null)

  if (failed) {
    return {
      status: "failed",
      message: errorMessage(failed.error),
      // A network blip is worth retrying; a refused permission is not.
      retry: classify(failed.error) === "forbidden" ? undefined : failed.refetch,
    }
  }

  if (results.some((result) => result.isLoading)) return { status: "loading" }

  return { status: "ready" }
}
