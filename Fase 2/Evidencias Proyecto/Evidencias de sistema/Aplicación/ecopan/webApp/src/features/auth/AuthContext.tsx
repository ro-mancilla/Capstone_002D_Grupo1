import { createContext, use, useCallback, useState, type ReactNode } from "react"
import {
  clearStoredSession,
  getStoredSession,
  login as loginRequest,
  logout as logoutRequest,
  sessionFromLoginResponse,
  storeSession,
} from "@/api/auth"
import { clearSessionTokens } from "@/api/http"
import type { LoginRequest, Session, TenantProfileResponse } from "@/api/types"

export interface AuthState {
  /** `undefined` while the very first render hasn't checked localStorage yet; `null` once
   * checked and there is no session. Route guards only need to distinguish "no session" from
   * "has a session", so both render the same to consumers, but keeping the initial state a
   * plain read is enough here -- there is no network round-trip to await (07 §2: the access
   * token, not the session snapshot, is what actually gets re-validated, lazily, by the first
   * authenticated request's 401-refresh cycle). */
  session: Session | null
  login: (request: LoginRequest & { tenantSlug: string }) => Promise<Session>
  logout: () => Promise<void>
  /** Hard-logout path invoked by `http.ts` on refresh failure -- synchronous, local-only,
   * no network call (the refresh token is already known to be dead). */
  clearSession: () => void
  /**
   * Re-syncs the live session's `enabledModules`/`branches`/`tenantName`/`currencyCode` from a
   * freshly-fetched `GET /tenant` (T1) response, without touching the identity fields
   * (`userId`/`role`/`branchId`) that only login can change. This is what makes "toggling a
   * module refetches the tenant profile, nav re-filters live" (plan 07 §5/§11 item 7) actually
   * update the UI -- `hooks/useTenantProfileSync.ts` calls this on every successful
   * `tenantProfileQuery()` fetch, mounted once from `AppShell` so it applies app-wide.
   */
  syncFromTenantProfile: (profile: TenantProfileResponse) => void
}

const AuthContext = createContext<AuthState | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => getStoredSession())

  const login = useCallback(async (request: LoginRequest & { tenantSlug: string }) => {
    const response = await loginRequest(request)
    const nextSession = sessionFromLoginResponse(response, request.tenantSlug)
    storeSession(nextSession)
    setSession(nextSession)
    return nextSession
  }, [])

  const clearSession = useCallback(() => {
    clearSessionTokens()
    clearStoredSession()
    setSession(null)
  }, [])

  const logout = useCallback(async () => {
    await logoutRequest()
    clearStoredSession()
    setSession(null)
  }, [])

  const syncFromTenantProfile = useCallback((profile: TenantProfileResponse) => {
    setSession((current) => {
      if (!current) return current
      const next: Session = {
        ...current,
        tenantName: profile.name,
        currencyCode: profile.currencyCode,
        enabledModules: profile.enabledModules,
        branches: profile.branches,
      }
      storeSession(next)
      return next
    })
  }, [])

  return (
    <AuthContext value={{ session, login, logout, clearSession, syncFromTenantProfile }}>
      {children}
    </AuthContext>
  )
}

export function useAuth(): AuthState {
  const context = use(AuthContext)
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
