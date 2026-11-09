"""The evaluation harness: every estimator scored by the same code, over the same origins.

Plan 08 §3 makes this a hard rule -- "never compare numbers produced by different evaluation code".
It sounds pedantic until you notice how most published "our model beats the baseline" claims
actually fail. The model gets a careful rolling-origin evaluation; the baseline gets a quick
one-liner computed slightly differently -- a different set of dates, a different NaN policy, a
horizon of 1 instead of 7. The margin that gets reported is then partly an artifact of the
comparison, not of the model.

The defence here is structural rather than procedural. `evaluate_estimator` takes a *callable* with
a fixed signature:

    (history: DataFrame, target_dates: DatetimeIndex) -> DataFrame[..., y_pred]

The baselines and the LightGBM recursive forecaster are both wrapped to that signature and handed
to the same function. They therefore see the same origins, the same history cutoffs, the same
non-negativity clipping, and the same scoring -- by construction, not by care.
"""

from collections.abc import Callable
from dataclasses import dataclass

import pandas as pd

from ecopan_ml.baselines.naive import SERIES_KEYS, clip_to_non_negative
from ecopan_ml.eval.metrics import ErrorMetrics, compute_metrics, metrics_by_group
from ecopan_ml.eval.rolling_origin import generate_origins, iter_origin_slices

Estimator = Callable[[pd.DataFrame, pd.DatetimeIndex], pd.DataFrame]


@dataclass
class EvaluationResult:
    """Everything a reviewer needs to check one estimator's claim."""

    name: str
    pooled: ErrorMetrics
    per_origin: pd.DataFrame
    per_series: pd.DataFrame
    predictions: pd.DataFrame

    def summary_row(self) -> dict[str, float | int | str]:
        return {"estimator": self.name} | self.pooled.as_dict()


def evaluate_estimator(
    name: str,
    estimator: Estimator,
    frame: pd.DataFrame,
    *,
    test_start: str | pd.Timestamp,
    test_end: str | pd.Timestamp,
    horizon: int = 7,
    step: int = 7,
    date_col: str = "date",
    target_col: str = "demand",
) -> EvaluationResult:
    """Run one estimator across every origin in the test window and score it.

    `frame` is the *whole* series history, not just the test window: each origin needs the history
    preceding it, and `iter_origin_slices` is what cuts it at the right place. Handing the estimator
    a pre-trimmed frame would be the leak this design exists to prevent.
    """
    origins = generate_origins(
        test_start=pd.Timestamp(test_start),
        test_end=pd.Timestamp(test_end),
        horizon=horizon,
        step=step,
    )

    collected: list[pd.DataFrame] = []

    for origin, history, target in iter_origin_slices(frame, origins, date_col=date_col):
        if history.empty or target.empty:
            continue

        target_dates = pd.date_range(origin.first_target_date, origin.last_target_date, freq="D")
        predicted = estimator(history, target_dates)
        predicted = clip_to_non_negative(predicted, "y_pred")

        # Inner join on (series, date): only score dates the data actually contains. An outer join
        # would invent rows for series that do not exist on those dates and score them against NaN.
        merged = target[[*SERIES_KEYS, date_col, target_col]].merge(
            predicted[[*SERIES_KEYS, date_col, "y_pred"]],
            on=[*SERIES_KEYS, date_col],
            how="inner",
        )
        merged = merged.rename(columns={target_col: "y_true"})
        merged["origin"] = origin.index
        # Horizon of each prediction (1..h). Kept so the report can show error growing with
        # horizon -- the visible cost of recursive forecasting (see models/predict.py).
        merged["horizon"] = (
            pd.to_datetime(merged[date_col]) - origin.first_target_date
        ).dt.days + 1
        collected.append(merged)

    if not collected:
        raise ValueError(f"estimator {name!r} produced no scoreable predictions")

    predictions = pd.concat(collected, ignore_index=True)

    return EvaluationResult(
        name=name,
        pooled=compute_metrics(predictions["y_true"], predictions["y_pred"]),
        per_origin=metrics_by_group(predictions, group_cols=["origin"]),
        per_series=metrics_by_group(predictions, group_cols=SERIES_KEYS),
        predictions=predictions,
    )


def metrics_by_horizon(result: EvaluationResult) -> pd.DataFrame:
    """Error as a function of days-ahead.

    For the baselines this is roughly flat (they do not compound). For the recursive model it should
    slope upward, and showing that curve is more honest than a single pooled number -- it tells the
    bakery that Monday's forecast is worth more than next Sunday's.
    """
    return metrics_by_group(result.predictions, group_cols=["horizon"]).sort_values(
        "horizon", ignore_index=True
    )


def comparison_table(
    results: list[EvaluationResult], *, reference: str | None = None
) -> pd.DataFrame:
    """Side-by-side pooled metrics, with improvement over the reference baseline.

    `reference` is the better baseline chosen on *validation* (plan 08 §3 step 1). Improvement is
    reported for every estimator including the reference itself (0.0), so the table reads the same
    whether or not the model wins -- there is no formatting path that only exists for success.
    """
    table = pd.DataFrame([r.summary_row() for r in results])

    if reference is not None:
        if reference not in set(table["estimator"]):
            raise ValueError(f"reference {reference!r} not among evaluated estimators")
        base = table.loc[table["estimator"] == reference].iloc[0]
        for metric in ("mae", "rmse", "wape"):
            table[f"{metric}_improvement"] = (base[metric] - table[metric]) / base[metric]

    return table


def verdict(
    table: pd.DataFrame,
    *,
    reference: str,
    candidate: str,
    threshold: float,
) -> tuple[bool, str]:
    """Did the candidate clear the pre-registered bar? Returns (passed, human-readable sentence).

    The bar is "beat the reference baseline by ≥ threshold on **both** pooled MAE and pooled WAPE"
    (plan 08 §3 step 2). Requiring both is deliberate: a model can buy MAE by shading every
    prediction toward the median while getting the volume-weighted picture worse, and either metric
    alone can be gamed in a way the pair cannot.

    Returns the failure sentence just as readily as the success one -- plan 08 §3 step 3 commits us
    to reporting an unbeaten baseline plainly and keeping the baseline as the serving default.
    """
    row = table.loc[table["estimator"] == candidate]
    if row.empty:
        raise ValueError(f"candidate {candidate!r} not in table")
    row = row.iloc[0]

    mae_gain = float(row["mae_improvement"])
    wape_gain = float(row["wape_improvement"])
    passed = mae_gain >= threshold and wape_gain >= threshold

    detail = (
        f"{candidate} vs {reference}: "
        f"MAE {mae_gain:+.1%}, WAPE {wape_gain:+.1%} "
        f"(threshold ≥ {threshold:.0%} on both)"
    )
    return passed, ("PASS — " if passed else "FAIL — ") + detail
