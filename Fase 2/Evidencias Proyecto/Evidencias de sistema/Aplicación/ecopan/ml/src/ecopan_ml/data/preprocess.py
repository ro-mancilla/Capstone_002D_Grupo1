"""Favorita raw CSVs -> one dense daily parquet at the grain we actually forecast.

--- Why Favorita, and why the mapping is defensible ---------------------------------------------

The reference bakery has **no sales history at all**. That is not an inconvenience, it is a threat
to the entire evaluation: with no real data there is no legitimate test set, and any accuracy number
computed on data we invented is circular. Plan 08's answer is to prove model quality on real public
retail data and treat synthetic data as demo-only.

Corporación Favorita (Kaggle, 2013-2017) is the closest public match available:

  * **Daily grain**, store × item -- ours is daily, branch × product_type.
  * **Grocery retail in Ecuador** -- same continent, similar holiday calendar, and critically a
    `BREAD/BAKERY` family that is literally the product category we forecast.
  * **Real seasonality, real holidays, real promotions** -- the structure a bakery forecast must
    capture. Synthetic data can only contain the seasonality we programmed into it, which makes
    "the model recovered it" a tautology.

The domain mapping that carries the claim:

    Favorita                              ecopan
    ------------------------------------  --------------------------------------
    store_nbr                             branch
    family (items aggregated up)          product_type
    Σ unit_sales per (store, family, day) daily demand per (branch, product_type)
    onpromotion share                     (no ecopan analogue yet; see features/build.py)
    holidays_events                       holiday calendar

Aggregating items up to `family` is the step that makes the analogy tight. Per-item grocery demand
is far sparser and spikier than per-family; a bakery forecasting "how much bread tomorrow" is
operating at exactly the aggregated grain, not at the individual-SKU grain.

The honest limits, which belong in the defence rather than buried here: this is a supermarket chain,
not a bakery; volumes per series are larger; and a bakery's same-day perishability constraint has no
analogue in the data. The claim is therefore "the model learns daily retail demand structure of the
kind our client has", not "these exact error rates will hold at the client".
"""

from pathlib import Path

import pandas as pd

RAW_FILES = {
    "train": "train.csv",
    "items": "items.csv",
    "stores": "stores.csv",
    "holidays": "holidays_events.csv",
}

# Explicit dtypes for train.csv, and they are not a micro-optimisation -- without them the full
# 125M-row read does not fit in a laptop.
#
# Pandas infers int64/float64 for the numeric columns and, because `onpromotion` contains nulls,
# falls back to `object` for it: a column of 125M boxed Python objects at roughly 40 bytes each,
# ~5 GB for that column alone, on top of ~8 GB for four int64/float64 columns. The whole read lands
# north of 20 GB and dies before printing a single metric.
#
# Narrowed below to what the data actually needs: store_nbr is 1-54, item_nbr fits in int32,
# unit_sales is a count (float32 keeps ~7 significant digits, far beyond the precision of a sales
# figure), and `onpromotion` becomes pandas' nullable BooleanDtype -- one byte plus a mask, and it
# carries the nulls the competition data really has instead of silently coercing them. Together
# that is roughly a 5x cut, which is the difference between "runs" and "does not".
#
# `date` is deliberately absent: it is handled by parse_dates, and naming a column in both places
# is an error in pandas.
TRAIN_DTYPES = {
    "store_nbr": "int16",
    "item_nbr": "int32",
    "unit_sales": "float32",
    "onpromotion": "boolean",
}


def load_raw(raw_dir: Path, *, nrows: int | None = None) -> dict[str, pd.DataFrame]:
    """Read the competition CSVs.

    `nrows` exists because `train.csv` is ~125M rows: iterate on a slice, run the final numbers on
    the whole thing. The scope actually used is recorded in RESULTS.md, because a metric computed on
    a convenient subset is a different claim from one computed on the full set.
    """
    missing = [name for name, filename in RAW_FILES.items() if not (raw_dir / filename).exists()]
    if missing:
        raise FileNotFoundError(
            f"missing raw files {missing} in {raw_dir} -- "
            "run `make data` (needs Kaggle credentials)"
        )

    frames = {
        "train": pd.read_csv(
            raw_dir / RAW_FILES["train"],
            usecols=["date", "store_nbr", "item_nbr", "unit_sales", "onpromotion"],
            dtype=TRAIN_DTYPES,
            parse_dates=["date"],
            nrows=nrows,
        ),
        "items": pd.read_csv(raw_dir / RAW_FILES["items"]),
        "stores": pd.read_csv(raw_dir / RAW_FILES["stores"]),
        "holidays": pd.read_csv(raw_dir / RAW_FILES["holidays"], parse_dates=["date"]),
    }
    return frames


