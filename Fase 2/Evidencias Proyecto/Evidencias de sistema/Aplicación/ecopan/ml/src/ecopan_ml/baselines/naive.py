"""Naive baselines. Written and scored *before* any model code exists (plan 08 §3, §9 item 4).

--- Why the baseline is the most important code in this project --------------------------------

A forecast model with no baseline is unfalsifiable. "MAE of 14 loaves" means nothing on its own:
it could be excellent or it could be worse than a sticky note reading "same as last Tuesday".
The baseline converts an uninterpretable number into a claim that can fail.

Two baselines, and they are chosen to be *strong*, not to be beaten easily:

1.  **Same-weekday-last-week**  ŷ(d) = y(d − 7)

    Deceptively hard to beat on retail data. Daily demand is dominated by day-of-week seasonality
    -- Saturday is like other Saturdays, not like Wednesday -- and this estimator captures that
    perfectly at zero cost, while also tracking the recent level (it uses data from a week ago, not
    a seasonal average from months back). It is the reason many production "ML" forecasting systems
    quietly underperform their predecessors.

    Its weakness is variance: it inherits the noise of exactly one day. A freak Tuesday becomes
    next Tuesday's forecast.

2.  **7-day moving average**  ŷ(d) = mean(y(d−7 … d−1))

    The complementary trade. Averaging a whole week kills the noise (variance down by ~√7) but also
    destroys the day-of-week signal: it predicts the same value for Sunday and Wednesday. Strong on
    flat, noisy series; weak wherever the weekly shape is pronounced.

Together they bracket the bias-variance trade-off, so whichever wins tells us something real about
the data before a single tree is grown. Beating the *better* of the two is the bar (plan 08 §3),
not beating the average of them, and not beating whichever one happens to be worse.

--- The horizon subtlety that makes a baseline honest ------------------------------------------

Both estimators are defined recursively in terms of "recent" history, which is unambiguous at
horizon 1 and ambiguous at horizon 7. The trap: when forecasting day d+3, the value y(d+2) does not
exist yet. An implementation that reaches for it produces a beautiful baseline that cannot be
deployed -- and, worse, an unbeatable one, because it is peeking.

Both baselines here therefore forecast the entire horizon from the origin's history *only*:

  * same-weekday-last-week reaches back exactly 7 days from each target date, which for horizons
    ≤ 7 always lands on or before the origin. This is what makes h=7 the natural horizon for a
    weekly-seasonal series: at h=8 the estimator would need a day it has not seen.
  * the moving average computes one mean over the last 7 observed days and holds it flat across
    the horizon, rather than feeding its own predictions back in.

The LightGBM predictor is held to exactly the same restriction (see `models/predict.py`, which
feeds its own predictions back in precisely *because* it has no observed values to use). Nobody
gets to see the future here.
"""

import numpy as np
import pandas as pd

# The series key used throughout: one time series per (store, family) == per (branch, product_type).
SERIES_KEYS = ["store_nbr", "family"]

BASELINE_SAME_WEEKDAY = "baseline-swlw"
BASELINE_MOVING_AVERAGE = "baseline-ma7"


def _validate_history(history: pd.DataFrame, date_col: str, target_col: str) -> pd.DataFrame:
    missing = {date_col, target_col, *SERIES_KEYS} - set(history.columns)
    if missing:
        raise ValueError(f"history is missing required columns: {sorted(missing)}")
    out = history.copy()
    out[date_col] = pd.to_datetime(out[date_col])
    return out


def same_weekday_last_week(
    history: pd.DataFrame,
    target_dates: pd.DatetimeIndex,
    *,
    date_col: str = "date",
    target_col: str = "demand",
) -> pd.DataFrame:
    """ŷ(d) = y(d − 7), per series.

    Falls back to the series mean when d−7 is absent from history (a gap, or a series shorter than
    a week). The fallback is a real decision, not an implementation detail: returning NaN would
    quietly drop those rows from the metric denominators and flatter *every* estimator scored on
    the same rows, while 0.0 would inject a large artificial error. The series mean keeps the row
    scoreable and is the weakest defensible guess.
    """
    history = _validate_history(history, date_col, target_col)

    lookup = history.set_index([*SERIES_KEYS, date_col])[target_col]
    series_mean = history.groupby(SERIES_KEYS, observed=True)[target_col].mean()

    records = []
    for keys, _ in history.groupby(SERIES_KEYS, observed=True):
        key_tuple = keys if isinstance(keys, tuple) else (keys,)
        fallback = float(series_mean.loc[keys])

        for target_date in target_dates:
            source_date = target_date - pd.Timedelta(days=7)
            try:
                value = float(lookup.loc[(*key_tuple, source_date)])
            except KeyError:
                value = fallback
            records.append(
                dict(zip(SERIES_KEYS, key_tuple, strict=True))
                | {date_col: target_date, "y_pred": value, "model_version": BASELINE_SAME_WEEKDAY}
            )

    return pd.DataFrame.from_records(records)


