import { createFileRoute } from "@tanstack/react-router"
import { UsersPage } from "@/features/admin-org/UsersPage"
import { requireEntitlements } from "@/lib/guards"

/** Always-on, never module-gated (01 §4 / 02 T3-T4: `USERS_MANAGE` permission only). */
export const Route = createFileRoute("/_authed/organization/users")({
  beforeLoad: requireEntitlements("/organization/users"),
  component: UsersPage,
})
