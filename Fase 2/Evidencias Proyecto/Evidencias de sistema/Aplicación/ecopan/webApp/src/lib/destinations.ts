import type { LucideIcon } from "lucide-react"
import {
  Building2,
  ChartArea,
  ChartLine,
  Croissant,
  Scale,
  Factory,
  Trash2,
  LayoutDashboard,
  Settings2,
  Store,
  Users,
  Wheat,
} from "lucide-react"
import type { ModuleId, Session } from "@/api/types"
import { hasEveryModule } from "./entitlements"
import { can, type Capability } from "./roles"
import { t } from "./i18n"

/**
 * The destination table (plan 07 §3). Every fact about "what does this destination require" is
 * stated here once, and read by both the sidebar (`buildNavSections`) and the route guards
 * (`requireEntitlements`). Before this table the two were written separately and had drifted:
 * a section stayed visible with `ANALYTICS_DASHBOARD` off while the route refused it, and the
 * guard's redirect target was itself inside the refused subtree.
 *
 * Gates live on each destination, never on its section: sections group by task, and a task can
 * mix audiences (Organización holds Sucursales for a branch manager and Usuarios for admins).
 *
 * A disabled module or missing capability removes the destination entirely -- hidden, never
 * greyed-out (01 §7.3).
 *
 * Scope: this table decides *reachability*. A page that stays reachable but changes shape with
 * a module off -- `BranchesPage` going read-only without `MULTI_BRANCH_ROLES` (plan 07 §5) --
 * still asks `hasModule` for itself, and should.
 */
export type SectionId = "analytics" | "catalog" | "organization"

export interface Destination {
  to: string
  label: string
  /** Rendered ahead of the label in the sidebar, so a collapsed icon-only rail has something
   * to show. Carried here rather than mapped in `AppShell` so the table stays declarative. */
  icon: LucideIcon
  /** The task this destination belongs to. Sections group by what you are doing (look at the
   * numbers, curate the catalog, set up the organization), not by read versus edit. */
  section: SectionId
  /** Every module this destination needs. Stated in full on each row: there is no section-level
   * gate to inherit, so reading a row tells you exactly what its route requires. */
  modules?: readonly ModuleId[]
  /** Every capability this destination needs, stated in full for the same reason. */
  capabilities?: readonly Capability[]
}

const SECTIONS: readonly { id: SectionId; label: string }[] = [
  { id: "analytics", label: t.nav.sectionAnalytics },
  { id: "catalog", label: t.nav.sectionCatalog },
  { id: "organization", label: t.nav.sectionOrganization },
]

/** The analytics pages all read `/analytics/*` endpoints behind this module and capability. */
const ANALYTICS_MODULES = ["ANALYTICS_DASHBOARD"] as const satisfies readonly ModuleId[]
const VIEW_ANALYTICS = ["VIEW_DASHBOARD"] as const satisfies readonly Capability[]
/** Day-to-day catalog and branch upkeep: a `BRANCH_MANAGER` reaches it, the pages themselves hide
 * create/edit/delete from anyone without `MANAGE_FULL_ADMIN`. */
const MANAGE_STOCK = ["MANAGE_BRANCH_STOCK"] as const satisfies readonly Capability[]
const FULL_ADMIN = ["MANAGE_FULL_ADMIN"] as const satisfies readonly Capability[]

