import type { QueryClient } from "@tanstack/react-query"
import type { AnyRouter } from "@tanstack/react-router"

/** True for `/login` itself and anything under it (query and hash included). */
export function isLoginHref(href: string): boolean {
  return /^\/login(?:[/?#]|$)/.test(href)
}

/**
 * The hard-logout reaction, shared by `main.tsx` and the test harness so they cannot drift.
 *
 * `http.ts` can report a dead session more than once -- every request that was in flight when the
 * refresh failed, and any request issued afterwards, each ends in a logout. So this must be
 * idempotent: clearing state twice is harmless, but once the router is on `/login` it must not
 * navigate again -- the current location must never be captured as the `redirect` target, which is
 * what used to nest `/login?redirect=/login?redirect=...`. State is still cleared there: a session
 * can die in the moment after login succeeded and before the router left `/login`.
 */
export function handleSessionExpired(deps: {
  clearSession: () => void
  queryClient: QueryClient
  router: AnyRouter
}): void {
  const { clearSession, queryClient, router } = deps
  const here = router.state.location.href
  clearSession()
  queryClient.clear()
  if (isLoginHref(here)) return

  router.navigate({ to: "/login", search: { redirect: here } })
}
