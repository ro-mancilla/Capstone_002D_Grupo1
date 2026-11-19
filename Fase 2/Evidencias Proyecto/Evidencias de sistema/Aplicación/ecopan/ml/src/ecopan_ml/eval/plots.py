"""Defence plots (plan 08 §7 item 4). PNGs committed; the data behind them is not.

A table of pooled metrics tells a committee whether the model is better. It does not tell them
*how* it is better, or where it fails -- and "where does it fail" is the question that separates a
project that understands its model from one that ran a script. These three plots are chosen to
answer exactly the questions a reviewer asks next.
"""

from pathlib import Path

import matplotlib

# Non-interactive backend, selected before pyplot is imported. Plot generation runs in `make plots`
# and in CI, neither of which has a display; without this, matplotlib tries to open a GUI backend
# and fails on a headless machine.
matplotlib.use("Agg")

import matplotlib.pyplot as plt
import pandas as pd

from ecopan_ml.baselines.naive import SERIES_KEYS


def plot_predicted_vs_actual(
    predictions: pd.DataFrame,
    output_path: Path,
    *,
    series_keys: tuple[str, str],
    title: str | None = None,
) -> Path:
    """Actual and predicted over the test window, for one series.

    The single most informative plot, because it exposes failure modes a metric averages away: a
    forecast that is right on level but a day out of phase, one that has learned the weekly shape
    but not the trend, or one that has collapsed to a flat line (the classic recursive-forecast
    degeneration). Plan 08 asks for three series -- high, medium and low volume -- because models
    typically fail differently at different scales.
    """
    store, family = series_keys
    subset = predictions.loc[
        (predictions[SERIES_KEYS[0]] == store) & (predictions[SERIES_KEYS[1]] == family)
    ].sort_values("date")

    if subset.empty:
        raise ValueError(f"no predictions for series {series_keys}")

    fig, ax = plt.subplots(figsize=(11, 4))
    ax.plot(subset["date"], subset["y_true"], label="actual", linewidth=1.6, color="#1f2933")
    ax.plot(
        subset["date"],
        subset["y_pred"],
        label="predicted",
        linewidth=1.6,
        color="#d97757",
        linestyle="--",
    )
    ax.set_title(title or f"{store} / {family} — test window")
    ax.set_ylabel("daily demand")
    ax.legend(frameon=False)
    ax.grid(alpha=0.25, linewidth=0.6)
    fig.autofmt_xdate()
    fig.tight_layout()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=140)
    plt.close(fig)
    return output_path


def plot_error_by_horizon(by_horizon: pd.DataFrame, output_path: Path) -> Path:
    """MAE against days-ahead, per estimator.

    The plot that makes recursive forecasting's cost visible. The model's curve should slope upward
    (each step feeds on its own predictions); the baselines' should be flatter. If the model starts
    below the baseline and crosses above it at some horizon, that is a genuine finding and an
    argument for shortening the forecast window -- not something to average into a single number.
    """
    fig, ax = plt.subplots(figsize=(7, 4))
    for estimator, group in by_horizon.groupby("estimator", observed=True):
        ordered = group.sort_values("horizon")
        ax.plot(ordered["horizon"], ordered["mae"], marker="o", label=str(estimator))

    ax.set_xlabel("forecast horizon (days ahead)")
    ax.set_ylabel("MAE")
    ax.set_title("Error growth with horizon")
    ax.legend(frameon=False)
    ax.grid(alpha=0.25, linewidth=0.6)
    fig.tight_layout()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=140)
    plt.close(fig)
    return output_path


def plot_feature_importance(booster, output_path: Path, *, top_n: int = 20) -> Path:
    """Gain-based feature importance.

    "Gain" (total loss reduction attributable to splits on a feature) rather than the default
    "split" count, which merely counts how often a feature was used and over-credits
    high-cardinality features that offer many possible split points without contributing much.

    Read with care, and say so at the defence: importance is *associational*, not causal, and it is
    unreliable when features are correlated -- `lag_7` and `roll_mean_7` overlap heavily, so
    boosting will pick one somewhat arbitrarily and the other's importance collapses. The plot is
    evidence about what the model used, not about what drives demand.
    """
    importance = (
        pd.DataFrame(
            {
                "feature": booster.feature_name(),
                "gain": booster.feature_importance(importance_type="gain"),
            }
        )
        .sort_values("gain", ascending=True)
        .tail(top_n)
    )

    fig, ax = plt.subplots(figsize=(7, max(4, 0.32 * len(importance))))
    ax.barh(importance["feature"], importance["gain"], color="#8f7fd4")
    ax.set_xlabel("total gain")
    ax.set_title("Feature importance (gain)")
    ax.grid(axis="x", alpha=0.25, linewidth=0.6)
    fig.tight_layout()

    output_path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(output_path, dpi=140)
    plt.close(fig)
    return output_path


def pick_family_series(predictions: pd.DataFrame, family: str, n: int = 1) -> list[tuple[str, str]]:
    """The highest-volume series inside one product family.

    Separate from `pick_representative_series` because that one spans the whole dataset by volume,
    and the families it lands on (groceries, stationery, homeware) are not what this system is for.
    The flagship family gets a figure of its own or the defence has charts of everything except the
    product category it is about.

    Still not cherry-picking, on two counts: the family is fixed by the domain rather than by the
    scores — and the model does *worse* here than pooled, so this figure argues against the
    headline, not for it — and within the family the pick is by volume, a property of the data
    settled before any model existed.
    """
    subset = predictions.loc[predictions[SERIES_KEYS[1]].astype(str) == family]
    if subset.empty:
        return []
    volumes = (
        subset.groupby(SERIES_KEYS, observed=True)["y_true"].sum().sort_values(ascending=False)
    )
    picked: list[tuple[str, str]] = []
    for key in volumes.index[:n]:
        parts = key if isinstance(key, tuple) else (key,)
        picked.append(tuple(str(part) for part in parts))  # type: ignore[arg-type]
    return picked


def pick_representative_series(predictions: pd.DataFrame, n: int = 3) -> list[tuple[str, str]]:
    """High / medium / low volume series, by test-window total actual demand.

    Picked by *volume*, deliberately not by error. Choosing the series where the model happens to do
    well would be cherry-picking; volume is a property of the data, fixed before any model was
    fitted, so the selection cannot flatter the result.
    """
    volumes = (
        predictions.groupby(SERIES_KEYS, observed=True)["y_true"].sum().sort_values(ascending=False)
    )
    if volumes.empty:
        return []

    positions = [0, len(volumes) // 2, len(volumes) - 1][:n]
    seen: list[tuple[str, str]] = []
    for position in positions:
        key = volumes.index[position]
        key_tuple = tuple(str(part) for part in (key if isinstance(key, tuple) else (key,)))
        if key_tuple not in seen:
            seen.append(key_tuple)  # type: ignore[arg-type]
    return seen  # type: ignore[return-value]
