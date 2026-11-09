"""CLI entry points behind the Makefile targets. One command per pipeline stage.

The staging is not cosmetic -- it enforces plan 08 §3's ordering discipline:

    make data       raw CSVs -> dense daily parquet
    make baselines  score both baselines on VALIDATION, pick the reference, write RESULTS.md
    make train      fit LightGBM (early stopping on validation)
    make evaluate   score everything on the UNTOUCHED test window, state the verdict
    make plots      defence figures

`baselines` must run and its output be committed before `train` is ever invoked, because the
threshold has to be fixed before anyone has seen a model number. The commit timestamp is the
evidence. Nothing in the code can force that ordering -- but keeping the stages separate, with the
threshold living in a committed file rather than a variable, makes violating it a deliberate act
rather than an accident.
"""

import argparse
import json
from dataclasses import replace
from pathlib import Path

import pandas as pd

from ecopan_ml.baselines.naive import (
    BASELINE_MOVING_AVERAGE,
    BASELINE_SAME_WEEKDAY,
    cold_start_forecast,
    moving_average,
    same_weekday_last_week,
)
from ecopan_ml.config import RunConfig, load_config
from ecopan_ml.data.preprocess import describe, preprocess
from ecopan_ml.eval.metrics import compute_metrics
from ecopan_ml.eval.report import (
    EvaluationResult,
    comparison_table,
    evaluate_estimator,
    metrics_by_horizon,
    verdict,
)
from ecopan_ml.eval.rolling_origin import temporal_split
from ecopan_ml.eval.transfer import (
    ARMS,
    BINDING_ARM,
    N_HELD_OUT_STORES,
    SCALE_FACTOR,
    TRANSFER_SEED,
    apply_arm,
    pick_holdout_stores,
)
from ecopan_ml.features.build import DATE, SERIES_KEYS, FeatureConfig
from ecopan_ml.models.predict import CALENDAR_FLAGS, recursive_forecast
from ecopan_ml.models.train import (
    load_artifacts,
    prepare_training_frame,
    save_artifacts,
    train_model,
)
from ecopan_ml.service.schemas import MIN_HISTORY_FOR_MODEL


def _load_processed(config: RunConfig) -> pd.DataFrame:
    path = config.dataset.processed_path
    if not path.exists():
        raise FileNotFoundError(
            f"{path} not found — run `make data` first (needs Kaggle credentials; see README)"
        )
    frame = pd.read_parquet(path)
    frame["date"] = pd.to_datetime(frame["date"])
    return frame


def _future_calendar(frame: pd.DataFrame) -> pd.DataFrame:
    """Collapse the per-(series, date) holiday flags to one row per date.

    Holidays in this dataset are national or regional, never per-store-and-family, so every series
    carries the identical flag for a given date and `max` simply de-duplicates. Handing this to
    `recursive_forecast` is what lets a forecast for Christmas know it is Christmas.
    """
    columns = [column for column in CALENDAR_FLAGS if column in frame.columns]
    if not columns:
        return pd.DataFrame(columns=[DATE, *CALENDAR_FLAGS])
    calendar = frame.groupby(DATE, as_index=False)[columns].max()
    for flag in CALENDAR_FLAGS:
        if flag not in calendar.columns:
            calendar[flag] = 0
    return calendar


def cmd_data(config: RunConfig) -> None:
    frame = preprocess(
        config.dataset.raw_dir,
        config.dataset.processed_path,
        families=config.dataset.families,
        stores=config.dataset.stores,
        nrows=config.dataset.nrows,
    )
    print(f"wrote {config.dataset.processed_path}")
    print(describe(frame))


def _baseline_estimators() -> dict[str, object]:
    return {
        BASELINE_SAME_WEEKDAY: same_weekday_last_week,
        BASELINE_MOVING_AVERAGE: moving_average,
    }