def build_holiday_calendar(holidays: pd.DataFrame) -> pd.DataFrame:
    """Collapse Favorita's holiday table into per-date flags.

    Two subtleties that matter and are easy to get wrong:

    `transferred == True` means the holiday was *moved* -- the nominal date is an ordinary working
    day and the celebration happens elsewhere. Treating it as a holiday teaches the model a demand
    spike on a normal day. Those rows are dropped; the corresponding `type == "Transfer"` rows carry
    the real date and are kept.

    `type == "Work Day"` is the inverse: a Saturday that was worked to compensate for a bridge day.
    Not a holiday at all, so it is excluded from the flag.

    `is_day_before_holiday` is computed as a separate flag because anticipatory demand is the larger
    effect in food retail -- people buy bread the day *before* the shops close.
    """
    calendar = holidays.loc[~holidays["transferred"].astype(bool)].copy()
    calendar = calendar.loc[calendar["type"] != "Work Day"]

    flags = (
        calendar[["date"]]
        .drop_duplicates()
        .assign(is_holiday=1)
        .sort_values("date", ignore_index=True)
    )
    day_before = flags[["date"]].assign(
        date=lambda df: df["date"] - pd.Timedelta(days=1), is_day_before_holiday=1
    )

    merged = flags.merge(day_before, on="date", how="outer").fillna(0)
    merged["is_holiday"] = merged["is_holiday"].astype("int8")
    merged["is_day_before_holiday"] = merged["is_day_before_holiday"].astype("int8")
    return merged


def aggregate_to_family_daily(
    train: pd.DataFrame,
    items: pd.DataFrame,
    *,
    families: list[str] | None = None,
    stores: list[int] | None = None,
) -> pd.DataFrame:
    """Item-level rows -> daily demand per (store_nbr, family).

    Two data-cleaning decisions, both of which change the numbers and so are stated rather than
    buried:

    **Negative `unit_sales` are returns, and are clipped to 0.** A return is not negative demand; it
    is a later correction to an earlier sale. Leaving them in would teach the model that some days
    have below-zero demand, which is not a thing a bakery can bake.

    **`onpromotion` nulls become False.** The column is genuinely absent for the earlier part of the
    competition period rather than merely unknown per-row. `promo_share` (the mean over items in a
    family) is consequently understated in that era, which is one reason `use_promo` defaults off.
    """
    merged = train.merge(items[["item_nbr", "family"]], on="item_nbr", how="left")

    if families:
        merged = merged.loc[merged["family"].isin(families)]
    if stores:
        merged = merged.loc[merged["store_nbr"].isin(stores)]

    merged["unit_sales"] = merged["unit_sales"].clip(lower=0.0)
    merged["onpromotion"] = merged["onpromotion"].fillna(False).astype(bool)

    aggregated = (
        merged.groupby(["store_nbr", "family", "date"], observed=True)
        .agg(demand=("unit_sales", "sum"), promo_share=("onpromotion", "mean"))
        .reset_index()
    )
    aggregated["store_nbr"] = aggregated["store_nbr"].astype(str)
    aggregated["family"] = aggregated["family"].astype(str)
    return aggregated


def preprocess(
    raw_dir: Path,
    output_path: Path,
    *,
    families: list[str] | None = None,
    stores: list[int] | None = None,
    nrows: int | None = None,
) -> pd.DataFrame:
    """Full raw -> modelling-ready parquet pipeline. Returns the frame it wrote."""
    frames = load_raw(raw_dir, nrows=nrows)

    aggregated = aggregate_to_family_daily(
        frames["train"], frames["items"], families=families, stores=stores
    )
    calendar = build_holiday_calendar(frames["holidays"])

    joined = aggregated.merge(calendar, on="date", how="left")
    joined["is_holiday"] = joined["is_holiday"].fillna(0).astype("int8")
    joined["is_day_before_holiday"] = joined["is_day_before_holiday"].fillna(0).astype("int8")

    # Densification happens here rather than at feature time so the parquet is already the honest
    # calendar, and so row counts logged below describe what the model actually sees.
    from ecopan_ml.features.build import ensure_dense_daily

    dense = ensure_dense_daily(joined)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    dense.to_parquet(output_path, index=False)
    return dense


def describe(frame: pd.DataFrame) -> str:
    """One-paragraph scope description, pasted into RESULTS.md so the numbers have their context."""
    n_series = frame.groupby(["store_nbr", "family"], observed=True).ngroups
    return (
        f"{len(frame):,} daily rows | {n_series:,} series "
        f"(store × family) | {frame['date'].min().date()} .. {frame['date'].max().date()} | "
        f"total demand {frame['demand'].sum():,.0f}"
    )
