"""Forecast error metrics.

Hand-written rather than imported from scikit-learn, deliberately: these three numbers are the
entire quantitative claim of this project, so they are implemented here and unit-tested against
hand-computed fixtures (and cross-checked against sklearn in the test suite). A metric you cannot
recompute on paper is a metric you cannot defend.

--- Why three metrics, and what each one hides -------------------------------------------------

MAE  = mean(|y - ŷ|)
    Average error in the unit of the data. Interpretable ("we are off by 12 loaves a day") and,
    crucially, the loss LightGBM optimises when `objective="regression_l1"`. Minimising absolute
    error drives predictions toward the conditional *median*, not the mean -- which is what you
    want for demand that is right-skewed (occasional huge days) and where over- and under-stocking
    both cost money roughly linearly.

RMSE = sqrt(mean((y - ŷ)²))
    Squares the errors first, so one catastrophic day counts far more than several mediocre ones.
    Minimising squared error targets the conditional *mean*. Reported alongside MAE because the
    gap between them is itself informative: RMSE >> MAE means the error is concentrated in a few
    bad days (spikes, holidays) rather than spread evenly. A model can improve MAE while worsening
    RMSE by giving up on the spikes entirely -- reporting only one metric hides that trade.

WAPE = Σ|y - ŷ| / Σy
    Weighted absolute percentage error. This is the one that makes *pooling across series* honest.

    The naive alternative, MAPE = mean(|y-ŷ|/y), is unusable here for two reasons. First, it is
    undefined when y = 0, and zero-demand days are common in retail (closed days, dead SKUs) --
    every implementation has to fudge them, and the fudge drives the number. Second, it weights
    every *day* equally regardless of volume, so a series selling 2 units/day dominates one selling
    2000, and the aggregate stops describing the business.

    WAPE instead divides total absolute error by total actual volume. It is scale-free (a
    percentage, comparable across families of wildly different volume), well-defined as long as the
    denominator is non-zero over the aggregation window, and volume-weighted -- errors on the
    bread family, which is most of the revenue, count for most of the score. That is the right
    weighting for a bakery deciding how much to bake.

    Note WAPE is *not* symmetric in the way sMAPE tries to be, and it can be gamed by
    under-forecasting when Σy is large. We accept this: it is reported next to MAE and RMSE, and
    the baseline comparison is what carries the argument, not any single number.

--- Pooled vs per-series -----------------------------------------------------------------------

Every function takes flat arrays and is agnostic about grouping. `pooled` metrics concatenate all
series and all origins into one array; `per_series` metrics compute the metric within each series
and then report the distribution. They answer different questions:

    pooled      "how wrong is the system, in aggregate, weighted by how much it matters?"
    per-series  "is the system uniformly decent, or great on three big series and useless
                 elsewhere?"

RESULTS.md reports both, because a pooled win driven entirely by the largest series is not the
same claim as a broad win, and a committee is entitled to know which one it is being shown.
"""

from dataclasses import asdict, dataclass

import numpy as np
import pandas as pd


@dataclass(frozen=True)
class ErrorMetrics:
    """One evaluation's headline numbers, plus the sample size that produced them.

    `n` is carried alongside because a metric without its support is not reviewable -- a WAPE of
    0.18 over 40 predictions and over 40,000 are very different claims.
    """

    mae: float
    rmse: float
    wape: float
    n: int

    def as_dict(self) -> dict[str, float | int]:
        return asdict(self)


def _as_float_arrays(y_true, y_pred) -> tuple[np.ndarray, np.ndarray]:
    """Coerce inputs to aligned 1-D float arrays, refusing anything ambiguous.

    Silent misalignment (a shifted index, a length mismatch broadcast into nonsense) is the classic
    way an evaluation reports an impressive number for the wrong comparison, so length equality is
    asserted rather than assumed. NaNs are rejected outright for the same reason: quietly dropping
    them changes the denominator, and therefore the metric, without saying so.
    """
    y_true = np.asarray(y_true, dtype=float).ravel()
    y_pred = np.asarray(y_pred, dtype=float).ravel()

    if y_true.shape != y_pred.shape:
        raise ValueError(f"length mismatch: y_true={y_true.shape} y_pred={y_pred.shape}")
    if y_true.size == 0:
        raise ValueError("cannot compute metrics over an empty array")
    if np.isnan(y_true).any() or np.isnan(y_pred).any():
        raise ValueError("NaN in y_true/y_pred -- handle missing values before scoring, not during")

    return y_true, y_pred


def mae(y_true, y_pred) -> float:
    y_true, y_pred = _as_float_arrays(y_true, y_pred)
    return float(np.mean(np.abs(y_true - y_pred)))


def rmse(y_true, y_pred) -> float:
    y_true, y_pred = _as_float_arrays(y_true, y_pred)
    return float(np.sqrt(np.mean((y_true - y_pred) ** 2)))


def wape(y_true, y_pred) -> float:
    """Σ|y-ŷ| / Σy.

    The zero-denominator case is real: a window in which a series sold nothing at all. There is no
    meaningful percentage error against zero volume, so we return NaN rather than 0.0 or infinity.
    NaN propagates visibly into any aggregate and forces the caller to decide, where 0.0 would
    silently flatter the model and inf would silently destroy an otherwise valid pooled score.
    """
    y_true, y_pred = _as_float_arrays(y_true, y_pred)
    denominator = np.sum(np.abs(y_true))
    if denominator == 0:
        return float("nan")
    return float(np.sum(np.abs(y_true - y_pred)) / denominator)


def compute_metrics(y_true, y_pred) -> ErrorMetrics:
    """All three headline metrics over one flat set of (actual, predicted) pairs."""
    y_true, y_pred = _as_float_arrays(y_true, y_pred)
    return ErrorMetrics(
        mae=mae(y_true, y_pred),
        rmse=rmse(y_true, y_pred),
        wape=wape(y_true, y_pred),
        n=int(y_true.size),
    )


def metrics_by_group(
    frame: pd.DataFrame,
    *,
    group_cols: list[str],
    actual_col: str = "y_true",
    predicted_col: str = "y_pred",
) -> pd.DataFrame:
    """Per-group metrics, one row per group, sorted by descending volume.

    Sorted by volume rather than by score on purpose: it puts the series that actually drive the
    pooled number at the top of the table, so a reader can see immediately whether a good pooled
    result is broad or is carried by one big family.
    """
    records = []
    for keys, group in frame.groupby(group_cols, observed=True):
        key_tuple = keys if isinstance(keys, tuple) else (keys,)
        result = compute_metrics(group[actual_col], group[predicted_col])
        records.append(
            dict(zip(group_cols, key_tuple, strict=True))
            | result.as_dict()
            | {"volume": float(group[actual_col].sum())}
        )

    return pd.DataFrame.from_records(records).sort_values(
        "volume", ascending=False, ignore_index=True
    )


def relative_improvement(baseline: float, candidate: float) -> float:
    """Fractional improvement of `candidate` over `baseline`, for error metrics (lower is better).

    Returns e.g. 0.12 for "12% better than baseline". Negative means the candidate is *worse* --
    which the report prints as-is rather than clipping, because plan 08 §3 commits us to reporting
    a model that loses to its baseline honestly.
    """
    if baseline == 0:
        return float("nan")
    return (baseline - candidate) / baseline
