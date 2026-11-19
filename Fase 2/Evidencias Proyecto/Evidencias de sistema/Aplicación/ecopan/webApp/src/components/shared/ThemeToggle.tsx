import { useEffect, useState } from "react"
import { Monitor, Moon, Sun } from "lucide-react"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Button } from "@/components/ui/button"
import { t } from "@/lib/i18n"
import {
  applyThemePreference,
  getStoredThemePreference,
  watchSystemTheme,
  type ThemePreference,
} from "@/lib/theme"

const OPTIONS: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: "light", label: t.theme.light, icon: Sun },
  { value: "dark", label: t.theme.dark, icon: Moon },
  { value: "system", label: t.theme.system, icon: Monitor },
]

/** Three-state light/dark/system toggle (plan 07 §1). The inline script in `index.html` already
 * applied the right class before first paint; this component only needs to react to further
 * user/OS changes. */
export function ThemeToggle() {
  const [preference, setPreference] = useState<ThemePreference>(() => getStoredThemePreference())

  useEffect(() => {
    applyThemePreference(preference)
    if (preference !== "system") return
    return watchSystemTheme(() => applyThemePreference(preference))
  }, [preference])

  const ActiveIcon = OPTIONS.find((option) => option.value === preference)?.icon ?? Sun

  return (
    <DropdownMenu>
      <DropdownMenuTrigger render={<Button variant="ghost" size="icon" aria-label={t.theme.toggleLabel} />}>
        <ActiveIcon className="size-4" />
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {OPTIONS.map((option) => (
          <DropdownMenuItem key={option.value} onClick={() => setPreference(option.value)}>
            <option.icon className="size-4" />
            {option.label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
