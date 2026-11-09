"""The flagship family must get a figure, and the selection must not be able to flatter the model.

`pick_representative_series` spans the whole dataset by volume, which on Favorita lands on
groceries, stationery and homeware — every category except the one this system is for. These pin
the fix and the property that makes it defensible.
"""

import pandas as pd

from ecopan_ml.eval.plots import pick_family_series, pick_representative_series


def _predictions() -> pd.DataFrame:
    """Seven series, arranged so neither bakery series can be picked by volume alone.

    `pick_representative_series` takes positions 0, n//2 and n-1 of the volume ranking — here 0, 3
    and 6. Both bakery series sit at 1 and 4, so the whole-dataset pick provably misses the family.
    """
    rows = []
    for store, family, volume in [
        ("1", "GROCERY I", 1000),  # rank 0 — picked
        ("4", "BREAD/BAKERY", 900),  # rank 1 — the biggest bakery series
        ("2", "GROCERY I", 800),
        ("6", "GROCERY II", 700),  # rank 3 — picked (median)
        ("3", "BREAD/BAKERY", 600),
        ("5", "HOME AND KITCHEN", 500),
        ("7", "HOME AND KITCHEN", 400),  # rank 6 — picked (lowest)
    ]:
        for day in range(3):
            rows.append(
                {
                    "store_nbr": store,
                    "family": family,
                    "date": pd.Timestamp("2017-07-01") + pd.Timedelta(days=day),
                    "y_true": float(volume),
                    "y_pred": float(volume) * 0.9,
                }
            )
    return pd.DataFrame(rows)


def test_flagship_family_is_selected_even_when_it_is_not_top_volume():
    frame = _predictions()
    assert pick_family_series(frame, "BREAD/BAKERY") == [("4", "BREAD/BAKERY")]
    # ...and the whole-dataset pick genuinely would have missed it, which is why the call exists.
    assert ("4", "BREAD/BAKERY") not in pick_representative_series(frame)


def test_within_the_family_the_pick_is_by_volume_not_by_error():
    frame = _predictions()
    # Make the low-volume bakery series the one the model nails, and the high-volume one terrible.
    bakery = frame["family"] == "BREAD/BAKERY"
    frame.loc[bakery & (frame["store_nbr"] == "3"), "y_pred"] = frame["y_true"]
    frame.loc[bakery & (frame["store_nbr"] == "4"), "y_pred"] = 0.0

    # Still store 4: selection reads volume only, so it cannot be steered by the scores.
    assert pick_family_series(frame, "BREAD/BAKERY") == [("4", "BREAD/BAKERY")]


def test_unknown_family_yields_nothing_rather_than_falling_back():
    # Silently substituting another family would put a mislabelled chart in the defence pack.
    assert pick_family_series(_predictions(), "NO/SUCH/FAMILY") == []
