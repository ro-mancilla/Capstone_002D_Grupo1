import { describe, expect, it } from "vitest"
import { render, screen } from "@testing-library/react"
import { StatusBadge } from "./StatusBadge"

describe("StatusBadge", () => {
  it.each([
    ["low", "Stock bajo", true],
    ["negative", "Stock negativo", true],
    ["outOfStock", "Agotado", true],
    ["inactive", "Inactivo", false],
  ] as const)("%s reads as the word %s, with an icon where it flags a problem", (status, word, hasIcon) => {
    const { container } = render(<StatusBadge status={status} />)

    expect(screen.getByText(word)).toBeInTheDocument()
    expect(container.querySelector("svg") !== null).toBe(hasIcon)
  })
})
