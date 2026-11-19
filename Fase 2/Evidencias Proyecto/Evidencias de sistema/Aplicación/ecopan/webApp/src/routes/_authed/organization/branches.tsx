import { createFileRoute } from "@tanstack/react-router"
import { BranchesPage } from "@/features/admin-org/BranchesPage"
import { requireEntitlements } from "@/lib/guards"

/** The one Sucursales route. No `module` or `MANAGE_FULL_ADMIN` guard, deliberately -- it stays
 * reachable with `MULTI_BRANCH_ROLES` off (plan 07 §5: "with it off, the single branch renders
 * read-only") and for branch managers. `BranchesPage` decides what each audience sees:
 * revenue (dashboard entitlement) and create/edit (`MANAGE_FULL_ADMIN` + the module). */
export const Route = createFileRoute("/_authed/organization/branches")({
  beforeLoad: requireEntitlements("/organization/branches"),
  component: BranchesPage,
})
