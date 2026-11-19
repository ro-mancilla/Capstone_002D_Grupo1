import { useEffect } from "react"
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router"
import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query"
import { render } from "@testing-library/react"
import { routeTree } from "@/routeTree.gen"
import { AuthProvider, useAuth, type AuthState } from "@/features/auth/AuthContext"
import { setAuthFailureHandler, setModuleDisabledHandler } from "@/api/http"
import { handleSessionExpired } from "@/lib/sessionExpiry"
import type { Session } from "@/api/types"

/** Per the TanStack Router testing docs' recipe: a fresh router (real, generated route tree;
 * memory history so tests don't touch `window.location`) per test, with an injectable `auth`
 * context so route guards can be exercised without a real login flow. */
export function buildTestAuth(session: Session | null): AuthState {
  return {
    session,
    login: vi.fn(),
    logout: vi.fn(),
    clearSession: vi.fn(),
    syncFromTenantProfile: vi.fn(),
  }
}

export function renderRouterAt(initialLocation: string, session: Session | null) {
  const queryClient = new QueryClient()
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [initialLocation] }),
    context: {
      auth: buildTestAuth(session),
      queryClient,
    },
  })

  const utils = render(<RouterProvider router={router} />, {
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  })

  return { router, ...utils }
}

/**
 * Full-integration harness (real `AuthProvider`, real `api/auth.ts`/`http.ts` -- MSW intercepts
 * the network calls): mirrors `main.tsx`'s `InnerApp` so the router's `auth` context prop
 * actually updates on login/logout, letting `_authed`'s `beforeLoad` re-run for real via
 * `router.invalidate()`. Use this for the redirect-to-login-and-back and hard-logout flows;
 * use `renderRouterAt` above for anything that only needs a fixed session snapshot.
 */
export function renderApp(initialLocation: string) {
  const queryClient = new QueryClient()
  const router = createRouter({
    routeTree,
    history: createMemoryHistory({ initialEntries: [initialLocation] }),
    context: { auth: undefined!, queryClient },
  })

  function InnerApp() {
    const auth = useAuth()
    const qc = useQueryClient()
    useEffect(() => {
      setAuthFailureHandler(() => {
        handleSessionExpired({ clearSession: auth.clearSession, queryClient: qc, router })
      })
      setModuleDisabledHandler(() => {
        qc.invalidateQueries({ queryKey: ["tenant"] })
      })
      return () => {
        setAuthFailureHandler(null)
        setModuleDisabledHandler(null)
      }
    }, [auth, qc])
    return <RouterProvider router={router} context={{ auth, queryClient: qc }} />
  }

  const utils = render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <InnerApp />
      </AuthProvider>
    </QueryClientProvider>
  )

  return { router, ...utils }
}
