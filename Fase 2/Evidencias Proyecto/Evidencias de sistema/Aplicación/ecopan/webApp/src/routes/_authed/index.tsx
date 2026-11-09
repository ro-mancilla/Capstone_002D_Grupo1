import { createFileRoute, redirect } from "@tanstack/react-router"
import { resolveLanding } from "@/lib/destinations"

/**
 * The manager app has no page of its own at the root, so it forwards to the first destination
 * this session is entitled to. Which one that is depends on the tenant's enabled modules, so it
 * cannot be hard-coded: with `ANALYTICS_DASHBOARD` off, `/analytics` is precisely the route the
 * guards refuse, and forwarding there sent the manager into a redirect that repeated.
 */
export const Route = createFileRoute("/_authed/")({
  beforeLoad: ({ context }) => {
    const landing = resolveLanding(context.auth.session)
    // Entitled to nothing: `LoginPage` owns the friendly rejection UI, same as `_authed` does
    // for a role that cannot use the manager app at all.
    throw redirect({ to: landing ?? "/login" })
  },
})
