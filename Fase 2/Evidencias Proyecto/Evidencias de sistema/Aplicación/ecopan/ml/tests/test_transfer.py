"""The transfer experiment's data transforms (RESULTS.md §4.7).

These pin the *construction* of the arms, not the model's score: the pre-registered protocol says
"unseen codes" and "x500", and a transform that quietly did something else would make the published
number a measurement of the wrong thing.
"""

import pandas as pd

from ecopan_ml.eval.transfer import (
    N_HELD_OUT_STORES,
    SCALE_FACTOR,
    TRANSFER_SEED,
    TransferArm,
    apply_arm,
    pick_holdout_stores,
)
from ecopan_ml.features.build import CategoryEncoding


def frame() -> pd.DataFrame:
    rows = []
    for store in (1, 2, 3):
        for family in ("BREAD", "DAIRY"):
            for day in pd.date_range("2024-01-01", periods=3):
                rows.append(
                    {"store_nbr": store, "family": family, "date": day, "demand": 2.0 * store}
                )
    return pd.DataFrame(rows)


def test_holdout_is_seeded_sorted_and_distinct():
    stores = list(range(1, 55))
    first = pick_holdout_stores(stores, n=N_HELD_OUT_STORES, seed=TRANSFER_SEED)
    again = pick_holdout_stores(reversed(stores), n=N_HELD_OUT_STORES, seed=TRANSFER_SEED)

    assert first == again  # input order must not change the draw
    assert first == sorted(first)
    assert len(set(first)) == N_HELD_OUT_STORES == 10


def test_holdout_matches_the_stores_actually_run():
    """Pins RESULTS §4.7's published set, drawn over string keys like the data."""
    stores = [str(n) for n in range(1, 55)]
    assert pick_holdout_stores(stores, n=N_HELD_OUT_STORES, seed=TRANSFER_SEED) == [
        "20", "34", "4", "41", "42", "46", "49", "53", "6", "9",
    ]  # fmt: skip


def test_arm_restricts_to_held_out_stores_only():
    out = apply_arm(frame(), [2], TransferArm("A", unseen=False, scale=1.0))
    assert set(out["store_nbr"].astype(str)) == {"2"}
    assert len(out) == 6


def test_unseen_arm_codes_encode_to_minus_one_and_seen_do_not():
    encoding = CategoryEncoding().fit(frame(), ["store_nbr", "family"])

    seen = encoding.transform(apply_arm(frame(), [1], TransferArm("A", unseen=False, scale=1.0)))
    unseen = encoding.transform(apply_arm(frame(), [1], TransferArm("D", unseen=True, scale=1.0)))

    assert (seen["store_nbr"] >= 0).all()
    assert (seen["family"] >= 0).all()
    assert (unseen["store_nbr"] == -1).all()
    assert (unseen["family"] == -1).all()


def test_unseen_keeps_series_distinct():
    out = apply_arm(frame(), [1, 2], TransferArm("D", unseen=True, scale=1.0))
    assert out.groupby(["store_nbr", "family"]).ngroups == 4


def test_scale_multiplies_demand_and_nothing_else():
    original = apply_arm(frame(), [3], TransferArm("A", unseen=False, scale=1.0))
    scaled = apply_arm(frame(), [3], TransferArm("C", unseen=False, scale=SCALE_FACTOR))

    assert SCALE_FACTOR == 500
    pd.testing.assert_series_equal(scaled["demand"], original["demand"] * 500)
    pd.testing.assert_series_equal(scaled["date"], original["date"])


def test_input_frame_is_not_mutated():
    source = frame()
    snapshot = source.copy()
    apply_arm(source, [1], TransferArm("D", unseen=True, scale=500.0))
    pd.testing.assert_frame_equal(source, snapshot)
