import { createFileRoute } from "@tanstack/react-router"
import { ModulesPage } from "@/features/admin-modules/ModulesPage"
import { requireEntitlements } from "@/lib/guards"

export const Route = createFileRoute("/_authed/organization/modules")({
  beforeLoad: requireEntitlements("/organization/modules"),
  component: ModulesPage,
})
