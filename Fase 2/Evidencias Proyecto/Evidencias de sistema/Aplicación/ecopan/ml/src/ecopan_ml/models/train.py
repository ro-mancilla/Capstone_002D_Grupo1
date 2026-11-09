"""LightGBM training: one global model over all (store, family) series.

--- Why gradient-boosted trees for this problem -------------------------------------------------

Not a fashion choice. Daily retail demand at this grain has properties that suit GBMs and defeat
the obvious alternatives:

  * **Tabular, modest scale.** Thousands of series × ~1500 days is a few million rows -- the regime
    where GBMs reliably beat neural networks on tabular data, at a fraction of the training cost
    and with no GPU.
  * **Non-linear interactions matter.** "Saturday" means something different for bread than for
    pastry, and different again during a holiday week. Trees capture interactions by construction;
    a linear model needs every one hand-specified.
  * **Mixed feature types, missing values, no scaling.** Categorical identity, integer calendar
    fields, real-valued lags with NaN warm-up -- LightGBM ingests all of it directly. NaN gets a
    learned default split direction rather than imputation, which is exactly right for "this series
    has no 28-day lag yet".
  * **Robust to outliers with an L1 objective** (below), which matters when one holiday can be 5×
    a normal day.

Rejected alternatives, briefly, since the defence will be asked:

  * **ARIMA/ETS** -- classical, well-understood, and *per-series*. With thousands of short series
    that means thousands of fits, no sharing of the weekly shape across series, and no clean way to
    use covariates like holidays and promotions. Strong for one long, well-behaved series; wrong
    shape for this problem.
  * **Prophet** -- convenient, but also per-series, and it is essentially a smooth trend +
    seasonality curve-fit. It struggles precisely where our value would be: sharp weekday effects
    and covariate interactions.
  * **Deep learning (LSTM/N-BEATS/TFT)** -- competitive at much larger scale with much more data
    per series. Here it would mean far more compute and tuning to reach parity, and a model far
    harder to explain to a committee. Plan 08 §4 rules it out explicitly.

--- Objective: regression_l1 (MAE) as the default ----------------------------------------------

`regression_l1` optimises mean absolute error, so predictions converge to the conditional
**median**. `regression_l2` (the default) optimises squared error and targets the conditional
**mean**.

For zero-inflated, right-skewed demand the median is the better target: squared error is dominated
by rare huge days, so an L2 model inflates everyday forecasts to hedge against spikes it cannot
predict anyway -- systematic over-forecasting, which for a bakery is systematic waste. It also
aligns the training loss with the headline reporting metric, so early stopping optimises the thing
we actually report rather than a proxy.

`tweedie` is offered in config as the documented alternative (plan 08 §4 asks for the comparison).
The Tweedie distribution with 1 < p < 2 is a compound Poisson-Gamma: a point mass at zero plus a
continuous positive part -- a genuinely good description of "most days some demand, some days none".
It is worth trying when many series are sparse. Whichever wins on *validation* is recorded in
RESULTS.md; the choice is never made on test.
"""

import json
from dataclasses import asdict
from pathlib import Path
from typing import Any

import lightgbm as lgb
import pandas as pd

from ecopan_ml.features.build import (
    SERIES_KEYS,
    TARGET,
    CategoryEncoding,
    FeatureConfig,
    build_features,
    ensure_dense_daily,
    feature_matrix,
)

DEFAULT_PARAMS: dict[str, Any] = {
    "objective": "regression_l1",
    "metric": ["l1", "rmse"],
    "learning_rate": 0.05,
    # Capacity knobs. LightGBM grows leaf-wise (best-first) rather than level-wise, so `num_leaves`
    # -- not `max_depth` -- is the primary capacity control. Leaf-wise growth reaches lower loss per
    # tree but overfits more readily, hence the explicit `min_data_in_leaf` floor: with thousands of
    # sparse series it is otherwise easy to grow leaves that memorise a handful of days on one
    # series.
    "num_leaves": 63,
    "min_data_in_leaf": 100,
    # Stochastic regularisation: each tree sees 80% of rows and 80% of columns. Decorrelates the
    # ensemble and is usually worth more than tuning depth.
    "feature_fraction": 0.8,
    "bagging_fraction": 0.8,
    "bagging_freq": 1,
    "lambda_l2": 1.0,
    "num_threads": 0,
    "verbosity": -1,
    "seed": 42,
}