export const DESTINATIONS = [
  {
    to: "/analytics",
    label: t.nav.dashboardOverview,
    icon: LayoutDashboard,
    section: "analytics",
    modules: ANALYTICS_MODULES,
    capabilities: VIEW_ANALYTICS,
  },
  {
    to: "/analytics/sales",
    label: t.nav.sales,
    icon: ChartLine,
    section: "analytics",
    modules: ANALYTICS_MODULES,
    capabilities: VIEW_ANALYTICS,
  },
  {
    to: "/analytics/forecast",
    label: t.nav.forecast,
    icon: ChartArea,
    section: "analytics",
    modules: ["ANALYTICS_DASHBOARD", "DEMAND_FORECASTING"],
    capabilities: VIEW_ANALYTICS,
  },
  // Dough kilos and batch count per product and branch, from `/analytics/production-summary`.
  {
    to: "/analytics/production",
    label: t.nav.production,
    icon: Factory,
    section: "analytics",
    modules: ANALYTICS_MODULES,
    capabilities: VIEW_ANALYTICS,
  },
  // Waste compares production with sales, so it needs the same inventory/production data the
  // Insumos page does.
  {
    to: "/analytics/waste",
    label: t.nav.waste,
    icon: Trash2,
    section: "analytics",
    modules: ["ANALYTICS_DASHBOARD", "INVENTORY_FORMULAS"],
    capabilities: VIEW_ANALYTICS,
  },
  // The ingredient ledger over a closed range. The backend gates it on STOCK_READ +
  // INVENTORY_FORMULAS; it lives under Análisis because it is a report, not a place to edit stock.
  {
    to: "/analytics/reconciliation",
    label: t.nav.reconciliation,
    icon: Scale,
    section: "analytics",
    modules: ["ANALYTICS_DASHBOARD", "INVENTORY_FORMULAS"],
    capabilities: VIEW_ANALYTICS,
  },

  // Product management *and* Almacén stock, on one page. No module gates it -- Almacén stock is
  // core catalog.
  {
    to: "/catalog/products",
    label: t.nav.adminProducts,
    icon: Croissant,
    section: "catalog",
    capabilities: MANAGE_STOCK,
  },
  // Insumos: the ingredient list with stock, ingredient CRUD and stock entry on one page (plan 07
  // §3: "branch-locked dashboard + stock entry").
  {
    to: "/catalog/ingredients",
    label: t.nav.adminIngredients,
    icon: Wheat,
    section: "catalog",
    modules: ["INVENTORY_FORMULAS"],
    capabilities: MANAGE_STOCK,
  },

  // The one Sucursales page: branch list, per-branch revenue and branch management. No module
  // gate, so every manager reaches the list. The page narrows itself: revenue only when
  // `/analytics` is entitled, create/edit only with `MANAGE_FULL_ADMIN` + `MULTI_BRANCH_ROLES`
  // (plan 07 §5).
  {
    to: "/organization/branches",
    label: t.nav.adminBranches,
    icon: Building2,
    section: "organization",
    capabilities: MANAGE_STOCK,
  },
  {
    to: "/organization/users",
    label: t.nav.adminUsers,
    icon: Users,
    section: "organization",
    capabilities: FULL_ADMIN,
  },
  {
    to: "/organization/modules",
    label: t.nav.adminModules,
    icon: Settings2,
    section: "organization",
    capabilities: FULL_ADMIN,
  },
  // The bakery profile. Labelled "Panadería" by explicit product choice.
  {
    to: "/organization/tenant",
    label: t.nav.adminTenant,
    icon: Store,
    section: "organization",
    capabilities: FULL_ADMIN,
  },
] as const satisfies readonly Destination[]

/** Every path in the table. A route file guarding a path not listed here is a type error. */
export type DestinationPath = (typeof DESTINATIONS)[number]["to"]

interface Entitlements {
  modules: readonly ModuleId[]
  capabilities: readonly Capability[]
}

/** What `to` requires, exactly as its row states it. Private: `isEntitled` is the question
 * callers actually have, and exporting both invites one of them drifting from the other. */
function entitlementsFor(to: DestinationPath): Entitlements {
  const destination = DESTINATIONS.find((candidate) => candidate.to === to) as Destination | undefined
  if (!destination) throw new Error(`Unknown destination: ${to}`)

  return { modules: destination.modules ?? [], capabilities: destination.capabilities ?? [] }
}

/** Whether `session` may open `to`. The sidebar and the guards both ask this, so they agree. */
export function isEntitled(session: Session | null | undefined, to: DestinationPath): boolean {
  const { modules, capabilities } = entitlementsFor(to)
  return (
    hasEveryModule(session, modules) &&
    capabilities.every((capability) => can(session?.role, capability))
  )
}

/**
 * The first destination in table order this session can open, or `null` if there is none.
 *
 * Table order is the fallback landing order, so moving a row in `DESTINATIONS` changes where
 * managers land after signing in and after a denial. That is deliberate -- the overview is
 * first because it is the intended home -- but it means the order is behaviour, not layout.
 */
export function firstEntitled(session: Session | null | undefined): DestinationPath | null {
  const landing = DESTINATIONS.find((destination) => isEntitled(session, destination.to))
  return landing?.to ?? null
}

/**
 * Where to send someone who has just signed in, or who has just been refused. The one place
 * that decides a landing target, so the answer cannot differ between the guards, the app root
 * and the login form.
 *
 * `requested` is an untrusted href -- typically `?redirect=` carried through login, which may
 * point at a route this session cannot open (an unauthenticated hit on `/analytics` sets it to
 * exactly that). It is honoured only if it names a destination they are entitled to; otherwise
 * they land on the first one they are. `null` means entitled to nothing at all.
 */
export function resolveLanding(
  session: Session | null | undefined,
  requested?: string | null
): DestinationPath | null {
  if (requested) {
    const pathname = requested.split(/[?#]/)[0]
    const match = DESTINATIONS.find((destination) => destination.to === pathname)
    if (match && isEntitled(session, match.to)) return match.to
  }
  return firstEntitled(session)
}

export interface NavItem {
  label: string
  to: DestinationPath
  icon: LucideIcon
}

export interface NavSection {
  label: string
  items: NavItem[]
}

/** The sidebar, built from the same table the guards read. */
export function buildNavSections(session: Session | null | undefined): NavSection[] {
  const sections: NavSection[] = []

  for (const section of SECTIONS) {
    const items = DESTINATIONS.filter(
      (destination) => destination.section === section.id && isEntitled(session, destination.to)
    ).map(({ to, label, icon }) => ({ to, label, icon }))

    // A section with nothing entitled is dropped, never rendered empty or greyed-out.
    if (items.length > 0) sections.push({ label: section.label, items })
  }

  return sections
}
