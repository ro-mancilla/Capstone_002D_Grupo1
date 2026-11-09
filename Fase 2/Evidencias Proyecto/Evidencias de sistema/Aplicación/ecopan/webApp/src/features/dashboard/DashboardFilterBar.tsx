import { Button } from "@/components/ui/button"
import { ChoiceSelect } from "@/components/shared/ChoiceSelect"
import { Input } from "@/components/ui/input"
import type { BranchSummaryDto } from "@/api/types"
import { t } from "@/lib/i18n"
import type { DashboardFilters, DateRangePreset } from "./filters"

const ALL_BRANCHES = "__all__"

const PRESETS: { value: DateRangePreset; label: string }[] = [
  { value: "today", label: t.dashboard.filters.rangeToday },
  { value: "7d", label: t.dashboard.filters.rangeWeek },
  { value: "30d", label: t.dashboard.filters.rangeMonth },
  { value: "custom", label: t.dashboard.filters.rangeCustom },
]

interface DashboardFilterBarProps {
  filters: DashboardFilters
  branches: BranchSummaryDto[]
  showBranchSelect: boolean
  onBranchChange: (branchId: string | undefined) => void
  onPresetChange: (preset: DateRangePreset) => void
  onCustomRangeChange: (from: number, to: number) => void
}

function toDateInputValue(epochMillis: number): string {
  return new Date(epochMillis).toISOString().slice(0, 10)
}

function fromDateInputValue(value: string): number {
  return new Date(`${value}T00:00:00`).getTime()
}

/** The one filter bar every `features/dashboard/*` page mounts (plan 07 §4/§11 item 8) -- branch
 * picker (GM/ADMIN only, hidden for a branch-locked manager) + date-range presets, with a custom
 * from/to pair when "Personalizado" is picked. */
export function DashboardFilterBar({
  filters,
  branches,
  showBranchSelect,
  onBranchChange,
  onPresetChange,
  onCustomRangeChange,
}: DashboardFilterBarProps) {
  const strings = t.dashboard.filters

  const branchItems = [
    { value: ALL_BRANCHES, label: strings.allBranches },
    ...branches.map((branch) => ({ value: branch.id, label: branch.name })),
  ]

  return (
    <div className="flex flex-wrap items-center gap-2">
      {showBranchSelect && (
        <ChoiceSelect
          options={branchItems}
          value={filters.branchId ?? ALL_BRANCHES}
          onValueChange={(value) => onBranchChange(value === ALL_BRANCHES ? undefined : value)}
          className="w-56"
          aria-label={strings.branchLabel}
          placeholder={strings.branchLabel}
        />
      )}

      <div className="flex items-center gap-1">
        {PRESETS.map((preset) => (
          <Button
            key={preset.value}
            type="button"
            size="sm"
            variant={filters.preset === preset.value ? "default" : "outline"}
            onClick={() => onPresetChange(preset.value)}
          >
            {preset.label}
          </Button>
        ))}
      </div>

      {filters.preset === "custom" && (
        <div className="flex items-center gap-2">
          <label className="text-muted-foreground flex items-center gap-1 text-sm">
            {strings.fromLabel}
            <Input
              type="date"
              className="w-40"
              value={toDateInputValue(filters.from)}
              onChange={(event) =>
                onCustomRangeChange(fromDateInputValue(event.target.value), filters.to)
              }
            />
          </label>
          <label className="text-muted-foreground flex items-center gap-1 text-sm">
            {strings.toLabel}
            <Input
              type="date"
              className="w-40"
              value={toDateInputValue(filters.to)}
              onChange={(event) =>
                onCustomRangeChange(filters.from, fromDateInputValue(event.target.value))
              }
            />
          </label>
        </div>
      )}
    </div>
  )
}
