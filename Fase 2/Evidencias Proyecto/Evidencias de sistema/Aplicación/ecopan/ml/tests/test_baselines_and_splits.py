"""Baselines and the rolling-origin splitter.

These two decide what "beating the baseline" even means, so their edge cases are worth pinning:
an origin generator that quietly scores a short final window, or a baseline that peeks one day
ahead, would both manufacture a result.
"""

from itertools import pairwise

import pandas as pd
import pytest

from ecopan_ml.baselines.naive import (
    clip_to_non_negative,
    cold_start_forecast,
    moving_average,
    same_weekday_last_week,
)
from ecopan_ml.eval.rolling_origin import (
    generate_origins,
    iter_origin_slices,
    temporal_split,
)


def history(days: int = 30, start: str = "2024-01-01") -> pd.DataFrame:
    dates = pd.date_range(start, periods=days, freq="D")
    return pd.DataFrame(
        [
            {"store_nbr": "1", "family": "BREAD", "date": day, "demand": float(index)}
            for index, day in enumerate(dates)
        ]
    )


def test_same_weekday_reaches_exactly_seven_days_back():
    frame = history(30)
    targets = pd.date_range("2024-01-31", periods=3, freq="D")

    result = same_weekday_last_week(frame, targets).sort_values("date").reset_index(drop=True)

    # demand == index, so 2024-01-24 (index 23) is the source for 2024-01-31.
    assert result.loc[0, "y_pred"] == pytest.approx(23.0)
    assert result.loc[1, "y_pred"] == pytest.approx(24.0)


def test_same_weekday_falls_back_to_series_mean_when_source_missing():
    """A gap must not produce NaN: NaN rows drop out of the metric and change its denominator."""
    frame = history(3)
    targets = pd.date_range("2024-01-20", periods=1, freq="D")

    result = same_weekday_last_week(frame, targets)
    assert result["y_pred"].notna().all()
    assert result["y_pred"].iloc[0] == pytest.approx(1.0)  # mean of 0,1,2


def test_moving_average_is_flat_across_the_horizon():
    """Held flat rather than rolled over its own output, which would drift toward a constant."""
    frame = history(30)
    targets = pd.date_range("2024-01-31", periods=7, freq="D")

    result = moving_average(frame, targets)
    assert result["y_pred"].nunique() == 1
    assert result["y_pred"].iloc[0] == pytest.approx(sum(range(23, 30)) / 7)


def test_cold_start_ladder_picks_the_right_rung():
    long_enough = cold_start_forecast(history(10), pd.date_range("2024-01-11", periods=2))
    assert set(long_enough["model_version"]) == {"baseline-swlw"}

    too_short = cold_start_forecast(history(3), pd.date_range("2024-01-11", periods=2))
    assert set(too_short["model_version"]) == {"baseline-trailing-mean"}


def test_clip_applies_to_every_estimator_equally():
    frame = pd.DataFrame({"y_pred": [-5.0, 0.0, 5.0]})
    assert list(clip_to_non_negative(frame)["y_pred"]) == [0.0, 0.0, 5.0]


def test_origins_tile_the_window_without_overlap():
    origins = generate_origins(
        test_start=pd.Timestamp("2024-03-01"),
        test_end=pd.Timestamp("2024-03-28"),
        horizon=7,
        step=7,
    )
    assert len(origins) == 4
    assert origins[0].first_target_date == pd.Timestamp("2024-03-01")
    assert origins[-1].last_target_date == pd.Timestamp("2024-03-28")
    # Non-overlapping: each origin starts the day after the previous one ends.
    for earlier, later in pairwise(origins):
        assert later.first_target_date == earlier.last_target_date + pd.Timedelta(days=1)


def test_trailing_partial_window_is_dropped_not_scored_short():
    """Scoring a 3-day tail as if it were a 7-day horizon would mix horizons into the pooled mean
    and flatter the last origin, since near-horizon days are easier."""
    origins = generate_origins(
        test_start=pd.Timestamp("2024-03-01"),
        test_end=pd.Timestamp("2024-03-24"),  # 24 days = 3 full weeks + 3 days
        horizon=7,
        step=7,
    )
    assert len(origins) == 3
    assert origins[-1].last_target_date == pd.Timestamp("2024-03-21")


def test_window_shorter_than_horizon_is_an_error():
    with pytest.raises(ValueError, match="shorter than horizon"):
        generate_origins(
            test_start=pd.Timestamp("2024-03-01"),
            test_end=pd.Timestamp("2024-03-03"),
            horizon=7,
        )


def test_history_slice_never_includes_the_target_dates():
    """The single line that enforces no-lookahead for every estimator in the project."""
    frame = history(60)
    origins = generate_origins(
        test_start=pd.Timestamp("2024-02-05"),
        test_end=pd.Timestamp("2024-02-18"),
        horizon=7,
        step=7,
    )

    for origin, past, target in iter_origin_slices(frame, origins):
        assert past["date"].max() < origin.first_target_date
        assert target["date"].min() >= origin.first_target_date
        assert target["date"].max() <= origin.last_target_date


def test_temporal_split_is_ordered_and_disjoint():
    frame = history(120)
    train, validation, test = temporal_split(
        frame, train_end="2024-03-01", val_end="2024-03-20", test_end="2024-04-29"
    )

    assert train["date"].max() < validation["date"].min()
    assert validation["date"].max() < test["date"].min()
    assert len(train) + len(validation) + len(test) == len(frame)


def test_out_of_order_split_boundaries_are_rejected():
    with pytest.raises(ValueError, match="must increase"):
        temporal_split(
            history(120), train_end="2024-03-20", val_end="2024-03-01", test_end="2024-04-29"
        )
