"""Multi-step forecasting with a single one-step model: recursive prediction.

--- The problem -------------------------------------------------------------------------------

The model is trained to answer one question: "given history up to yesterday, what is today?" But
the bakery needs seven days. There are three standard ways to bridge that gap.

1.  **Direct multi-horizon** -- train seven separate models, one per horizon (h=1 … h=7), each
    predicting further ahead from the same origin. No error compounding, and each model can
    specialise. Costs seven times the training and seven artifacts to keep in sync, and each model
    sees less usable data (longer horizons need longer warm-up).

2.  **Multi-output** -- one model emitting a 7-vector. Efficient, but LightGBM is single-output;
    this would mean changing model class, which plan 08 §4 rules out ("one model class, no
    detours").

3.  **Recursive** -- predict day 1, append the prediction to history *as if observed*, rebuild
    features, predict day 2, and so on. One model, one artifact, and the same feature code as
    training.

We use recursive, which plan 08 §4 fixes, and it is worth being explicit about the price.

--- The price: compounding error ---------------------------------------------------------------

At step 2 the lag_1 feature is not an observation, it is our own step-1 prediction. Any error there
propagates into every subsequent step and can amplify: the model was trained on *true* lag values,
so feeding it predicted ones is a mild distribution shift that grows with the horizon.

The practical consequences, all of which the evaluation is set up to expose rather than hide:

  * Accuracy degrades with horizon. Day 7 is meaningfully worse than day 1. Rolling-origin scoring
    at a fixed horizon of 7 means every reported number already *includes* this degradation --
    we are not quoting day-1 accuracy and implying it holds all week.
  * Recursive forecasts tend to over-smooth at longer horizons: as predictions feed back in, the
    series drifts toward its conditional mean and loses the weekly shape.
  * Errors are correlated across the horizon, so a bad step 1 makes a bad week.

The mitigation baked into the feature set is that long lags (7, 14, 28) stay *observed* throughout
a 7-day horizon -- at step 7 the lag_7 value is still a real measurement from the origin, not a
prediction. Only lag_1 (and the short rolling windows) become synthetic. That is a deliberate
argument for why horizon 7 with weekly lags is the sweet spot for this problem, and why extending
to 14 days would need the direct approach instead.
"""

import warnings

import numpy as np
import pandas as pd

from ecopan_ml.features.build import (
    DATE,
    SERIES_KEYS,
    TARGET,
    CategoryEncoding,
    FeatureConfig,
    build_features,
    ensure_dense_daily,
    feature_matrix,
)

CALENDAR_FLAGS = ("is_holiday", "is_day_before_holiday")


def _calendar_lookup(
    future_calendar: pd.DataFrame | None,
) -> dict[pd.Timestamp, dict[str, int]]:
    """Index a future calendar by date, so each forecast step is a dict hit rather than a merge."""
    if future_calendar is None or future_calendar.empty:
        return {}

    required = (DATE, *CALENDAR_FLAGS)
    missing = [c for c in required if c not in future_calendar.columns]
    if missing:
        raise ValueError(f"future_calendar is missing required columns: {missing}")

    lookup: dict[pd.Timestamp, dict[str, int]] = {}
    for row in future_calendar.itertuples(index=False):
        day = pd.Timestamp(getattr(row, DATE)).normalize()
        lookup[day] = {flag: int(getattr(row, flag)) for flag in CALENDAR_FLAGS}
    return lookup


