import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

export interface ChoiceOption<T extends string = string> {
  value: T
  label: string
}

export interface ChoiceSelectProps<T extends string = string> {
  /** Stated once. The trigger's label map and the list's items are both derived from this. */
  options: readonly ChoiceOption<T>[]
  /** `""` selects nothing, which is what renders [placeholder]. */
  value: T | ""
  /** Only ever called with one of [options]' values. */
  onValueChange: (value: T) => void
  placeholder?: string
  disabled?: boolean
  id?: string
  className?: string
  "aria-label"?: string
  "aria-invalid"?: boolean
}

/**
 * A single-choice dropdown over the Base UI select primitives (plan 07 §2: `components/ui/` is
 * shadcn output, hand-written composites live here).
 *
 * Base UI's `<Select.Value>` renders the selected **value**, resolving it to a label only through
 * the `items` map on the root -- unlike Radix, whose `SelectValue` reads the chosen item's own
 * children. Radix-shaped markup therefore type-checks, renders, and is silently wrong: triggers
 * across this app showed `__all__`, raw uuids and `WEIGHT` where Spanish labels belonged.
 *
 * The fix was applied by hand at every call site, which left each one stating its options twice
 * -- once as `items`, once as `<SelectItem>` children -- kept in agreement by a comment copied
 * verbatim across ten files. This takes the list once and derives both, so they cannot drift.
 *
 * The primitives stay exported for the grouped or custom-rendered case; this is the shape for
 * the ordinary one.
 */
export function ChoiceSelect<T extends string = string>({
  options,
  value,
  onValueChange,
  placeholder,
  disabled,
  id,
  className,
  "aria-label": ariaLabel,
  "aria-invalid": ariaInvalid,
}: ChoiceSelectProps<T>) {
  // Base UI infers its own value generic from `items`, and that inference collapses on an
  // unresolved `T`. Widen to `string` for the primitives -- the generic is what callers see,
  // and `T extends string` makes the widening sound.
  const items: ChoiceOption[] = [...options]
  const selected: string = value

  return (
    <Select
      items={items}
      value={selected}
      disabled={disabled}
      onValueChange={(next) => {
        // Unreachable while options are string-valued: Base UI emits `null` only for its
        // documented clearable-item pattern, a `{ value: null }` entry, which `ChoiceOption`
        // cannot express. Kept as the narrowing for Base UI's `T | null` signature.
        if (next === null) return
        onValueChange(next as T)
      }}
    >
      <SelectTrigger
        id={id}
        className={className}
        aria-label={ariaLabel}
        aria-invalid={ariaInvalid}
      >
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
