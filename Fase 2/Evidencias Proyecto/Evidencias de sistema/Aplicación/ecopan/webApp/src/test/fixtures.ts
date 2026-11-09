import { FIXTURE } from "@/mocks/handlers"
import type { ModuleId, Session } from "@/api/types"

/** Every module a tenant can have on, so a test can name what it turns *off*. */
export const ALL_MODULES: ModuleId[] = [
  "POS_SALES",
  "INVENTORY_FORMULAS",
  "DEMAND_FORECASTING",
  "MULTI_BRANCH_ROLES",
  "ANALYTICS_DASHBOARD",
]

/**
 * A signed-in manager, built from the same fixture the MSW handlers serve, so a unit test and an
 * integration test are talking about the same tenant. Override whatever the test is about.
 */
export function managerSession(overrides: Partial<Session> = {}): Session {
  return {
    userId: FIXTURE.genMgr.id,
    tenantId: FIXTURE.tenant.id,
    tenantSlug: FIXTURE.tenant.slug,
    branchId: null,
    role: "GENERAL_MANAGER",
    displayName: FIXTURE.genMgr.displayName,
    tenantName: FIXTURE.tenant.name,
    currencyCode: FIXTURE.tenant.currencyCode,
    enabledModules: ALL_MODULES,
    branches: [FIXTURE.branch],
    ...overrides,
  }
}