def cmd_baselines(config: RunConfig) -> None:
    """Score both baselines on the VALIDATION window and pick the reference.

    Validation, not test. The reference-baseline choice is a decision made by looking at numbers, so
    it consumes the window it is made on -- doing it on test would spend the holdout before the
    model has even been trained.
    """
    frame = _load_processed(config)
    results: list[EvaluationResult] = []

    for name, estimator in _baseline_estimators().items():
        result = evaluate_estimator(
            name,
            estimator,  # type: ignore[arg-type]
            frame,
            test_start=pd.Timestamp(config.split.train_end) + pd.Timedelta(days=1),
            test_end=config.split.val_end,
            horizon=config.evaluation.horizon,
            step=config.evaluation.step,
        )
        results.append(result)
        print(f"{name}: {result.pooled.as_dict()}")

    table = comparison_table(results)
    reference = table.sort_values("wape").iloc[0]["estimator"]
    print(f"\nreference baseline (best pooled WAPE on validation): {reference}")

    output = config.artifacts_dir / "baselines_validation.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {
                "window": "validation",
                "reference_baseline": reference,
                "results": table.to_dict("records"),
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"wrote {output}")
    print(
        "\nNEXT: set `threshold.reference_baseline` in configs/favorita.yaml,\n"
        "record these numbers\n"
        "and the chosen threshold in RESULTS.md, and COMMIT — before running `make train`."
    )


def cmd_train(config: RunConfig) -> None:
    frame = _load_processed(config)
    featured, encoding = prepare_training_frame(frame, config.features)
    train_frame, validation_frame, _ = temporal_split(
        featured,
        train_end=config.split.train_end,
        val_end=config.split.val_end,
        test_end=config.split.test_end,
    )

    params = config.lgb_params
    booster, metadata = train_model(
        train_frame,
        validation_frame,
        config.features,
        encoding,
        params=params,
        num_boost_round=int(config.model.get("num_boost_round", 3000)),
        early_stopping_rounds=int(config.model.get("early_stopping_rounds", 100)),
    )
    metadata["model_version"] = f"lgbm-{config.dataset.name}"
    metadata["config_snapshot"] = config.raw

    save_artifacts(booster, metadata, encoding, config.artifacts_dir)
    print(f"best iteration {metadata['best_iteration']} -> {config.artifacts_dir}")


