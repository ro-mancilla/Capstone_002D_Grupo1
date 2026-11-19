import type { Role } from "@/api/types"

export { MANAGER_APP_ROLES } from "@/api/types"

/**
 * Client-side capability gate (plan 07 §3: "table-driven capability lookup ... no scattered
 * role ifs" -- mirrors 01 §4's server-side `PermissionRegistry` shape, but these are UI-only
 * concerns; the backend's own `requirePermission`/`requireModule` checks are the real
 * enforcement (01 §7.2), this is belt-and-suspenders so the UI doesn't even offer actions a
 * request would 403 on).
 */
export type Capability =
  | "USE_MANAGER_APP"
  | "VIEW_DASHBOARD"
  | "MANAGE_BRANCH_STOCK"
  | "MANAGE_FULL_ADMIN"
  | "SELECT_BRANCH"

const ROLE_CAPABILITIES: Record<Role, ReadonlySet<Capability>> = {
  CASHIER: new Set(),
  BAKER: new Set(),
  BRANCH_MANAGER: new Set<Capability>(["USE_MANAGER_APP", "VIEW_DASHBOARD", "MANAGE_BRANCH_STOCK"]),
  GENERAL_MANAGER: new Set<Capability>([
    "USE_MANAGER_APP",
    "VIEW_DASHBOARD",
    "MANAGE_BRANCH_STOCK",
    "MANAGE_FULL_ADMIN",
    "SELECT_BRANCH",
  ]),
  ADMIN: new Set<Capability>([
    "USE_MANAGER_APP",
    "VIEW_DASHBOARD",
    "MANAGE_BRANCH_STOCK",
    "MANAGE_FULL_ADMIN",
    "SELECT_BRANCH",
  ]),
}

export function can(role: Role | null | undefined, capability: Capability): boolean {
  if (!role) return false
  return ROLE_CAPABILITIES[role].has(capability)
}
