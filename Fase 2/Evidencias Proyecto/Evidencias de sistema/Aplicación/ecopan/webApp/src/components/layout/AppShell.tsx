import type { CSSProperties } from "react"
import { Link, Outlet, useLocation, useNavigate, useRouter } from "@tanstack/react-router"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarInset,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarProvider,
} from "@/components/ui/sidebar"
import { BrandMark } from "@/components/layout/BrandMark"
import { NavUser } from "@/components/layout/NavUser"
import { SiteHeader } from "@/components/layout/SiteHeader"
import { useAuth } from "@/features/auth/AuthContext"
import { useTenantProfileSync } from "@/hooks/useTenantProfileSync"
import { useServerEvents } from "@/api/queries/events"
import { buildNavSections } from "@/lib/destinations"
import { t } from "@/lib/i18n"

/** The authenticated app shell (plan 07 §3): shadcn `sidebar` over `--sidebar-*` tokens, a sticky
 * breadcrumb header, and a data-driven nav from `buildNavSections`.
 *
 * `variant="inset"` (the dashboard-01 shell) floats the content on the page background instead of
 * butting it against the sidebar edge -- which is what lets the cards below read as a stack of
 * physical cards rather than regions of one flat pane. `--header-height` is defined here, at the
 * provider, because both the header and the sticky offsets inside pages measure against it. */
export function AppShell() {
  const { session, logout } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const router = useRouter()

  // Mounted once, app-wide: keeps `session.enabledModules`/`branches` synced to `GET /tenant` so
  // `buildNavSections` below re-filters live on any module toggle (plan 07 §11 item 7).
  useTenantProfileSync()
  // Mounted once, app-wide (plan 07 §6/§10 item 10): opens the SSE stream and invalidates the
  // matching KPI/stock/movement queries on `batch-ready`/`sync-available`.
  useServerEvents()

  const sections = buildNavSections(session)

  async function handleLogout() {
    await logout()
    await router.invalidate()
    navigate({ to: "/login" })
  }

  return (
    <SidebarProvider
      style={
        {
          // The design system's fixed structural widths: a 244px nav
          // sidebar, 76px collapsed (set on the primitive), over a 76px top bar.
          "--sidebar-width": "244px",
          "--header-height": "76px",
        } as CSSProperties
      }
    >
      <Sidebar collapsible="icon" variant="inset">
        <SidebarHeader>
          <SidebarMenu>
            <SidebarMenuItem>
              <SidebarMenuButton
                size="lg"
                render={<Link to="/analytics" />}
                className="hover:bg-transparent active:bg-transparent"
              >
                <div className="bg-primary text-primary-foreground flex aspect-square size-9 items-center justify-center rounded-xs">
                  <BrandMark />
                </div>
                <div className="grid flex-1 text-left leading-tight">
                  <span className="font-display text-base font-semibold">{t.app.name}</span>
                  <span className="text-muted-foreground truncate text-xs">{t.app.tagline}</span>
                </div>
              </SidebarMenuButton>
            </SidebarMenuItem>
          </SidebarMenu>
        </SidebarHeader>

        <SidebarContent>
          {sections.map((section) => (
            <SidebarGroup key={section.label}>
              <SidebarGroupLabel>{section.label}</SidebarGroupLabel>
              <SidebarGroupContent>
                <SidebarMenu>
                  {section.items.map((item) => (
                    <SidebarMenuItem key={item.to}>
                      {/* `tooltip` is what makes the icon-collapsed rail navigable -- without it a
                          collapsed sidebar is a column of unlabelled glyphs. */}
                      <SidebarMenuButton
                        render={<Link to={item.to} />}
                        isActive={location.pathname === item.to}
                        tooltip={item.label}
                      >
                        <item.icon />
                        <span>{item.label}</span>
                      </SidebarMenuButton>
                    </SidebarMenuItem>
                  ))}
                </SidebarMenu>
              </SidebarGroupContent>
            </SidebarGroup>
          ))}
        </SidebarContent>

        <SidebarFooter>
          {session && <NavUser session={session} onLogout={handleLogout} />}
        </SidebarFooter>
      </Sidebar>

      <SidebarInset>
        <SiteHeader sections={sections} pathname={location.pathname} />
        {/* `@container/main` so page content can respond to the *content* width rather than the
            viewport -- collapsing the sidebar then genuinely re-flows the card grids below. */}
        <main className="@container/main flex flex-1 flex-col overflow-auto">
          {/* Page gutters: 20px on mobile, 28px on desktop. */}
          <div className="flex flex-1 flex-col gap-6 p-5 pb-28 md:p-7 md:pb-7">
            <Outlet />
          </div>
        </main>
      </SidebarInset>
    </SidebarProvider>
  )
}
