import { createFileRoute } from "@tanstack/react-router"
import { SalesPage } from "@/features/dashboard/SalesPage"
import { requireEntitlements } from "@/lib/guards"

export const Route = createFileRoute("/_authed/analytics/sales")({
  beforeLoad: requireEntitlements("/analytics/sales"),
  component: SalesPage,
})