def cmd_evaluate(config: RunConfig) -> None:
    """The single pass over the untouched test window.

    Preconditions are checked before any work, cheapest and most-likely-wrong first. The threshold
    check in particular has to come before the model is even loaded: it is the one most likely to be
    missed (it requires a human to have edited and committed a file), and discovering it *after* a
    long evaluation would tempt whoever hit it to fill the value in from the results they can
    already see -- which is precisely the target-shopping the check exists to prevent.
    """
    reference = config.threshold.reference_baseline
    if reference is None:
        raise SystemExit(
            "threshold.reference_baseline is unset in the config.\n"
            "Run `make baselines`, record the reference and threshold in RESULTS.md, and commit "
            "that BEFORE evaluating — otherwise the threshold is being chosen after seeing the "
            "model's score, which is exactly what plan 08 §3 forbids."
        )

    if not (config.artifacts_dir / "model.txt").exists():
        raise SystemExit(
            f"no model at {config.artifacts_dir / 'model.txt'} — run `make train` first."
        )

    frame = _load_processed(config)
    booster, metadata, encoding = load_artifacts(config.artifacts_dir)
    model_version = metadata.get("model_version", "lgbm")

    # The holiday calendar for the test dates. `frame` already carries a flag per (series, date),
    # and the flags are national/regional -- so one row per date is the whole calendar.
    #
    # This is not leakage. A calendar is knowable years ahead; the model is *trained* on these
    # flags, and withholding them at scoring time does not make the evaluation more honest, it makes
    # it wrong in the model's disfavour. Everything genuinely unknowable (demand, promo) still comes
    # only from `history`, which `iter_origin_slices` cuts strictly before the first target date.
    calendar = _future_calendar(frame)

    def model_estimator(history: pd.DataFrame, target_dates: pd.DatetimeIndex) -> pd.DataFrame:
        return recursive_forecast(
            booster,
            history,
            target_dates,
            config.features,
            encoding,
            model_version=model_version,
            future_calendar=calendar,
        )

    estimators: dict[str, object] = {**_baseline_estimators(), model_version: model_estimator}

    test_start = pd.Timestamp(config.split.val_end) + pd.Timedelta(days=1)
    results = [
        evaluate_estimator(
            name,
            estimator,  # type: ignore[arg-type]
            frame,
            test_start=test_start,
            test_end=config.split.test_end,
            horizon=config.evaluation.horizon,
            step=config.evaluation.step,
        )
        for name, estimator in estimators.items()
    ]

    table = comparison_table(results, reference=reference)
    passed, sentence = verdict(
        table,
        reference=reference,
        candidate=model_version,
        threshold=config.threshold.min_relative_improvement,
    )

    print(table.to_string(index=False))
    print()
    print(sentence)
    if not passed:
        print(
            "Baseline remains the serving default. Per plan 08 §3 step 3 this is reported as-is: "
            "an unbeaten baseline means the model is not yet earning its place."
        )

    by_horizon = pd.concat(
        [metrics_by_horizon(r).assign(estimator=r.name) for r in results], ignore_index=True
    )

    output = config.artifacts_dir / "test_evaluation.json"
    output.write_text(
        json.dumps(
            {
                "window": {"start": str(test_start.date()), "end": config.split.test_end},
                "reference_baseline": reference,
                "threshold": config.threshold.min_relative_improvement,
                "passed": bool(passed),
                "verdict": sentence,
                "pooled": table.to_dict("records"),
                "by_horizon": by_horizon.to_dict("records"),
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    for result in results:
        result.predictions.to_parquet(
            config.artifacts_dir / f"predictions_{result.name}.parquet", index=False
        )
    print(f"\nwrote {output}")


def cmd_plots(config: RunConfig) -> None:
    from ecopan_ml.eval.plots import (
        pick_family_series,
        pick_representative_series,
        plot_error_by_horizon,
        plot_feature_importance,
        plot_predicted_vs_actual,
    )

    booster, metadata, _ = load_artifacts(config.artifacts_dir)
    model_version = metadata.get("model_version", "lgbm")
    predictions = pd.read_parquet(config.artifacts_dir / f"predictions_{model_version}.parquet")

    plots_dir = config.plots_dir
    # The flagship family first, then the volume spread. Without the first call the figure set
    # covers groceries and stationery and says nothing about bread.
    flagship = str(config.raw.get("report", {}).get("flagship_family", "BREAD/BAKERY"))
    selected = pick_family_series(predictions, flagship) + pick_representative_series(predictions)
    for keys in selected:
        name = "_".join(str(part) for part in keys).replace("/", "-").replace(" ", "-")
        plot_predicted_vs_actual(
            predictions, plots_dir / f"pred_vs_actual_{name}.png", series_keys=keys
        )

    evaluation = json.loads((config.artifacts_dir / "test_evaluation.json").read_text())
    plot_error_by_horizon(
        pd.DataFrame(evaluation["by_horizon"]), plots_dir / "error_by_horizon.png"
    )
    plot_feature_importance(booster, plots_dir / "feature_importance.png")
    print(f"wrote plots -> {plots_dir}")


def cmd_backtest(config: RunConfig) -> None:
    """Walk-forward robustness study. NOT part of the pre-registered test-window claim.

    The headline result is one six-week window (2017-07-01 … 08-15). It contains seven rolling
    origins, but all seven sit at the same position in the year, so it cannot answer "would the gain
    survive December?". This does: several folds at different seasonal positions, each with the
    model retrained from scratch on data strictly before that fold.

    Retraining per fold is the whole cost and the whole point. The shipped artifact was trained on
    data that includes every window below, so scoring them with it would be leakage in the model's
    favour -- and a robustness check that cheats is worse than none.

    What this reports is the *spread*, not the mean. A gain that averages well across folds but
    collapses in one of them is a model with a seasonal blind spot, and the minimum is the number
    that should be defended.
    """
    if not config.backtest:
        raise SystemExit("no `backtest.windows` in the config — nothing to run.")

    reserved_start = pd.Timestamp(config.split.val_end) + pd.Timedelta(days=1)
    for window in config.backtest:
        if pd.Timestamp(window.score_end) >= reserved_start:
            raise SystemExit(
                f"backtest window scoring to {window.score_end} reaches the reserved test window "
                f"(from {reserved_start.date()}). Move it earlier: the point of this study is to "
                "corroborate the held-out result, not to spend it."
            )

    frame = _load_processed(config)
    calendar = _future_calendar(frame)
    rows: list[dict[str, object]] = []

    for index, window in enumerate(config.backtest, start=1):
        print(f"\n[{index}/{len(config.backtest)}] {window.score_start} … {window.score_end}")

        # Only data strictly before this fold's scoring window is visible, at every stage.
        visible = frame.loc[frame[DATE] < pd.Timestamp(window.score_start)]
        featured, encoding = prepare_training_frame(visible, config.features)

        # Two-way split, not `temporal_split`'s three-way: a fold has no test slice of its own --
        # its scoring window is `frame`'s, cut by the rolling-origin harness further down. Slicing
        # inline rather than passing a degenerate third boundary keeps `temporal_split`'s
        # "boundaries must strictly increase" guard meaningful for the callers that need it.
        dates = featured[DATE]
        train_frame = featured.loc[dates <= pd.Timestamp(window.train_end)]
        validation_frame = featured.loc[
            (dates > pd.Timestamp(window.train_end)) & (dates <= pd.Timestamp(window.val_end))
        ]
        if train_frame.empty or validation_frame.empty:
            raise SystemExit(f"fold {window.score_start} has an empty train or validation slice")
        booster, metadata = train_model(
            train_frame,
            validation_frame,
            config.features,
            encoding,
            params=config.lgb_params,
            num_boost_round=int(config.model.get("num_boost_round", 3000)),
            early_stopping_rounds=int(config.model.get("early_stopping_rounds", 100)),
        )
        print(f"    trained to iteration {metadata['best_iteration']}")

        def model_estimator(
            history: pd.DataFrame,
            target_dates: pd.DatetimeIndex,
            _booster=booster,
            _encoding=encoding,
        ) -> pd.DataFrame:
            return recursive_forecast(
                _booster,
                history,
                target_dates,
                config.features,
                _encoding,
                model_version="lgbm",
                future_calendar=calendar,
            )

        estimators: dict[str, object] = {**_baseline_estimators(), "lgbm": model_estimator}
        for name, estimator in estimators.items():
            result = evaluate_estimator(
                name,
                estimator,  # type: ignore[arg-type]
                frame,
                test_start=pd.Timestamp(window.score_start),
                test_end=window.score_end,
                horizon=config.evaluation.horizon,
                step=config.evaluation.step,
            )
            metrics = result.pooled.as_dict()
            # d+1 separately: the pooled figure averages horizons 1-7, but the decision this system
            # supports is tomorrow morning's bake. Quoting a pooled number next to a d+1 number
            # without saying which is which is how a table starts looking like cherry-picking.
            horizons = metrics_by_horizon(result)
            first_day = horizons.loc[horizons["horizon"] == 1]
            rows.append(
                {
                    "window": window.score_start,
                    "estimator": name,
                    "best_iteration": metadata["best_iteration"] if name == "lgbm" else None,
                    **metrics,
                    "mae_d1": float(first_day["mae"].iloc[0]) if len(first_day) else float("nan"),
                    "wape_d1": float(first_day["wape"].iloc[0]) if len(first_day) else float("nan"),
                }
            )
            print(
                f"    {name:16s} MAE {metrics['mae']:8.2f}  WAPE {metrics['wape']:.4f}"
                f"   |  d+1 WAPE {rows[-1]['wape_d1']:.4f}"
            )

    table = pd.DataFrame(rows)
    reference = config.threshold.reference_baseline or BASELINE_SAME_WEEKDAY
    gains = []
    for window_start, group in table.groupby("window", sort=True):
        indexed = group.set_index("estimator")
        base, model = indexed.loc[reference], indexed.loc["lgbm"]
        gains.append(
            {
                "window": window_start,
                "best_iteration": model["best_iteration"],
                "baseline_wape": base["wape"],
                "model_wape": model["wape"],
                "wape_gain": (base["wape"] - model["wape"]) / base["wape"],
                "mae_gain": (base["mae"] - model["mae"]) / base["mae"],
                "baseline_wape_d1": base["wape_d1"],
                "model_wape_d1": model["wape_d1"],
                "wape_gain_d1": (base["wape_d1"] - model["wape_d1"]) / base["wape_d1"],
            }
        )
    summary = pd.DataFrame(gains)

    print("\n=== walk-forward summary (vs " + reference + ") ===")
    print(summary.to_string(index=False))
    worst = summary["wape_gain"].min()
    print(
        f"\nWAPE gain across {len(summary)} folds, horizons 1-7 pooled: "
        f"min {worst:+.1%}, median {summary['wape_gain'].median():+.1%}, "
        f"max {summary['wape_gain'].max():+.1%}"
    )
    print(
        f"WAPE gain across {len(summary)} folds, d+1 only:            "
        f"min {summary['wape_gain_d1'].min():+.1%}, "
        f"median {summary['wape_gain_d1'].median():+.1%}, "
        f"max {summary['wape_gain_d1'].max():+.1%}"
    )
    print(
        f"the number to defend is the minimum ({worst:+.1%}), not the average — "
        f"it {'clears' if worst >= config.threshold.min_relative_improvement else 'MISSES'} "
        f"the {config.threshold.min_relative_improvement:.0%} bar."
    )

    output = Path("reports/evaluation/backtest.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {
                "_note": (
                    "Walk-forward robustness study. Separate from the pre-registered test-window "
                    "claim in RESULTS.md section 4: different training cuts, different windows, "
                    "so these absolute numbers are NOT comparable to section 4's. Only the "
                    "per-fold gain over the same-fold baseline is the claim."
                ),
                "reference": reference,
                "per_estimator": table.to_dict("records"),
                "per_window_gain": summary.to_dict("records"),
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"wrote {output}")


def cmd_ablation(config: RunConfig) -> None:
    """Which feature groups actually carry the gain? Scored on VALIDATION, never on the test window.

    That restriction is the whole design. An ablation is a model-selection experiment: it asks which
    variant is best, and answering it against the held-out window would spend the pre-registered
    claim on feature selection -- the precise thing plan 08 §3 and RESULTS.md §4.1b are built to
    prevent. Validation is the surface that already absorbs every decision made by looking at a
    score, so it is the honest place for this and the numbers here are deliberately NOT comparable
    to §4.2's.

    Each variant retrains from scratch with early stopping. Dropping a feature group changes how
    fast the model fits, so reusing the full model's iteration count would confound "this feature
    mattered" with "this variant was stopped at the wrong place".
    """
    frame = _load_processed(config)
    base = config.features

    # Groups are defined by what the config can actually switch off, so every row here is a
    # reproducible `FeatureConfig`, not a hand-edited column list.
    variants: list[tuple[str, FeatureConfig, str]] = [
        ("full", base, "every feature the shipped model uses"),
        (
            "no_rolling",
            replace(base, rolling_windows=(), rolling_std_windows=()),
            "drop rolling mean/std — keep raw lags",
        ),
        (
            "no_calendar",
            replace(base, use_calendar=False),
            "drop dow/month/day-of-month/is_weekend",
        ),
        ("no_holidays", replace(base, use_holidays=False), "drop both holiday flags"),
        (
            "lag1_only",
            replace(base, lags=(1,), rolling_windows=(), rolling_std_windows=()),
            "yesterday only — the leanest thing still a model",
        ),
        (
            "no_weekly_lags",
            replace(base, lags=tuple(lag for lag in base.lags if lag % 7 != 0)),
            "drop lag_7/14/28 — leave the weekly rhythm to the calendar features",
        ),
    ]

    rows: list[dict[str, object]] = []
    for index, (name, features, note) in enumerate(variants, start=1):
        print(f"\n[{index}/{len(variants)}] {name}: {note}")
        featured, encoding = prepare_training_frame(frame, features)
        train_frame, validation_frame, _ = temporal_split(
            featured,
            train_end=config.split.train_end,
            val_end=config.split.val_end,
            test_end=config.split.test_end,
        )
        # The booster itself is not needed: the ablation compares recorded validation curves, and
        # nothing here forecasts or persists an artifact.
        _booster, metadata = train_model(
            train_frame,
            validation_frame,
            features,
            encoding,
            params=config.lgb_params,
            num_boost_round=int(config.model.get("num_boost_round", 3000)),
            early_stopping_rounds=int(config.model.get("early_stopping_rounds", 100)),
        )
        # Read the validation curve at the selected iteration rather than re-scoring: `train_model`
        # already recorded it, and a second pass through the recursive forecast would add cost
        # without adding information. `best_iteration` is 1-based, the curve is 0-based.
        best = int(metadata["best_iteration"])
        curve = metadata["evals"]["validation"]
        mae = float(curve["l1"][best - 1])
        rmse = float(curve["rmse"][best - 1]) if "rmse" in curve else float("nan")
        rows.append(
            {
                "variant": name,
                "note": note,
                "n_features": len(features.feature_names()),
                "best_iteration": best,
                "val_mae": mae,
                "val_rmse": rmse,
            }
        )
        print(f"    iter {best:>6}  features {rows[-1]['n_features']:>2}  val MAE {mae:8.3f}")

    table = pd.DataFrame(rows)
    full_mae = float(table.loc[table["variant"] == "full", "val_mae"].iloc[0])
    # Positive = removing the group made validation error worse, i.e. the group was pulling weight.
    table["mae_cost_of_removal"] = (table["val_mae"] - full_mae) / full_mae

    print("\n=== feature ablation (validation only) ===")
    print(
        table[
            ["variant", "n_features", "best_iteration", "val_mae", "mae_cost_of_removal"]
        ].to_string(index=False, float_format=lambda v: f"{v:.4f}")
    )
    ranked = table.loc[table["variant"] != "full"].sort_values(
        "mae_cost_of_removal", ascending=False
    )
    print("\nmost to least load-bearing:")
    for _, row in ranked.iterrows():
        print(f"  {row['variant']:16s} {row['mae_cost_of_removal']:+7.2%}  ({row['note']})")

    output = Path("reports/evaluation/ablation.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {
                "_note": (
                    "Feature ablation, scored on VALIDATION. Not comparable to RESULTS.md §4.2, "
                    "which is the untouched test window. Running this against the test window "
                    "would spend the pre-registered claim on feature selection."
                ),
                "full_val_mae": full_mae,
                "variants": table.to_dict("records"),
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"\nwrote {output}")


def cmd_family(config: RunConfig) -> None:
    """Metrics for one product family, recomputed from the committed predictions.

    Deliberately reads the prediction parquets rather than re-running the evaluation: the numbers
    for the flagship family belong in a file, not in an ad-hoc slice someone ran once and pasted
    into RESULTS.md, but regenerating them must not become a fourth pass over the held-out window.
    Reading the same predictions that produced §4.2 is a recomputation, not a new scoring, so it
    carries no disclosure obligation.

    `BREAD/BAKERY` is chosen because it is the product category this system exists for, fixed by the
    domain before any model was fitted -- not because of how the model scores on it. It in fact
    scores *worse* there than pooled (see RESULTS.md), which is the point of reporting it.
    """
    family = str(config.raw.get("report", {}).get("flagship_family", "BREAD/BAKERY"))
    artifacts = config.artifacts_dir
    reference = config.threshold.reference_baseline or BASELINE_SAME_WEEKDAY

    _booster, metadata, _encoding = load_artifacts(artifacts)
    model_version = metadata.get("model_version", "lgbm")

    rows: list[dict[str, object]] = []
    for name in (BASELINE_SAME_WEEKDAY, BASELINE_MOVING_AVERAGE, model_version):
        path = artifacts / f"predictions_{name}.parquet"
        if not path.exists():
            raise SystemExit(f"missing {path} — run `make evaluate` first.")
        frame = pd.read_parquet(path)
        subset = frame.loc[frame[SERIES_KEYS[1]].astype(str) == family]
        if subset.empty:
            raise SystemExit(f"no rows for family {family!r} in {path.name}")
        scores = compute_metrics(subset["y_true"], subset["y_pred"])
        rows.append(
            {
                "estimator": name,
                "n_rows": len(subset),
                "n_series": int(subset.groupby(SERIES_KEYS, observed=True).ngroups),
                "mean_actual": float(subset["y_true"].mean()),
                **scores.as_dict(),
            }
        )

    table = pd.DataFrame(rows).set_index("estimator")
    model = table.loc[model_version]
    gains = {
        name: {
            "wape_gain": float((table.loc[name, "wape"] - model["wape"]) / table.loc[name, "wape"]),
            "mae_gain": float((table.loc[name, "mae"] - model["mae"]) / table.loc[name, "mae"]),
        }
        for name in (BASELINE_SAME_WEEKDAY, BASELINE_MOVING_AVERAGE)
    }

    print(f"=== {family} — holdout, horizons 1-7 pooled ===")
    print(table.to_string(float_format=lambda v: f"{v:.4f}"))
    for name, gain in gains.items():
        print(f"  vs {name:18s} WAPE {gain['wape_gain']:+.1%}   MAE {gain['mae_gain']:+.1%}")

    output = Path("reports/evaluation/family_breakdown.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {
                "_note": (
                    "Recomputed from the committed prediction parquets, not a re-scoring of the "
                    "held-out window. Same predictions as RESULTS.md section 4.2."
                ),
                "family": family,
                "reference_baseline": reference,
                "per_estimator": table.reset_index().to_dict("records"),
                "gains_vs": gains,
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"wrote {output}")


def cmd_coldstart(config: RunConfig) -> None:
    """How much history does a series need before the model beats the cold-start baseline?

    The service's threshold used to be a reasoned 42 — 28 days for the longest lag, plus two weeks
    so the rolling windows were "meaningful rather than mostly warm-up". A sound-sounding argument
    and an untested one. This command is what measured it, and the margin turned out to be empty:
    the curve is flat from 28 days on, so `MIN_HISTORY_FOR_MODEL` is now derived from
    `FeatureConfig.max_lookback` instead. Re-run it whenever the feature set changes.

    Scored on VALIDATION, for the same reason the ablation is: the threshold is a deployment
    parameter being *chosen*, and choosing it against the held-out window would spend the
    pre-registered claim on tuning.

    The shipped model is used as-is at every history length -- no retraining. That matches serving:
    one pooled model meets a new series with whatever history that series has, and the question is
    when its features become informative, not whether a model trained only on short series would do
    better.
    """
    frame = _load_processed(config)
    booster, _metadata, encoding = load_artifacts(config.artifacts_dir)
    calendar = _future_calendar(frame)

    val_start = str((pd.Timestamp(config.split.train_end) + pd.Timedelta(days=1)).date())
    val_end = config.split.val_end

    def truncated_model(days: int):
        def estimator(history: pd.DataFrame, target_dates: pd.DatetimeIndex) -> pd.DataFrame:
            # Keep only the last `days` observations before the origin, per series -- the situation
            # a newly-onboarded bakery is actually in.
            cutoff = target_dates[0] - pd.Timedelta(days=days)
            visible = history.loc[history[DATE] >= cutoff]
            return recursive_forecast(
                booster,
                visible,
                target_dates,
                config.features,
                encoding,
                model_version="lgbm",
                future_calendar=calendar,
            )

        return estimator

    def truncated_baseline(days: int):
        def estimator(history: pd.DataFrame, target_dates: pd.DatetimeIndex) -> pd.DataFrame:
            cutoff = target_dates[0] - pd.Timedelta(days=days)
            visible = history.loc[history[DATE] >= cutoff]
            return cold_start_forecast(visible, target_dates)

        return estimator

    lengths = [7, 14, 21, 28, 35, 42, 56, 90, 180]
    rows: list[dict[str, object]] = []
    for days in lengths:
        entry: dict[str, object] = {"history_days": days}
        for label, factory in (("lgbm", truncated_model), ("cold_start", truncated_baseline)):
            result = evaluate_estimator(
                f"{label}_{days}d",
                factory(days),  # type: ignore[arg-type]
                frame,
                test_start=val_start,
                test_end=val_end,
                horizon=config.evaluation.horizon,
                step=config.evaluation.step,
            )
            pooled = result.pooled.as_dict()
            entry[f"{label}_mae"] = pooled["mae"]
            entry[f"{label}_wape"] = pooled["wape"]
        entry["model_advantage_wape"] = (
            float(entry["cold_start_wape"]) - float(entry["lgbm_wape"])
        ) / float(entry["cold_start_wape"])
        rows.append(entry)
        print(
            f"  {days:>4}d history   lgbm WAPE {entry['lgbm_wape']:.4f}   "
            f"cold-start {entry['cold_start_wape']:.4f}   "
            f"model {entry['model_advantage_wape']:+.1%}"
        )

    table = pd.DataFrame(rows)
    ahead = table.loc[table["model_advantage_wape"] > 0, "history_days"]
    crossover = int(ahead.min()) if len(ahead) else None

    print("\n=== cold-start curve (validation) ===")
    print(table.to_string(index=False, float_format=lambda v: f"{v:.4f}"))
    if crossover is None:
        print("\nthe model never overtakes the cold-start baseline at any tested history length.")
    else:
        print(
            f"\nthe model first beats the cold-start baseline at {crossover} days of history; "
            f"the service currently switches at {MIN_HISTORY_FOR_MODEL}."
        )

    output = Path("reports/evaluation/coldstart_curve.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {
                "_note": (
                    "Validation window, shipped model, history truncated per origin. Chooses a "
                    "deployment threshold, so it must not touch the held-out window."
                ),
                "service_threshold_days": MIN_HISTORY_FOR_MODEL,
                "first_length_where_model_wins": crossover,
                "curve": table.to_dict("records"),
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"wrote {output}")


def cmd_transfer(config: RunConfig) -> None:
    """Pre-registered transfer experiment (RESULTS.md §4.7): unseen, rescaled series.

    Scores the SHIPPED model against `baseline-swlw` on a seeded subset of whole stores, in four
    arms (seen/unseen keys x unit/gram-like scale). The window is the VALIDATION window by
    construction (derived from `split`, never from `test_end`): this chooses a deployment rule, so
    it must not spend the holdout. The binding arm is D (unseen + rescaled); its pass/fail drives
    how the service routes unseen series.
    """
    if not (config.artifacts_dir / "model.txt").exists():
        raise SystemExit(
            f"no model at {config.artifacts_dir / 'model.txt'} — run `make train` first."
        )

    frame = _load_processed(config)
    booster, metadata, encoding = load_artifacts(config.artifacts_dir)
    calendar = _future_calendar(frame)

    window_start = pd.Timestamp(config.split.train_end) + pd.Timedelta(days=1)
    window_end = pd.Timestamp(config.split.val_end)

    stores = pick_holdout_stores(
        frame[SERIES_KEYS[0]].unique(), n=N_HELD_OUT_STORES, seed=TRANSFER_SEED
    )
    print(f"held-out stores (seed {TRANSFER_SEED}): {stores}")

    def model_estimator(history: pd.DataFrame, target_dates: pd.DatetimeIndex) -> pd.DataFrame:
        return recursive_forecast(
            booster,
            history,
            target_dates,
            config.features,
            encoding,
            model_version="lgbm",
            future_calendar=calendar,
        )

    threshold = config.threshold.min_relative_improvement
    reference = BASELINE_SAME_WEEKDAY
    arms_out: list[dict[str, object]] = []

    for arm in ARMS:
        arm_frame = apply_arm(frame, stores, arm)
        scores: dict[str, dict[str, float]] = {}
        for name, estimator in ((reference, same_weekday_last_week), ("lgbm", model_estimator)):
            result = evaluate_estimator(
                name,
                estimator,  # type: ignore[arg-type]
                arm_frame,
                test_start=window_start,
                test_end=window_end,
                horizon=config.evaluation.horizon,
                step=config.evaluation.step,
            )
            first_day = metrics_by_horizon(result).query("horizon == 1").iloc[0]
            scores[name] = {
                **result.pooled.as_dict(),
                "mae_d1": float(first_day["mae"]),
                "wape_d1": float(first_day["wape"]),
            }
        base, model = scores[reference], scores["lgbm"]
        gains = {
            "mae_gain": (base["mae"] - model["mae"]) / base["mae"],
            "wape_gain": (base["wape"] - model["wape"]) / base["wape"],
            "mae_gain_d1": (base["mae_d1"] - model["mae_d1"]) / base["mae_d1"],
            "wape_gain_d1": (base["wape_d1"] - model["wape_d1"]) / base["wape_d1"],
        }
        clears = gains["mae_gain"] >= threshold and gains["wape_gain"] >= threshold
        arms_out.append(
            {
                "arm": arm.name,
                "unseen": arm.unseen,
                "scale": arm.scale,
                "binding": arm.name == BINDING_ARM,
                "n_series": int(arm_frame.groupby(SERIES_KEYS, observed=True).ngroups),
                "scores": scores,
                "gains": gains,
                "clears_bar": bool(clears),
            }
        )
        print(
            f"  {arm.name:20s} MAE {gains['mae_gain']:+.1%}  WAPE {gains['wape_gain']:+.1%}  "
            f"| d+1 MAE {gains['mae_gain_d1']:+.1%}  WAPE {gains['wape_gain_d1']:+.1%}"
            f"  {'clears' if clears else 'misses'} {threshold:.0%}"
        )

    binding = next(a for a in arms_out if a["binding"])
    passed = bool(binding["clears_bar"])
    print(
        f"\nBINDING ARM ({BINDING_ARM}): {'PASS' if passed else 'FAIL'} — "
        + (
            "unseen series keep model routing."
            if passed
            else "route unseen series to the labelled baseline regardless of history length."
        )
    )

    output = Path("reports/evaluation/transfer.json")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(
        json.dumps(
            {
                "_note": (
                    "RESULTS.md section 4.7. Validation window only, shipped model, no retraining. "
                    "Only the binding arm carries the pre-registered bar; others are diagnostic."
                ),
                "model_version": metadata.get("model_version", "lgbm"),
                "window": [str(window_start.date()), str(window_end.date())],
                "seed": TRANSFER_SEED,
                "held_out_stores": [int(s) for s in stores],
                "scale_factor": SCALE_FACTOR,
                "reference_baseline": reference,
                "threshold": threshold,
                "binding_arm": BINDING_ARM,
                "passed": passed,
                "arms": arms_out,
            },
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"wrote {output}")


COMMANDS = {
    "data": cmd_data,
    "baselines": cmd_baselines,
    "train": cmd_train,
    "evaluate": cmd_evaluate,
    "backtest": cmd_backtest,
    "ablation": cmd_ablation,
    "family": cmd_family,
    "coldstart": cmd_coldstart,
    "transfer": cmd_transfer,
    "plots": cmd_plots,
}


def main() -> None:
    parser = argparse.ArgumentParser(description="ecopan ML pipeline")
    parser.add_argument("command", choices=sorted(COMMANDS))
    parser.add_argument("--config", type=Path, default=Path("configs/favorita.yaml"))
    args = parser.parse_args()

    COMMANDS[args.command](load_config(args.config))


if __name__ == "__main__":
    main()
