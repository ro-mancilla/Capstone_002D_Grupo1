import { afterEach, describe, expect, it } from "vitest"
import { act, render, screen } from "@testing-library/react"
import { setLiveUpdatesPaused } from "@/api/queries/events"
import { t } from "@/lib/i18n"
import { LiveUpdatesPaused } from "./LiveUpdatesPaused"

describe("LiveUpdatesPaused (#140)", () => {
  afterEach(() => {
    act(() => setLiveUpdatesPaused(false))
    document.documentElement.classList.remove("dark")
  })

  it("renders nothing while live", () => {
    const { container } = render(<LiveUpdatesPaused />)
    expect(container).toBeEmptyDOMElement()
  })

  it.each(["light", "dark"])("shows the paused notice from i18n in %s mode, and clears when live again", (mode) => {
    if (mode === "dark") document.documentElement.classList.add("dark")
    render(<LiveUpdatesPaused />)
    act(() => setLiveUpdatesPaused(true))

    const notice = screen.getByRole("status")
    expect(notice).toHaveTextContent(t.events.paused.label)
    expect(notice).toHaveAccessibleDescription(t.events.paused.description)

    act(() => setLiveUpdatesPaused(false))
    expect(screen.queryByRole("status")).toBeNull()
  })
})
