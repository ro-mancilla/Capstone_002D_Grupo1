import { Badge } from "@/components/ui/badge"
import { modelKindLabel, type ModelKind } from "@/lib/labels"

/** The estimator behind a forecast. Never claims a trained model for a baseline number. */
export function EstimatorChip({ kind }: { kind: ModelKind }) {
  return <Badge variant={kind === "MODEL" ? "info" : "secondary"}>{modelKindLabel(kind)}</Badge>
}
