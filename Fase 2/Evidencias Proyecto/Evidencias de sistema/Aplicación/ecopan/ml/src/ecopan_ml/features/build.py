"""Feature construction. ONE implementation, used by training and by serving (plan 08 §1).

--- Training/serving skew, and why this file exists --------------------------------------------

The single most common way a forecasting system silently fails in production is *training/serving
skew*: features computed one way in the training notebook and another way in the service. The model
then receives, at inference, inputs drawn from a subtly different distribution than it was fitted
on -- a rolling mean that includes the current day in one path and excludes it in the other, a
day-of-week that is Monday=0 here and Sunday=0 there. Nothing errors. Accuracy is just quietly
worse in production than the report claimed, and the report is not wrong, it is measuring a
different system.

The structural fix is not discipline, it is having only one implementation. `build_features` is
called by `models/train.py` and by `service/app.py`, and the tests assert that a serving-shaped
slice produces byte-identical feature rows to the training frame for the same dates.

--- The leakage rule ---------------------------------------------------------------------------

Every feature for target date *d* must be computable strictly before *d*. Concretely, all lags and
rolling windows are shifted by at least one day, so nothing on row *d* is derived from y(d).

This is easy to get wrong with pandas' rolling(), whose window *includes* the current row by
default. `df.rolling(7).mean()` on the target column is a leak: at row d it averages y(d−6…d),
which contains the answer. The correct form is `.shift(1).rolling(7).mean()` -- shift first, then
roll -- and it is written that way everywhere below, with the shift applied before the window.

There is a test for this (`test_features.py::test_no_leakage`) that perturbs future rows and
asserts feature values at date d are unchanged. It is worth more than the comment: a leak of this
kind produces a spectacular validation score and a worthless model, and it will not announce
itself.

--- Why these features ------------------------------------------------------------------------

Gradient-boosted trees cannot see time. Handed a raw (date, series_id) row they can only memorise;
they have no notion that yesterday is adjacent to today. Every temporal structure the model is
allowed to use has to be made an explicit column. That is the whole job here, and the feature list
below is a direct encoding of how bakery demand actually behaves:

  lag_1               yesterday. Short-run level and momentum.
  lag_7, lag_14       same weekday, one and two weeks back. Day-of-week seasonality *and* level,
                      which is exactly what makes the same-weekday baseline strong -- we give the
                      model the same information rather than hoping it infers it.
  lag_28              four weeks back: monthly/payday rhythm, and a slower level reference that is
                      robust to a single odd week.
  roll_mean_7/28      denoised level at two timescales. The 7-day mean is the moving-average
                      baseline as a *feature*; the model can learn when to trust it. A model that
                      cannot at least reproduce its baseline has no excuse for losing to it.
  roll_std_7          recent volatility. Lets the model modulate how far it leans on the level --
                      the closest a point forecast gets to knowing its own uncertainty.
  dow, month, dom     calendar position. `dow` is the dominant one for daily retail.
  is_weekend          redundant with `dow` for a tree (which can split on it), kept because it
                      makes the split cheap and the feature-importance plot legible for the defence.
  holiday flags       today, and day-before. Bakery demand spikes *ahead* of a holiday, so the
                      lead flag carries more signal than the day itself.
  promo_share         promotion intensity. ecopan has no promotion signal today (the day-old
                      discount that was once meant to map onto it was dropped), so on bakery
                      data this feature is simply zero until one exists. Only ever
                      the value for the target date, which is legitimate: promotions are *planned*,
                      so they are known in advance for the horizon. This is the one feature that
                      may reference the target date, and it is safe for that reason -- see the
                      note in `FeatureConfig.use_promo`.
  store_nbr, family   categorical identity, native LightGBM categoricals (see below).

--- Pooled global model, not one model per series ----------------------------------------------

One model is trained across all (store, family) series, with the identity passed in as a
categorical feature. The alternative -- a model per series -- sounds more tailored and is usually
worse here:

  * Most series individually have far too little data to fit a tree ensemble without overfitting.
  * Pooling shares statistical strength: the weekly shape learned from a high-volume bread series
    informs a sparse pastry series that could never learn it alone.
  * A new series gets predictions immediately from the pooled patterns rather than needing its own
    model trained -- which is precisely the cold-start problem this project has to answer.

The cost is that one global function must fit heterogeneous scales. That is what the categorical
identity features are for: the tree can carve out series-specific regions where the pooled pattern
does not apply.

LightGBM handles categoricals natively (Fisher's optimal-split-for-categoricals), rather than us
one-hot encoding: with ~54 stores × ~33 families, one-hot would add ~90 sparse columns and force
the trees into many shallow binary splits, where the native treatment finds a good partition of
category values in one split. Categories must be integer-coded, which `encode_categoricals` does
with a *stable, persisted* mapping -- an unstable mapping is another silent training/serving skew,
since category 4 must mean the same store at inference as it did in training.
"""

