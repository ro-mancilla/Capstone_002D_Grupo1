"""Routing of unseen series (RESULTS.md §4.7).

`make transfer` failed the pre-registered bar on unseen, rescaled series, so the service sends any
series whose branch or product key is absent from the artifact's encoding to the labelled baseline,
however much history it has. These tests drive `_forecast_sync` with a stub booster so they hold
whether or not a real artifact is on disk.
"""

from datetime import date, timedelta

import numpy as np

from ecopan_ml.features.build import CategoryEncoding, FeatureConfig
from ecopan_ml.service.app import ModelBundle, _forecast_sync
from ecopan_ml.service.schemas import ForecastRequest, SeriesHistory

MODEL_VERSION = "lgbm-stub"


class StubBooster:
    best_iteration = None

    def predict(self, matrix, num_iteration=None):
        return np.full(len(matrix), 7.0)


def bundle() -> ModelBundle:
    known = {"store_nbr": {"known-branch": 0}, "family": {"known-type": 0}}
    return ModelBundle(
        booster=StubBooster(),
        config=FeatureConfig(),
        encoding=CategoryEncoding.from_dict(known),
        version=MODEL_VERSION,
    )


def series(branch: str, product: str, days: int = 60) -> SeriesHistory:
    start = date(2026, 1, 1)
    return SeriesHistory(
        branch_id=branch,
        product_type=product,
        history=[
            {"day": (start + timedelta(days=i)).isoformat(), "demand": 100.0 + i % 7}
            for i in range(days)
        ],
    )


def run(*batch: SeriesHistory) -> dict[tuple[str, str], str]:
    request = ForecastRequest(series=list(batch), start_day=date(2026, 3, 15), horizon_days=7)
    return {
        (f.branch_id, f.product_type): f.model_version for f in _forecast_sync(bundle(), request)
    }


def test_known_series_with_enough_history_uses_the_model():
    assert run(series("known-branch", "known-type")) == {
        ("known-branch", "known-type"): MODEL_VERSION
    }


def test_unseen_branch_goes_to_baseline_despite_long_history():
    labels = run(series("new-branch-uuid", "known-type"))
    assert labels[("new-branch-uuid", "known-type")].startswith("baseline")


def test_unseen_product_goes_to_baseline_despite_long_history():
    labels = run(series("known-branch", "new-product-id"))
    assert labels[("known-branch", "new-product-id")].startswith("baseline")


def test_batch_is_split_per_series():
    labels = run(series("known-branch", "known-type"), series("new-branch-uuid", "new-product-id"))
    assert labels[("known-branch", "known-type")] == MODEL_VERSION
    assert labels[("new-branch-uuid", "new-product-id")].startswith("baseline")
