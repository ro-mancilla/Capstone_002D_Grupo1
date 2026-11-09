import { describe, expect, it, beforeEach } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { ThemeToggle } from "@/components/shared/ThemeToggle"
import { THEME_STORAGE_KEY } from "@/lib/theme"

/**
 * Regression guard: the menu items used `onSelect`, which Base UI's `Menu.Item` does not have --
 * it exposes `onClick`. React happily bound it as the DOM text-selection event, so no handler
 * ever ran and the theme could not be changed at all. Nothing type-checked wrong and nothing
 * logged; the button simply did nothing.
 *
 * This drives the real menu, so it fails again if the prop regresses or the menu stops
 * dispatching clicks.
 */
describe("ThemeToggle", () => {
  beforeEach(() => {
    document.documentElement.classList.remove("dark")
    window.localStorage.clear()
  })

  it("applies the dark class and persists the preference when 'Oscuro' is chosen", async () => {
    const user = userEvent.setup()
    render(<ThemeToggle />)

    await user.click(screen.getByRole("button", { name: /cambiar tema/i }))
    await user.click(await screen.findByRole("menuitem", { name: /oscuro/i }))

    await waitFor(() => {
      expect(document.documentElement.classList.contains("dark")).toBe(true)
    })
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("dark")
  })

  it("removes the dark class again when 'Claro' is chosen", async () => {
    const user = userEvent.setup()
    render(<ThemeToggle />)

    await user.click(screen.getByRole("button", { name: /cambiar tema/i }))
    await user.click(await screen.findByRole("menuitem", { name: /oscuro/i }))
    await waitFor(() => expect(document.documentElement.classList.contains("dark")).toBe(true))

    await user.click(screen.getByRole("button", { name: /cambiar tema/i }))
    await user.click(await screen.findByRole("menuitem", { name: /claro/i }))

    await waitFor(() => {
      expect(document.documentElement.classList.contains("dark")).toBe(false)
    })
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe("light")
  })
})
