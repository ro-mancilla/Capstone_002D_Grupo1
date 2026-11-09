"""The forecast horizon must know about holidays.

Regression tests for a real defect: `recursive_forecast` used to hard-code `is_holiday` and
`is_day_before_holiday` to 0 for every forecast date, and `pipeline.cmd_evaluate` used that same
function to score the test window. The model is *trained* on true flags, so it was being evaluated
and served with two of its features switched off -- a training/serving mismatch that silently
suppressed exactly the days demand moves most.

These assert plumbing, never accuracy: that the flags reach the feature matrix, and that omitting
them is loud rather than silent. Per plan 08 §8, no test in this repo may assert a metric floor.
"""

import numpy as np
import pandas as pd
import pytest

from ecopan_ml.features.build import CategoryEncoding, FeatureConfig, build_features
from ecopan_ml.models.predict import recursive_forecast


class _SpyBooster:
    """Stands in for a LightGBM booster and records the feature matrix it was handed."""

    def __init__(self, feature_names: list[str]) -> None:
        self.feature_names = feature_names
        self.seen: list[pd.DataFrame] = []

    def predict(self, matrix, num_iteration=None):
        frame = pd.DataFrame(np.asarray(matrix), columns=self.feature_names)
        self.seen.append(frame)
        return np.full(len(frame), 10.0)


def _history(days: int = 60) -> pd.DataFrame:
    dates = pd.date_range("2017-01-01", periods=days, freq="D")
    return pd.DataFrame(
        {
            "store_nbr": ["1"] * days,
            "family": ["BREAD/BAKERY"] * days,
            "date": dates,
            "demand": np.linspace(100.0, 200.0, days),
            "is_holiday": 0,
            "is_day_before_holiday": 0,
        }
    )


def _fitted(history: pd.DataFrame, config: FeatureConfig) -> tuple[CategoryEncoding, list[str]]:
    featured = build_features(history, config, dropna=False)
    encoding = CategoryEncoding().fit(featured, ["store_nbr", "family"])
    return encoding, config.feature_names()


def test_future_holiday_reaches_the_feature_matrix() -> None:
    config = FeatureConfig()
    history = _history()
    encoding, names = _fitted(history, config)
    booster = _SpyBooster(names)

    targets = pd.date_range("2017-03-02", periods=3, freq="D")
    calendar = pd.DataFrame(
        {
            "date": targets,
            # Only the middle day is a holiday, so a blanket fill in either direction fails.
            "is_holiday": [0, 1, 0],
            "is_day_before_holiday": [1, 0, 0],
        }
    )

    recursive_forecast(booster, history, targets, config, encoding, future_calendar=calendar)

    assert [float(step["is_holiday"].iloc[0]) for step in booster.seen] == [0.0, 1.0, 0.0]
    before = [float(step["is_day_before_holiday"].iloc[0]) for step in booster.seen]
    assert before == [1.0, 0.0, 0.0]


def test_omitting_the_calendar_warns_instead_of_silently_zeroing() -> None:
    config = FeatureConfig()
    history = _history()
    encoding, names = _fitted(history, config)
    booster = _SpyBooster(names)

    with pytest.warns(UserWarning, match="no future_calendar supplied"):
        recursive_forecast(
            booster, history, pd.date_range("2017-03-02", periods=2, freq="D"), config, encoding
        )

    # Still zero-filled -- the point is that it is now audible, not that it changed.
    assert all(float(step["is_holiday"].iloc[0]) == 0.0 for step in booster.seen)


def test_a_predicted_holiday_stays_a_holiday_when_looked_back_on() -> None:
    """The fed-back row becomes history for the next step, so its flags must persist."""
    config = FeatureConfig()
    history = _history()
    encoding, names = _fitted(history, config)
    booster = _SpyBooster(names)

    targets = pd.date_range("2017-03-02", periods=2, freq="D")
    calendar = pd.DataFrame(
        {"date": targets, "is_holiday": [1, 0], "is_day_before_holiday": [0, 0]}
    )

    result = recursive_forecast(
        booster, history, targets, config, encoding, future_calendar=calendar
    )

    assert len(result) == 2
    assert list(result["date"]) == list(targets)


def test_promo_is_not_carried_forward_even_when_a_calendar_is_supplied() -> None:
    """Promo is genuinely unknowable across the horizon; the fix must not quietly change that."""
    config = FeatureConfig(use_promo=True)
    history = _history().assign(promo_share=0.5)
    encoding, names = _fitted(history, config)
    booster = _SpyBooster(names)

    targets = pd.date_range("2017-03-02", periods=2, freq="D")
    calendar = pd.DataFrame(
        {"date": targets, "is_holiday": [1, 1], "is_day_before_holiday": [0, 0]}
    )

    recursive_forecast(booster, history, targets, config, encoding, future_calendar=calendar)

    assert all(float(step["promo_share"].iloc[0]) == 0.0 for step in booster.seen)


def test_calendar_missing_required_columns_is_rejected() -> None:
    config = FeatureConfig()
    history = _history()
    encoding, _ = _fitted(history, config)

    bad = pd.DataFrame({"date": pd.date_range("2017-03-02", periods=1, freq="D")})
    with pytest.raises(ValueError, match="missing required columns"):
        recursive_forecast(
            object(),
            history,
            pd.date_range("2017-03-02", periods=1, freq="D"),
            config,
            encoding,
            future_calendar=bad,
        )


def test_history_overlapping_the_forecast_window_is_trimmed_not_duplicated() -> None:
    """A second, separate defect found while fixing the first.

    `recursive_forecast` appends a scaffold row per target date. If history already held a row for
    that date, the frame ended up with two, and every overlapping day was predicted twice -- a
    7-day request could return 14 points. The evaluation harness never hit it (it cuts history
    strictly before the first target), but the FastAPI service has no such guarantee, and the
    backend worker can easily send history running past `start_day`.
    """
    config = FeatureConfig()
    # 90 days from 2017-01-01 runs to 2017-03-31, straddling the target window below.
    history = _history(days=90)
    encoding, names = _fitted(history, config)
    booster = _SpyBooster(names)

    targets = pd.date_range("2017-03-02", periods=7, freq="D")

    with pytest.warns(UserWarning, match="dropping .* history row"):
        result = recursive_forecast(
            booster,
            history,
            targets,
            config,
            encoding,
            future_calendar=pd.DataFrame(
                {"date": targets, "is_holiday": 0, "is_day_before_holiday": 0}
            ),
        )

    assert len(result) == 7
    assert result["date"].is_unique
    assert list(result["date"]) == list(targets)


def test_history_entirely_inside_the_forecast_window_is_an_error() -> None:
    config = FeatureConfig()
    history = _history(days=10)  # 2017-01-01 .. 2017-01-10
    encoding, _ = _fitted(history, config)

    with pytest.raises(ValueError, match="no history remains"):
        recursive_forecast(
            object(),
            history,
            pd.date_range("2017-01-01", periods=3, freq="D"),
            config,
            encoding,
        )
