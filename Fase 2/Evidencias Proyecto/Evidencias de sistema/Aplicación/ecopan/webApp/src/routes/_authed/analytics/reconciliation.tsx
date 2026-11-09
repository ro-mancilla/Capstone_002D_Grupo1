import { createFileRoute } from "@tanstack/react-router"
import { ReconciliationPage } from "@/features/dashboard/ReconciliationPage"
import { requireEntitlements } from "@/lib/guards"

export const Route = createFileRoute("/_authed/analytics/reconciliation")({
  beforeLoad: requireEntitlements("/analytics/reconciliation"),
  component: ReconciliationPage,
})
