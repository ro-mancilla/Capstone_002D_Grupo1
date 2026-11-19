"""Metric tests against hand-computed fixtures.

Every number in RESULTS.md flows through these three functions, so they are checked two ways:
against values worked out by hand (so the test is readable and independent of any library) and
against scikit-learn (so an independent implementation agrees). Testing only against sklearn would
just assert we called sklearn correctly; testing only by hand risks two people making the same
arithmetic slip.
"""

import math

import numpy as np
import pandas as pd
import pytest
from sklearn.metrics import mean_absolute_error, root_mean_squared_error

from ecopan_ml.eval.metrics import (
    compute_metrics,
    mae,
    metrics_by_group,
    relative_improvement,
    rmse,
    wape,
)


def test_mae_matches_hand_computation():
    y_true = [10.0, 20.0, 30.0]
    y_pred = [12.0, 18.0, 33.0]
    # |10-12| + |20-18| + |30-33| = 2 + 2 + 3 = 7; 7 / 3
    assert mae(y_true, y_pred) == pytest.approx(7 / 3)


def test_rmse_matches_hand_computation():
    y_true = [10.0, 20.0, 30.0]
    y_pred = [12.0, 18.0, 33.0]
    # (4 + 4 + 9) / 3 = 17/3; sqrt(17/3)
    assert rmse(y_true, y_pred) == pytest.approx(math.sqrt(17 / 3))


def test_wape_matches_hand_computation():
    y_true = [10.0, 20.0, 30.0]
    y_pred = [12.0, 18.0, 33.0]
    # Σ|error| = 7, Σ|actual| = 60
    assert wape(y_true, y_pred) == pytest.approx(7 / 60)


def test_agrees_with_sklearn():
    rng = np.random.default_rng(7)
    y_true = rng.uniform(0, 500, size=1000)
    y_pred = y_true + rng.normal(0, 25, size=1000)

    assert mae(y_true, y_pred) == pytest.approx(mean_absolute_error(y_true, y_pred))
    assert rmse(y_true, y_pred) == pytest.approx(root_mean_squared_error(y_true, y_pred))


def test_wape_is_nan_when_no_volume():
    """A window with zero actual demand has no meaningful percentage error.

    NaN rather than 0.0 (which would silently flatter) or inf (which would silently poison an
    otherwise valid pooled score). NaN is visible and forces a decision.
    """
    assert math.isnan(wape([0.0, 0.0, 0.0], [1.0, 2.0, 3.0]))


def test_zero_demand_days_do_not_break_mae_or_rmse():
    """Zero-demand days are ordinary for MAE/RMSE -- it is only MAPE that dies on them.

    This is the concrete reason WAPE is the pooled percentage metric in this project.
    """
    result = compute_metrics([0.0, 0.0, 10.0], [1.0, 0.0, 8.0])
    assert result.mae == pytest.approx(3 / 3)
    assert result.n == 3


def test_perfect_prediction_scores_zero():
    result = compute_metrics([5.0, 7.0, 9.0], [5.0, 7.0, 9.0])
    assert result.mae == 0.0
    assert result.rmse == 0.0
    assert result.wape == 0.0


def test_length_mismatch_is_rejected():
    """Silent misalignment is the classic way an evaluation reports the wrong comparison."""
    with pytest.raises(ValueError, match="length mismatch"):
        mae([1.0, 2.0], [1.0])


def test_nan_input_is_rejected():
    with pytest.raises(ValueError, match="NaN"):
        mae([1.0, float("nan")], [1.0, 2.0])


def test_empty_input_is_rejected():
    with pytest.raises(ValueError, match="empty"):
        mae([], [])


def test_metrics_by_group_sorts_by_volume():
    frame = pd.DataFrame(
        {
            "series": ["small", "small", "big", "big"],
            "y_true": [1.0, 1.0, 100.0, 100.0],
            "y_pred": [2.0, 2.0, 90.0, 90.0],
        }
    )
    result = metrics_by_group(frame, group_cols=["series"])
    assert list(result["series"]) == ["big", "small"]
    assert result.loc[result["series"] == "big", "wape"].iloc[0] == pytest.approx(20 / 200)


def test_relative_improvement_reports_regressions_as_negative():
    """A worse candidate must produce a negative number, not be clipped to zero.

    Plan 08 §3 step 3 commits us to reporting a model that loses to its baseline honestly, and that
    starts with the arithmetic not hiding it.
    """
    assert relative_improvement(baseline=10.0, candidate=8.0) == pytest.approx(0.2)
    assert relative_improvement(baseline=10.0, candidate=12.0) == pytest.approx(-0.2)
