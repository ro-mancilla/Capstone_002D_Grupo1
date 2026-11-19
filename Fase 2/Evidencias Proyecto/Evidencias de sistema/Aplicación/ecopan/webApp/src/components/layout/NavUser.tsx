import { ChevronsUpDown, LogOut } from "lucide-react"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar"
import type { Session } from "@/api/types"
import { t } from "@/lib/i18n"

/** Initials for the avatar fallback: first letters of the first two words, so "Ana Bravo" -> "AB"
 * and a single-word name -> its first two letters. Never renders empty. */
function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  if (words.length === 0) return "·"
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase()
  return (words[0][0] + words[1][0]).toUpperCase()
}

/**
 * The sidebar footer identity block (dashboard-01's `nav-user`). Replaces a bare ghost button:
 * an avatar keeps the row legible when the sidebar collapses to icons, and the branch the session
 * is scoped to is worth showing permanently -- in a multi-branch tenant it is the single most
 * load-bearing piece of context on screen, because every number on every page is filtered by it.
 */
export function NavUser({ session, onLogout }: { session: Session; onLogout: () => void }) {
  const { isMobile } = useSidebar()
  const branchName =
    session.branches.find((branch) => branch.id === session.branchId)?.name ?? t.userMenu.branchless

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu>
          <DropdownMenuTrigger
            render={
              <SidebarMenuButton
                size="lg"
                className="data-popup-open:bg-sidebar-accent data-popup-open:text-sidebar-accent-foreground"
              />
            }
          >
            <Avatar className="size-8 rounded-lg">
              <AvatarFallback className="rounded-lg bg-primary/12 text-primary text-xs font-semibold">
                {initials(session.displayName)}
              </AvatarFallback>
            </Avatar>
            <div className="grid flex-1 text-left text-sm leading-tight">
              <span className="truncate font-medium">{session.displayName}</span>
              <span className="text-muted-foreground truncate text-xs">{branchName}</span>
            </div>
            <ChevronsUpDown className="ml-auto size-4" />
          </DropdownMenuTrigger>
          <DropdownMenuContent
            className="w-(--anchor-width) min-w-56 rounded-lg"
            side={isMobile ? "bottom" : "right"}
            align="end"
            sideOffset={4}
          >
            {/* Base UI's `DropdownMenuLabel` is a `Menu.GroupLabel` and throws
                ("MenuGroupContext is missing") unless it sits inside a `Menu.Group`; the
                Radix version had no such requirement, which is how this shipped bare. */}
            <DropdownMenuGroup>
              <DropdownMenuLabel className="p-0 font-normal">
                <div className="flex items-center gap-2 px-1 py-1.5 text-left text-sm">
                  <Avatar className="size-8 rounded-lg">
                    <AvatarFallback className="rounded-lg bg-primary/12 text-primary text-xs font-semibold">
                      {initials(session.displayName)}
                    </AvatarFallback>
                  </Avatar>
                  <div className="grid flex-1 text-left text-sm leading-tight">
                    <span className="truncate font-medium">{session.displayName}</span>
                    <span className="text-muted-foreground truncate text-xs">{branchName}</span>
                  </div>
                </div>
              </DropdownMenuLabel>
            </DropdownMenuGroup>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={onLogout}>
              <LogOut className="size-4" />
              {t.auth.logout}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  )
}
