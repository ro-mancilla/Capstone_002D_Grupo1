"""Typed access to `configs/favorita.yaml`.

One config object threaded through every stage. The point is not tidiness: it is that the exact
config used by a training run is snapshotted next to the model artifact, so "which features, which
split, which hyperparameters produced this number" is answerable months later from a git SHA rather
than from memory.
"""

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

from ecopan_ml.features.build import FeatureConfig


@dataclass(frozen=True)
class DatasetConfig:
    name: str
    raw_dir: Path
    processed_path: Path
    families: list[str] | None
    stores: list[int] | None
    nrows: int | None


@dataclass(frozen=True)
class SplitConfig:
    train_end: str
    val_end: str
    test_end: str


@dataclass(frozen=True)
class EvaluationConfig:
    horizon: int
    step: int


@dataclass(frozen=True)
class BacktestWindow:
    """One walk-forward fold: train to `train_end`, early-stop on the next month, score the rest.

    `score_end` must fall strictly before the reserved test window. The backtest exists to show the
    headline gain is not an artefact of one seasonal position, and it would defeat itself by
    spending the holdout to do so.
    """

    train_end: str
    val_end: str
    score_start: str
    score_end: str


@dataclass(frozen=True)
class ThresholdConfig:
    """The pre-registered success bar (plan 08 §3).

    `reference_baseline` is None until `make baselines` has run and written it back. A model
    evaluation that finds it still None should refuse to declare a verdict -- there is nothing to
    compare against, and inventing a reference after seeing model results is exactly the
    target-shopping this file exists to prevent.
    """

    min_relative_improvement: float
    reference_baseline: str | None


@dataclass(frozen=True)
class RunConfig:
    dataset: DatasetConfig
    split: SplitConfig
    evaluation: EvaluationConfig
    features: FeatureConfig
    model: dict[str, Any]
    artifacts_dir: Path
    plots_dir: Path
    threshold: ThresholdConfig
    backtest: tuple[BacktestWindow, ...] = ()
    raw: dict[str, Any] = field(default_factory=dict)

    @property
    def lgb_params(self) -> dict[str, Any]:
        """Model block minus the loop-control keys, which are `lgb.train` arguments, not params."""
        return {
            key: value
            for key, value in self.model.items()
            if key not in {"num_boost_round", "early_stopping_rounds"}
        }


def load_config(path: str | Path = "configs/favorita.yaml") -> RunConfig:
    path = Path(path)
    data = yaml.safe_load(path.read_text(encoding="utf-8"))

    dataset = data["dataset"]
    features = data["features"]
    threshold = data.get("threshold", {})

    return RunConfig(
        dataset=DatasetConfig(
            name=dataset["name"],
            raw_dir=Path(dataset["raw_dir"]),
            processed_path=Path(dataset["processed_path"]),
            families=dataset.get("families"),
            stores=dataset.get("stores"),
            nrows=dataset.get("nrows"),
        ),
        split=SplitConfig(**data["split"]),
        evaluation=EvaluationConfig(**data["evaluation"]),
        features=FeatureConfig(
            lags=tuple(features["lags"]),
            rolling_windows=tuple(features["rolling_windows"]),
            rolling_std_windows=tuple(features["rolling_std_windows"]),
            use_calendar=features["use_calendar"],
            use_holidays=features["use_holidays"],
            use_promo=features["use_promo"],
        ),
        model=dict(data["model"]),
        artifacts_dir=Path(data["artifacts"]["dir"]),
        plots_dir=Path(data["artifacts"]["plots_dir"]),
        threshold=ThresholdConfig(
            min_relative_improvement=float(threshold.get("min_relative_improvement", 0.10)),
            reference_baseline=threshold.get("reference_baseline"),
        ),
        backtest=tuple(
            BacktestWindow(**window) for window in data.get("backtest", {}).get("windows", [])
        ),
        raw=data,
    )
