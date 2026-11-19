import { describe, expect, it } from "vitest"
import { render, screen } from "@testing-library/react"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

/**
 * Regression guard for a whole class of bug this app shipped: Base UI's `<Select.Value>` renders
 * the selected **value**, not the chosen item's text, unless `<Select.Root>` is given an `items`
 * label map. Radix's `SelectValue` reads the item's rendered children instead, so Radix-shaped
 * markup looks correct and silently isn't -- every trigger in the app read `__all__`, a raw uuid,
 * or `WEIGHT` where a Spanish label belonged.
 *
 * Every ordinary dropdown now goes through `components/shared/ChoiceSelect`, which derives both
 * halves from one option list, so the trap is unreachable from feature code. This keeps one test
 * on the primitives themselves, for the grouped/custom-rendered case that still uses them.
 */
describe("Select trigger label resolution", () => {
  const ALL = "__all__"

  it("shows the item's label, never the raw sentinel value, for the selected option", () => {
    const items = [
      { value: ALL, label: "Todas las sucursales" },
      { value: "branch-1", label: "Sucursal Principal" },
    ]
    render(
      <Select items={items} value={ALL}>
        <SelectTrigger>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ALL}>Todas las sucursales</SelectItem>
          <SelectItem value="branch-1">Sucursal Principal</SelectItem>
        </SelectContent>
      </Select>
    )

    const trigger = screen.getByRole("combobox")
    expect(trigger).toHaveTextContent("Todas las sucursales")
    expect(trigger).not.toHaveTextContent(ALL)
  })

})