def moving_average(
    history: pd.DataFrame,
    target_dates: pd.DatetimeIndex,
    *,
    window: int = 7,
    date_col: str = "date",
    target_col: str = "demand",
) -> pd.DataFrame:
    """ŷ(d) = mean of the last `window` observed days, held flat across the horizon.

    Held flat rather than rolled forward over its own predictions. Rolling a mean over its own
    output is a low-pass filter applied twice: it would drift toward a constant and understate the
    estimator, making the model's win look better than it is. Flat is both simpler and the stronger
    version of this baseline.
    """
    history = _validate_history(history, date_col, target_col)

    records = []
    for keys, group in history.groupby(SERIES_KEYS, observed=True):
        key_tuple = keys if isinstance(keys, tuple) else (keys,)
        recent = group.sort_values(date_col)[target_col].tail(window)
        value = float(recent.mean()) if len(recent) else 0.0

        records.extend(
            dict(zip(SERIES_KEYS, key_tuple, strict=True))
            | {date_col: target_date, "y_pred": value, "model_version": BASELINE_MOVING_AVERAGE}
            for target_date in target_dates
        )

    return pd.DataFrame.from_records(records)


def cold_start_forecast(
    history: pd.DataFrame,
    target_dates: pd.DatetimeIndex,
    *,
    default_value: float = 0.0,
    date_col: str = "date",
    target_col: str = "demand",
) -> pd.DataFrame:
    """The deployed cold-start ladder (plan 08 §5), degrading gracefully with available history.

        ≥ 7 days   -> same-weekday-last-week (the weekly shape is the strongest available signal)
        1..6 days  -> trailing mean          (too short for weekly shape; level is all we have)
        0 days     -> `default_value`        (tenant-configured; nothing else exists)

    This is the honest answer to "your reference bakery has no sales history". The system does not
    pretend to forecast on day one -- it serves a labelled baseline and says so, and the LightGBM
    model takes over per series once that series has accumulated enough history to populate its
    longest lag (see `MIN_HISTORY_FOR_MODEL` in the service).

    That story is also the product argument: this bakery never recorded per-type daily demand at
    all, so the first contribution is *starting the record*. The model is what that record later
    makes possible, not a precondition for shipping.
    """
    history = _validate_history(history, date_col, target_col)

    if history.empty:
        raise ValueError("cold_start_forecast needs at least the series keys; history is empty")

    records = []
    for keys, group in history.groupby(SERIES_KEYS, observed=True):
        key_tuple = keys if isinstance(keys, tuple) else (keys,)
        observed_days = group[date_col].nunique()

        if observed_days >= 7:
            single = same_weekday_last_week(
                group, target_dates, date_col=date_col, target_col=target_col
            )
            records.extend(single.to_dict("records"))
            continue

        value = float(group[target_col].mean()) if observed_days > 0 else float(default_value)
        label = "baseline-trailing-mean" if observed_days > 0 else "baseline-default"
        records.extend(
            dict(zip(SERIES_KEYS, key_tuple, strict=True))
            | {date_col: target_date, "y_pred": value, "model_version": label}
            for target_date in target_dates
        )

    return pd.DataFrame.from_records(records)


def clip_to_non_negative(frame: pd.DataFrame, column: str = "y_pred") -> pd.DataFrame:
    """Demand cannot be negative; clip at zero.

    Applied uniformly to baselines *and* to the model, because it is a property of the quantity
    being predicted, not a trick. Applying it to only one side would be exactly the kind of
    asymmetry that invalidates a comparison.
    """
    out = frame.copy()
    out[column] = np.maximum(out[column].to_numpy(dtype=float), 0.0)
    return out
