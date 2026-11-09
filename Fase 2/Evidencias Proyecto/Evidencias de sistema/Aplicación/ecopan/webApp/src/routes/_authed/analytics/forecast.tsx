import { createFileRoute } from "@tanstack/react-router"
import { ForecastPage } from "@/features/dashboard/ForecastPage"
import { requireEntitlements } from "@/lib/guards"

export const Route = createFileRoute("/_authed/analytics/forecast")({
  beforeLoad: requireEntitlements("/analytics/forecast"),
  component: ForecastPage,
})
