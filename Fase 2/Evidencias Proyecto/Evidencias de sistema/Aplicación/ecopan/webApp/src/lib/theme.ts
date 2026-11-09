/**
 * Three-state theme preference (light/dark/system), persisted in localStorage.
 * The actual `.dark` class application happens twice by design:
 *  - once, synchronously, in the inline <script> in `index.html` (pre-paint, avoids FOUC)
 *  - again here, reactively, whenever the user changes the preference or the OS theme changes
 *    while `system` is selected.
 */

export type ThemePreference = "light" | "dark" | "system"

export const THEME_STORAGE_KEY = "ecopan-web-theme"

const DARK_MEDIA_QUERY = "(prefers-color-scheme: dark)"

export function getStoredThemePreference(): ThemePreference {
  if (typeof window === "undefined") return "system"
  const stored = window.localStorage.getItem(THEME_STORAGE_KEY)
  if (stored === "light" || stored === "dark" || stored === "system") {
    return stored
  }
  return "system"
}

export function systemPrefersDark(): boolean {
  if (typeof window === "undefined") return false
  return window.matchMedia(DARK_MEDIA_QUERY).matches
}

export function resolveIsDark(preference: ThemePreference): boolean {
  return preference === "dark" || (preference === "system" && systemPrefersDark())
}

export function applyThemePreference(preference: ThemePreference): void {
  window.localStorage.setItem(THEME_STORAGE_KEY, preference)
  document.documentElement.classList.toggle("dark", resolveIsDark(preference))
}

export function watchSystemTheme(onChange: () => void): () => void {
  const media = window.matchMedia(DARK_MEDIA_QUERY)
  media.addEventListener("change", onChange)
  return () => media.removeEventListener("change", onChange)
}