def recursive_forecast(
    booster,
    history: pd.DataFrame,
    target_dates: pd.DatetimeIndex,
    config: FeatureConfig,
    encoding: CategoryEncoding,
    *,
    model_version: str = "lgbm",
    non_negative: bool = True,
    future_calendar: pd.DataFrame | None = None,
) -> pd.DataFrame:
    """Forecast `target_dates` for every series in `history`, one day at a time.

    All series advance together, one date per iteration, so each step is a single batched
    `booster.predict` over all series rather than a Python loop per series. With ~1800 series and a
    7-day horizon that is 7 predict calls instead of 12,600 -- the difference between a service that
    responds in milliseconds and one that times out.

    `history` must carry the series keys, `date` and `demand`. Anything else (holiday flags,
    promo_share) is carried through when present.

    `future_calendar` supplies `is_holiday`/`is_day_before_holiday` for the *target* dates, keyed by
    date and shared by every series. Pass it whenever the calendar is known -- which is always, for
    a calendar. Omitting it zero-fills those flags, which silently tells the model that no future
    day is a holiday: the model is trained on true flags, so scoring or serving without them is a
    training/serving mismatch that quietly suppresses exactly the days demand moves most. That was
    the behaviour before this parameter existed, so the omission path warns rather than being a
    silent default.

    Promo is deliberately *not* part of this. `promo_share` stays zero-filled because a promotion
    calendar genuinely is not knowable across the horizon at serving time (`use_promo: false` in the
    config says the same thing) -- supplying it in training but not in production is the mismatch
    this parameter exists to remove, and inventing future promos would reintroduce it.
    """
    if history.empty:
        raise ValueError("cannot forecast from empty history")

    target_index = pd.DatetimeIndex(target_dates)
    if target_index.empty:
        raise ValueError("cannot forecast an empty set of target dates")

    # History must end before the first date being predicted. The evaluation harness guarantees
    # this (`iter_origin_slices` cuts on `date < first_target_date`), but the service does not: a
    # caller sending 90 days ending after `start_day` got a *second* row per overlapping date --
    # the real observation and the scaffold row -- and therefore two predictions for the same day,
    # silently, with the horizon apparently doubled. Enforce the precondition here so every caller
    # gets the harness's guarantee rather than only the callers who happen to know about it.
    first_target = target_index.min()
    overlap = pd.to_datetime(history[DATE]) >= first_target
    if bool(overlap.any()):
        warnings.warn(
            f"recursive_forecast: dropping {int(overlap.sum())} history row(s) dated on or after "
            f"the first target date ({first_target.date()}). History must precede the forecast "
            "window; forecasting a day that is already observed is not a forecast.",
            stacklevel=2,
        )
        history = history.loc[~overlap]
        if history.empty:
            raise ValueError(
                "no history remains before the first target date -- every row was on or after it"
            )

    working = ensure_dense_daily(history)

    calendar = _calendar_lookup(future_calendar)
    if not calendar and any(flag in working.columns for flag in CALENDAR_FLAGS):
        warnings.warn(
            "recursive_forecast: no future_calendar supplied, so is_holiday/"
            "is_day_before_holiday are zero-filled for every forecast date. The model was trained "
            "with real flags, so predictions on and around holidays will be biased.",
            stacklevel=2,
        )

    predictions: list[pd.DataFrame] = []

    for target_date in target_index:
        # One synthetic row per series for the target date, with demand deliberately NaN: it is the
        # unknown we are about to fill. Appending it before feature building is what lets the *same*
        # `build_features` compute this row's lags from the rows beneath it -- no parallel
        # "serving feature" code path, which is the whole point of features/build.py.
        scaffold = (
            working[SERIES_KEYS].drop_duplicates().assign(**{DATE: target_date, TARGET: np.nan})
        )
        day_flags = calendar.get(pd.Timestamp(target_date).normalize(), {})
        for flag in CALENDAR_FLAGS:
            if flag in working.columns:
                scaffold[flag] = day_flags.get(flag, 0)
        if "promo_share" in working.columns:
            scaffold["promo_share"] = 0.0

        extended = pd.concat([working, scaffold], ignore_index=True)

        # dropna=False: the target row's own lags may be NaN for a short series, and LightGBM
        # handles NaN natively. Dropping it would silently return no forecast for that series.
        featured = build_features(extended, config, dropna=False)
        target_rows = featured.loc[featured[DATE] == target_date].copy()

        matrix, _ = feature_matrix(target_rows, config, encoding)
        predicted = booster.predict(matrix, num_iteration=getattr(booster, "best_iteration", None))
        predicted = np.asarray(predicted, dtype=float)
        if non_negative:
            predicted = np.maximum(predicted, 0.0)

        target_rows["y_pred"] = predicted
        predictions.append(
            target_rows[[*SERIES_KEYS, DATE, "y_pred"]].assign(model_version=model_version)
        )

        # Feed the prediction back in as if it had been observed -- this is the "recursive" step,
        # and the exact point where error begins to compound.
        realised = target_rows[[*SERIES_KEYS, DATE]].copy()
        realised[TARGET] = predicted
        # The same flags the scaffold used: this row becomes history for the next step, and a
        # holiday that was real when predicted must stay real when it is looked back on.
        for flag in CALENDAR_FLAGS:
            if flag in working.columns:
                realised[flag] = day_flags.get(flag, 0)
        if "promo_share" in working.columns:
            realised["promo_share"] = 0.0
        working = pd.concat([working, realised], ignore_index=True)

    return pd.concat(predictions, ignore_index=True)