from dataclasses import dataclass, field

import pandas as pd

SERIES_KEYS = ["store_nbr", "family"]
TARGET = "demand"
DATE = "date"


@dataclass(frozen=True)
class FeatureConfig:
    """Feature switches. Mirrored in `configs/favorita.yaml` so a run is reproducible from config.

    Kept as a frozen dataclass rather than a loose dict so that the config used for a training run
    can be snapshotted next to the model artifact and diffed later.
    """

    lags: tuple[int, ...] = (1, 7, 14, 28)
    rolling_windows: tuple[int, ...] = (7, 28)
    rolling_std_windows: tuple[int, ...] = (7,)
    use_calendar: bool = True
    use_holidays: bool = True
    # Promotions are *planned*, so their value on a future target date is genuinely known at
    # forecast time and using it is not leakage. Defaults to off because it is only safe when the
    # caller can actually supply the promo calendar for the horizon; the serving path in `app.py`
    # leaves it off unless the request carries one. Using a promo value the deployed system will
    # not have would be leakage of the ordinary kind -- a feature available in training and absent
    # in production -- and it degrades quietly rather than erroring.
    use_promo: bool = False

    @property
    def max_lookback(self) -> int:
        """Longest history any feature reaches back for.

        Drives two things: how many warm-up rows to discard when training (rows whose lags are NaN),
        and the minimum series length at which the model may be used at all instead of the
        cold-start baseline.
        """
        return max((*self.lags, *self.rolling_windows, *self.rolling_std_windows), default=0)

    def feature_names(self) -> list[str]:
        """The feature columns, in the exact order the booster expects them.

        Derived from the config alone -- never from "which columns happen to be present in this
        frame". An earlier version inferred holiday features from the input frame, which produced 15
        features at training (where the raw data carried holiday flags) and 13 at serving (where the
        request did not), and LightGBM rejected the predict call. That is training/serving skew in
        its purest form, and the fix is that the *config* decides the schema and `build_features`
        guarantees the columns exist -- so the two paths cannot disagree.
        """
        names = [f"lag_{lag}" for lag in self.lags]
        names += [f"roll_mean_{w}" for w in self.rolling_windows]
        names += [f"roll_std_{w}" for w in self.rolling_std_windows]
        if self.use_calendar:
            names += ["dow", "month", "day_of_month", "is_weekend"]
        if self.use_holidays:
            names += ["is_holiday", "is_day_before_holiday"]
        if self.use_promo:
            names.append("promo_share")
        names += SERIES_KEYS
        return names


@dataclass
class CategoryEncoding:
    """Stable string -> integer code maps for the categorical features.

    Persisted with the model. Re-deriving codes at inference from whatever categories happen to be
    present in the request would renumber them (a request about one store would code it as 0), and
    the model would read that as a different store entirely. Unseen categories map to -1, which
    LightGBM treats as missing -- the right behaviour for a store the model has never seen, which
    should fall back on the pooled pattern rather than impersonate store 0.
    """

    maps: dict[str, dict[str, int]] = field(default_factory=dict)

    def fit(self, frame: pd.DataFrame, columns: list[str]) -> CategoryEncoding:
        for column in columns:
            values = sorted(frame[column].astype(str).unique())
            self.maps[column] = {value: code for code, value in enumerate(values)}
        return self

    def transform(self, frame: pd.DataFrame) -> pd.DataFrame:
        out = frame.copy()
        for column, mapping in self.maps.items():
            if column in out.columns:
                out[column] = out[column].astype(str).map(mapping).fillna(-1).astype("int32")
        return out

    def as_dict(self) -> dict[str, dict[str, int]]:
        return self.maps

    @classmethod
    def from_dict(cls, data: dict[str, dict[str, int]]) -> CategoryEncoding:
        return cls(maps={k: {str(kk): int(vv) for kk, vv in v.items()} for k, v in data.items()})


