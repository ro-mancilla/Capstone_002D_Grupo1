import { redirect } from "@tanstack/react-router"
import { toast } from "sonner"
import type { RouterContext } from "@/routes/__root"
import { isEntitled, resolveLanding, type DestinationPath } from "./destinations"
import { t } from "./i18n"

/**
 * Segment-layout guard (plan 07 §3): belt-and-suspenders over the backend's own
 * `requirePermission`/`requireModule` 403s (01 §7.2) -- keeps the UI from ever offering a route
 * the API would reject.
 *
 * A route names its own path and nothing else; what that path requires lives in the destination
 * table, which the sidebar reads too, so the two cannot drift apart. On denial the redirect
 * resolves to a destination this session can actually open -- never a hard-coded one, which is
 * how a disabled `ANALYTICS_DASHBOARD` used to bounce `/analytics` back into itself.
 */
export function requireEntitlements(to: DestinationPath) {
  return ({ context }: { context: RouterContext }) => {
    const { session } = context.auth
    if (isEntitled(session, to)) return

    toast.error(t.errors.moduleDisabled)

    // `resolveLanding` only ever returns a destination that passes, and `to` has just failed,
    // so this cannot resolve back to where we were refused.
    const landing = resolveLanding(session)
    if (!landing) {
      // Entitled to nothing at all. `LoginPage` owns the friendly rejection UI (see `_authed`),
      // so send them there rather than leaving them on an empty shell.
      throw redirect({ to: "/login" })
    }
    throw redirect({ to: landing })
  }
}
