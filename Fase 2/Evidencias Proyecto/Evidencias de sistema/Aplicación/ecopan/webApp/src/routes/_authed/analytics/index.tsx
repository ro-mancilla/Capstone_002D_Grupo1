import { createFileRoute } from "@tanstack/react-router"
import { OverviewPage } from "@/features/dashboard/OverviewPage"
import { requireEntitlements } from "@/lib/guards"

export const Route = createFileRoute("/_authed/analytics/")({
  beforeLoad: requireEntitlements("/analytics"),
  component: OverviewPage,
})
