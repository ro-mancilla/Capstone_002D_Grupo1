import type { ModuleId, Session } from "@/api/types"

export type { ModuleId }

/**
 * Entitlement-driven nav (01 §7.3 / plan 07 §3): `hasModule` is the single lookup every nav
 * filter and route guard goes through -- a disabled module makes a section absent entirely
 * (hidden, never greyed-out). `POS_SALES` is deliberately absent from the tenant-toggleable set
 * (data/domain/Enums.kt: always-on, never gated) but this dashboard has no POS surface anyway
 * (plan 07 §7), so it never needs to be checked here.
 */
export function hasModule(session: Session | null | undefined, module: ModuleId): boolean {
  return session?.enabledModules.includes(module) ?? false
}

export function hasEveryModule(
  session: Session | null | undefined,
  modules: readonly ModuleId[]
): boolean {
  return modules.every((module) => hasModule(session, module))
}
