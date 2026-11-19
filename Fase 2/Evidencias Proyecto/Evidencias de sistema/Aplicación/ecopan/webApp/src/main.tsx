import "@/lib/zodConfig" // first: it must run before any module builds a schema
import { StrictMode, useEffect } from "react"
import { createRoot } from "react-dom/client"
import { QueryClient, QueryClientProvider, useQueryClient } from "@tanstack/react-query"
import { RouterProvider } from "@tanstack/react-router"
// One geometric sans carries the whole interface -- display through label -- which is what the
// Ecopan design system specifies. It replaces the Archivo/Fraunces
// pair the dashboard used to ship: that pairing needed a rule keeping figures off the serif, and
// a single family with one rhythm makes the rule unnecessary. Mono stays, for runs of characters
// that must align as data -- timestamps, ids, key/value rows.
import "@fontsource-variable/outfit"
import "@fontsource/ibm-plex-mono/400.css"
import "@fontsource/ibm-plex-mono/700.css"
import "./index.css"
import { router } from "./router"
import { AuthProvider, useAuth } from "@/features/auth/AuthContext"
import { handleSessionExpired } from "@/lib/sessionExpiry"
import { setAuthFailureHandler, setModuleDisabledHandler } from "@/api/http"

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
    },
  },
})

/** Wires `http.ts`'s hard-logout callback to this render tree's `queryClient` + `router` (kept
 * out of `http.ts` itself so that module has no dependency on the routing layer -- plan 07 §2).
 * Also the one place that supplies the router's `auth` context, per the documented
 * "create router with placeholder context, inject via <RouterProvider context>" pattern. */
function InnerApp() {
  const auth = useAuth()
  const queryClient = useQueryClient()

  useEffect(() => {
    setAuthFailureHandler(() => {
      handleSessionExpired({ clearSession: auth.clearSession, queryClient, router })
    })
    // Generic across module names (plan 07 §11 item 7) -- any 403 that names a disabled module
    // re-fetches the tenant profile, which `AppShell`'s `useTenantProfileSync` pushes into
    // `Session` so nav/guards see the change without a full reload.
    setModuleDisabledHandler(() => {
      queryClient.invalidateQueries({ queryKey: ["tenant"] })
    })
    return () => {
      setAuthFailureHandler(null)
      setModuleDisabledHandler(null)
    }
  }, [auth, queryClient])

  return <RouterProvider router={router} context={{ auth, queryClient }} />
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <InnerApp />
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>
)