def prepare_training_frame(
    raw: pd.DataFrame, config: FeatureConfig
) -> tuple[pd.DataFrame, CategoryEncoding]:
    """Densify, build features, and fit the categorical encoding.

    The encoding is fitted on the *full* frame before splitting, on purpose. It is not a leak: a
    category code is an arbitrary identifier, not a statistic of the target, so knowing that store
    "47" exists tells the model nothing about future demand. Fitting it after splitting would give
    a validation-only store the code -1 and needlessly cripple it.
    """
    dense = ensure_dense_daily(raw)
    featured = build_features(dense, config, dropna=True)
    encoding = CategoryEncoding().fit(featured, SERIES_KEYS)
    return featured, encoding


def train_model(
    train_frame: pd.DataFrame,
    validation_frame: pd.DataFrame,
    config: FeatureConfig,
    encoding: CategoryEncoding,
    *,
    params: dict[str, Any] | None = None,
    num_boost_round: int = 3000,
    early_stopping_rounds: int = 100,
) -> tuple[lgb.Booster, dict[str, Any]]:
    """Fit with early stopping on the validation window.

    Early stopping is the main defence against overfitting here and it is why the three-way split
    exists. Boosting reduces training loss monotonically -- left alone it will fit noise -- so we
    stop when *validation* loss stops improving for `early_stopping_rounds` iterations and keep the
    best iteration.

    That makes the validation window part of model selection: its score is optimistically biased and
    must never be quoted as a generalisation estimate. Only the untouched test window gets to make
    that claim, which is precisely why plan 08 §3 requires the threshold to be committed before this
    function is ever run.
    """
    params = {**DEFAULT_PARAMS, **(params or {})}
    x_train, feature_names = feature_matrix(train_frame, config, encoding)
    x_valid, _ = feature_matrix(validation_frame, config, encoding)

    train_set = lgb.Dataset(
        x_train,
        label=train_frame[TARGET],
        feature_name=feature_names,
        categorical_feature=SERIES_KEYS,
        free_raw_data=False,
    )
    valid_set = lgb.Dataset(
        x_valid,
        label=validation_frame[TARGET],
        reference=train_set,
        feature_name=feature_names,
        categorical_feature=SERIES_KEYS,
        free_raw_data=False,
    )

    evals: dict[str, dict[str, list[float]]] = {}
    booster = lgb.train(
        params,
        train_set,
        num_boost_round=num_boost_round,
        valid_sets=[train_set, valid_set],
        valid_names=["train", "validation"],
        callbacks=[
            lgb.early_stopping(early_stopping_rounds, verbose=False),
            lgb.record_evaluation(evals),
            lgb.log_evaluation(period=100),
        ],
    )

    metadata = {
        "params": params,
        "feature_names": feature_names,
        "best_iteration": int(booster.best_iteration or num_boost_round),
        "n_train_rows": len(train_frame),
        "n_validation_rows": len(validation_frame),
        "feature_config": asdict(config),
        "evals": {
            k: {kk: [float(x) for x in vv] for kk, vv in v.items()} for k, v in evals.items()
        },
    }
    return booster, metadata


def save_artifacts(
    booster: lgb.Booster,
    metadata: dict[str, Any],
    encoding: CategoryEncoding,
    output_dir: Path,
) -> None:
    """Persist model + everything needed to reproduce its inputs.

    The encoding and the feature-name order ship *with* the model rather than being re-derived at
    load time. Re-deriving either is the classic source of training/serving skew: same artifact,
    different meaning, no error.
    """
    output_dir.mkdir(parents=True, exist_ok=True)
    booster.save_model(str(output_dir / "model.txt"), num_iteration=booster.best_iteration or None)
    (output_dir / "metadata.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    (output_dir / "encoding.json").write_text(
        json.dumps(encoding.as_dict(), indent=2), encoding="utf-8"
    )


def load_artifacts(output_dir: Path) -> tuple[lgb.Booster, dict[str, Any], CategoryEncoding]:
    booster = lgb.Booster(model_file=str(output_dir / "model.txt"))
    metadata = json.loads((output_dir / "metadata.json").read_text(encoding="utf-8"))
    encoding = CategoryEncoding.from_dict(
        json.loads((output_dir / "encoding.json").read_text(encoding="utf-8"))
    )
    return booster, metadata, encoding