def ensure_dense_daily(frame: pd.DataFrame) -> pd.DataFrame:
    """Reindex every series onto a gap-free daily calendar, filling absent days with zero demand.

    Lags are *positional* once a frame is dense: lag_7 is "seven rows back" only if every day is
    present. On a sparse frame, `shift(7)` silently reaches back seven *observations*, which could
    be a month, and the feature stops meaning what its name says.

    Filling gaps with 0 encodes the Favorita convention that an absent row means no sales. It does
    conflate "closed" with "open but sold nothing" -- a real limitation, recorded in RESULTS.md
    rather than hidden, since it biases the model slightly toward under-forecasting around closures.
    """
    frame = frame.copy()
    frame[DATE] = pd.to_datetime(frame[DATE])

    pieces = []
    for keys, group in frame.groupby(SERIES_KEYS, observed=True):
        key_tuple = keys if isinstance(keys, tuple) else (keys,)
        full_range = pd.date_range(group[DATE].min(), group[DATE].max(), freq="D")

        dense = group.set_index(DATE).reindex(full_range).rename_axis(DATE).reset_index()
        for column, value in zip(SERIES_KEYS, key_tuple, strict=True):
            dense[column] = value
        dense[TARGET] = dense[TARGET].fillna(0.0)
        if "promo_share" in dense.columns:
            dense["promo_share"] = dense["promo_share"].fillna(0.0)
        for flag in ("is_holiday", "is_day_before_holiday"):
            if flag in dense.columns:
                dense[flag] = dense[flag].fillna(0).astype("int8")
        pieces.append(dense)

    return pd.concat(pieces, ignore_index=True).sort_values([*SERIES_KEYS, DATE], ignore_index=True)


def build_features(
    frame: pd.DataFrame,
    config: FeatureConfig | None = None,
    *,
    dropna: bool = True,
) -> pd.DataFrame:
    """Attach every model feature to a dense daily frame.

    `dropna=True` (training) discards warm-up rows whose longest lag is undefined. `dropna=False`
    (serving, and recursive multi-step prediction) keeps them: at inference we want a row for the
    target date even when some lag is missing, because LightGBM handles NaN natively by learning a
    default direction at each split. Dropping the row instead would mean returning no forecast at
    all for a short series -- which is what the cold-start baseline is for, chosen explicitly by the
    caller rather than by an accidental empty frame here.
    """
    config = config or FeatureConfig()
    out = frame.copy()
    out[DATE] = pd.to_datetime(out[DATE])
    out = out.sort_values([*SERIES_KEYS, DATE], ignore_index=True)

    grouped = out.groupby(SERIES_KEYS, observed=True)[TARGET]

    # --- lags: shift(k) is "k days ago" only because the frame is dense (see ensure_dense_daily)
    for lag in config.lags:
        out[f"lag_{lag}"] = grouped.shift(lag)

    # --- rolling statistics: SHIFT FIRST, THEN ROLL. `.shift(1).rolling(w)` covers d-w .. d-1 and
    #     excludes the target day. Rolling before shifting would include y(d) and leak the answer.
    shifted = grouped.shift(1)
    for window in config.rolling_windows:
        out[f"roll_mean_{window}"] = (
            shifted.groupby([out[k] for k in SERIES_KEYS], observed=True)
            .rolling(window, min_periods=1)
            .mean()
            .reset_index(level=list(range(len(SERIES_KEYS))), drop=True)
        )
    for window in config.rolling_std_windows:
        out[f"roll_std_{window}"] = (
            shifted.groupby([out[k] for k in SERIES_KEYS], observed=True)
            .rolling(window, min_periods=2)
            .std()
            .reset_index(level=list(range(len(SERIES_KEYS))), drop=True)
            .fillna(0.0)
        )

    if config.use_calendar:
        out["dow"] = out[DATE].dt.dayofweek.astype("int8")
        out["month"] = out[DATE].dt.month.astype("int8")
        out["day_of_month"] = out[DATE].dt.day.astype("int8")
        out["is_weekend"] = (out["dow"] >= 5).astype("int8")

    if config.use_holidays:
        for flag in ("is_holiday", "is_day_before_holiday"):
            if flag not in out.columns:
                out[flag] = 0
            out[flag] = out[flag].fillna(0).astype("int8")

    if dropna:
        longest_lag = f"lag_{max(config.lags)}" if config.lags else None
        if longest_lag:
            out = out.loc[out[longest_lag].notna()].reset_index(drop=True)

    return out


def feature_matrix(
    frame: pd.DataFrame,
    config: FeatureConfig,
    encoding: CategoryEncoding,
) -> tuple[pd.DataFrame, list[str]]:
    """Select and order feature columns, apply the categorical encoding.

    Column *order* is part of the contract with the booster: LightGBM matches features positionally
    once a Dataset is built. Deriving the order from `config.feature_names()` in both paths -- and
    returning it so the caller can persist it -- keeps train and serve from disagreeing.
    """
    names = config.feature_names()
    missing = [name for name in names if name not in frame.columns]
    if missing:
        raise ValueError(f"feature frame is missing columns: {missing}")

    encoded = encoding.transform(frame)
    return encoded[names], names
