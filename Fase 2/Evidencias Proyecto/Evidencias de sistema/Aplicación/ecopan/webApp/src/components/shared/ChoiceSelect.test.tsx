import { describe, expect, it, vi } from "vitest"
import { render, screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { ChoiceSelect, type ChoiceOption } from "@/components/shared/ChoiceSelect"

/**
 * `ChoiceSelect` exists so the Base UI trap cannot be re-entered: `<Select.Value>` renders the
 * selected *value* unless the root is also handed a value->label map, and stating the options
 * twice to satisfy that is what let triggers show `__all__`, a raw uuid or `WEIGHT`. Here the
 * options are given once and both halves are derived from them -- so the first two tests below
 * go red if `items` is ever dropped from the root.
 */
const OPTIONS = [
  { value: "__all__", label: "Todas las sucursales" },
  { value: "branch-1", label: "Sucursal Principal" },
  { value: "branch-2", label: "Sucursal Norte" },
]

describe("ChoiceSelect", () => {
  it("shows the selected option's label, never its value", () => {
    render(<ChoiceSelect options={OPTIONS} value="__all__" onValueChange={vi.fn()} aria-label="Sucursal" />)

    const trigger = screen.getByRole("combobox")
    expect(trigger).toHaveTextContent("Todas las sucursales")
    expect(trigger).not.toHaveTextContent("__all__")
  })

  it("resolves the label for every option, not just the first", () => {
    render(<ChoiceSelect options={OPTIONS} value="branch-2" onValueChange={vi.fn()} aria-label="Sucursal" />)

    const trigger = screen.getByRole("combobox")
    expect(trigger).toHaveTextContent("Sucursal Norte")
    expect(trigger).not.toHaveTextContent("branch-2")
  })

  it("reports the chosen option's value as a plain string", async () => {
    const onValueChange = vi.fn()
    render(
      <ChoiceSelect options={OPTIONS} value="__all__" onValueChange={onValueChange} aria-label="Sucursal" />
    )

    const user = userEvent.setup()
    await user.click(screen.getByRole("combobox"))
    await user.click(await screen.findByRole("option", { name: "Sucursal Norte" }))

    expect(onValueChange).toHaveBeenCalledWith("branch-2")
    // Exactly one argument: Base UI passes event details second, which no call site wants.
    expect(onValueChange.mock.calls[0]).toHaveLength(1)
  })

  it("only ever reports a value the options offered", async () => {
    const onValueChange = vi.fn()
    const offered = OPTIONS.map((option) => option.value)
    render(
      <ChoiceSelect options={OPTIONS} value="__all__" onValueChange={onValueChange} aria-label="Sucursal" />
    )

    const user = userEvent.setup()
    for (const label of ["Sucursal Norte", "Sucursal Principal", "Todas las sucursales"]) {
      await user.click(screen.getByRole("combobox"))
      await user.click(await screen.findByRole("option", { name: label }))
    }

    expect(onValueChange).toHaveBeenCalledTimes(3)
    for (const [reported] of onValueChange.mock.calls) {
      expect(offered).toContain(reported)
    }
  })

  it("shows the placeholder when nothing is selected", () => {
    render(
      <ChoiceSelect
        options={OPTIONS}
        value=""
        onValueChange={vi.fn()}
        placeholder="Elige una sucursal"
        aria-label="Sucursal"
      />
    )

    expect(screen.getByRole("combobox")).toHaveTextContent("Elige una sucursal")
  })

  it("renders a wire enum's label rather than its code", () => {
    // `soldBy` is exactly the case CLAUDE.md warns about -- a raw `WEIGHT` reaching a trigger.
    // The typed `ChoiceOption<...>`/`vi.fn<...>` below also pin that the generic survives rather
    // than widening to `string`, but only in an editor: `tsconfig.app.json` excludes test files,
    // so nothing here is typechecked by `tsc -b`.
    const soldBy: ChoiceOption<"WEIGHT" | "UNIT">[] = [
      { value: "WEIGHT", label: "Por peso" },
      { value: "UNIT", label: "Por unidad" },
    ]
    const onValueChange = vi.fn<(value: "WEIGHT" | "UNIT") => void>()

    render(<ChoiceSelect options={soldBy} value="WEIGHT" onValueChange={onValueChange} aria-label="Venta" />)

    expect(screen.getByRole("combobox")).toHaveTextContent("Por peso")
  })

  it("carries the accessible name and disabled state through to the trigger", () => {
    render(
      <ChoiceSelect options={OPTIONS} value="branch-1" onValueChange={vi.fn()} aria-label="Sucursal" disabled />
    )

    expect(screen.getByRole("combobox", { name: "Sucursal" })).toBeDisabled()
  })

  it("marks itself invalid for a field in error", () => {
    render(
      <ChoiceSelect
        options={OPTIONS}
        value="branch-1"
        onValueChange={vi.fn()}
        aria-label="Sucursal"
        aria-invalid
      />
    )

    expect(screen.getByRole("combobox")).toHaveAttribute("aria-invalid", "true")
  })
})
