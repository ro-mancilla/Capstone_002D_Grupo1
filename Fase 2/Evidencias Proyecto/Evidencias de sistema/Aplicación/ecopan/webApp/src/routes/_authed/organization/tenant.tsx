import { createFileRoute } from "@tanstack/react-router"
import { TenantProfilePage } from "@/features/admin-org/TenantProfilePage"
import { requireEntitlements } from "@/lib/guards"

export const Route = createFileRoute("/_authed/organization/tenant")({
  beforeLoad: requireEntitlements("/organization/tenant"),
  component: TenantProfilePage,
})
