"""Feature pipeline tests. The leakage test is the one that matters.

A leak does not raise. It produces a validation score that looks like a breakthrough and a model
that is worthless in production, and the only way to catch it is to assert the property directly:
a feature for date d must not change when data after d changes.
"""

import pandas as pd
import pytest

from ecopan_ml.features.build import (
    CategoryEncoding,
    FeatureConfig,
    build_features,
    ensure_dense_daily,
    feature_matrix,
)


def toy_frame(days: int = 60, stores=("1", "2")) -> pd.DataFrame:
    dates = pd.date_range("2024-01-01", periods=days, freq="D")
    return pd.DataFrame(
        [
            {"store_nbr": store, "family": "BREAD", "date": day, "demand": float(index + 1)}
            for store in stores
            for index, day in enumerate(dates)
        ]
    )


def test_lags_are_positionally_correct():
    featured = build_features(ensure_dense_daily(toy_frame()), FeatureConfig(), dropna=True)
    row = featured.loc[featured["store_nbr"] == "1"].sort_values("date").iloc[10]

    # demand increments by 1 per day, so lag_k is exactly demand - k.
    assert row["lag_1"] == pytest.approx(row["demand"] - 1)
    assert row["lag_7"] == pytest.approx(row["demand"] - 7)
    assert row["lag_28"] == pytest.approx(row["demand"] - 28)


def test_rolling_mean_excludes_the_target_day():
    """`.shift(1).rolling(7)` covers d-7..d-1. Including d would be a leak of the answer itself."""
    featured = build_features(ensure_dense_daily(toy_frame()), FeatureConfig(), dropna=True)
    row = featured.loc[featured["store_nbr"] == "1"].sort_values("date").iloc[10]

    # Mean of the seven strictly-preceding days of an arithmetic sequence = demand - 4.
    assert row["roll_mean_7"] == pytest.approx(row["demand"] - 4)


def test_no_leakage_from_future_rows():
    """THE test. Perturb the future; every feature at date d must be untouched.

    Catches the whole family of accidents -- an unshifted rolling window, a centred window, a
    groupby that sorts differently, a merge that pulls a value backwards in time.
    """
    config = FeatureConfig()
    base = ensure_dense_daily(toy_frame(days=60))
    cutoff = pd.Timestamp("2024-02-10")

    perturbed = base.copy()
    future = perturbed["date"] > cutoff
    perturbed.loc[future, "demand"] = perturbed.loc[future, "demand"] * 1000 + 12345

    original_features = build_features(base, config, dropna=True)
    perturbed_features = build_features(perturbed, config, dropna=True)

    feature_cols = [c for c in config.feature_names() if c not in ("store_nbr", "family")]
    left = original_features.loc[original_features["date"] <= cutoff, feature_cols].reset_index(
        drop=True
    )
    right = perturbed_features.loc[perturbed_features["date"] <= cutoff, feature_cols].reset_index(
        drop=True
    )

    pd.testing.assert_frame_equal(left, right)


def test_dense_calendar_fills_gaps_with_zero():
    """Lags are positional, so a sparse frame would make `shift(7)` mean the wrong thing."""
    sparse = toy_frame(days=10)
    sparse = sparse.loc[sparse["date"] != pd.Timestamp("2024-01-05")]

    dense = ensure_dense_daily(sparse)

    per_series = dense.loc[dense["store_nbr"] == "1"]
    assert len(per_series) == 10
    assert per_series.loc[per_series["date"] == pd.Timestamp("2024-01-05"), "demand"].iloc[0] == 0.0


def test_training_and_serving_produce_identical_features():
    """The training/serving skew guard.

    A serving-shaped slice (history up to a cutoff, then one target row appended) must yield exactly
    the feature values the training frame has for that date. If these two ever diverge, the model is
    scored on one distribution and deployed on another.
    """
    config = FeatureConfig()
    full = ensure_dense_daily(toy_frame(days=60))
    target_date = pd.Timestamp("2024-02-20")

    training_row = (
        build_features(full, config, dropna=True)
        .query("date == @target_date and store_nbr == '1'")
        .reset_index(drop=True)
    )

    serving_history = full.loc[(full["date"] < target_date) & (full["store_nbr"] == "1")]
    scaffold = pd.DataFrame(
        [{"store_nbr": "1", "family": "BREAD", "date": target_date, "demand": float("nan")}]
    )
    serving_row = (
        build_features(
            pd.concat([serving_history, scaffold], ignore_index=True), config, dropna=False
        )
        .query("date == @target_date")
        .reset_index(drop=True)
    )

    feature_cols = [c for c in config.feature_names() if c not in ("store_nbr", "family")]
    pd.testing.assert_frame_equal(
        training_row[feature_cols], serving_row[feature_cols], check_dtype=False
    )


def test_feature_names_do_not_depend_on_the_input_frame():
    """Regression test for a real bug found while building this.

    The schema was originally inferred from "which columns are present", which produced 15 features
    at training and 13 at serving, and LightGBM rejected the predict call. The config alone decides
    the schema now.
    """
    config = FeatureConfig()
    assert len(config.feature_names()) == len(FeatureConfig(**config.__dict__).feature_names())
    assert "is_holiday" in config.feature_names()
    assert "is_holiday" not in FeatureConfig(use_holidays=False).feature_names()


def test_unseen_category_encodes_to_minus_one():
    """An unknown store must not silently impersonate store 0.

    -1 is LightGBM's missing marker, so the model falls back on pooled behaviour instead of applying
    another store's learned offset.
    """
    encoding = CategoryEncoding().fit(toy_frame(), ["store_nbr", "family"])
    unknown = pd.DataFrame([{"store_nbr": "999", "family": "BREAD"}])

    assert encoding.transform(unknown)["store_nbr"].iloc[0] == -1


def test_feature_matrix_column_order_follows_config():
    """Order is part of the contract -- LightGBM matches features positionally."""
    config = FeatureConfig()
    featured = build_features(ensure_dense_daily(toy_frame()), config, dropna=True)
    encoding = CategoryEncoding().fit(featured, ["store_nbr", "family"])

    matrix, names = feature_matrix(featured, config, encoding)
    assert list(matrix.columns) == names == config.feature_names()
