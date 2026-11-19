import { createFileRoute, redirect } from "@tanstack/react-router"
import { AppShell } from "@/components/layout/AppShell"
import { MANAGER_APP_ROLES } from "@/api/types"

/**
 * `_authed` layout (plan 07 §3): every route nested under it inherits this guard. No session ->
 * redirect to `/login?redirect=<here>`; session present but wrong role (CASHIER/BAKER slipping
 * in via a stale/shared session) -> redirect to login too, since `LoginPage` owns the
 * friendly rejection UI and re-checking the role there keeps that logic in one place.
 */
export const Route = createFileRoute("/_authed")({
  beforeLoad: ({ context, location }) => {
    const { session } = context.auth
    if (!session || !MANAGER_APP_ROLES.includes(session.role)) {
      throw redirect({ to: "/login", search: { redirect: location.href } })
    }
  },
  component: AppShell,
})
