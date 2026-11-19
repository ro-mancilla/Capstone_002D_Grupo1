"""FastAPI contract tests, run against a service with no trained model.

Deliberately exercising the artifact-less path: it is the state a fresh deployment is in, and the
one the cold-start story depends on. A service that only works once someone has trained a model is a
service that cannot ship on day one.

Note what is NOT tested here, per plan 08 §8: model *quality*. These tests must stay green whatever
the metrics say. Asserting an accuracy floor in the test suite would mean a legitimately
disappointing result breaks the build, which is pressure in exactly the wrong direction.
"""

from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient

from ecopan_ml.service.app import app
from ecopan_ml.service.schemas import MIN_HISTORY_FOR_MODEL


@pytest.fixture
def client() -> TestClient:
    # The context-manager form runs the lifespan handler, which is what loads (or fails to load)
    # the model bundle. Constructing TestClient(app) bare would skip it and leave app.state unset.
    with TestClient(app) as test_client:
        yield test_client


def history_payload(days: int, *, start: date = date(2026, 1, 1)) -> list[dict]:
    return [
        {"day": (start + timedelta(days=offset)).isoformat(), "demand": 100.0 + (offset % 7) * 10}
        for offset in range(days)
    ]


def test_health_reports_whether_a_model_is_loaded(client: TestClient):
    response = client.get("/health")
    assert response.status_code == 200

    body = response.json()
    assert body["status"] == "ok"
    assert body["min_history_for_model"] == MIN_HISTORY_FOR_MODEL
    # "up but serving baselines" is a distinct operational state a monitor should be able to see.
    assert isinstance(body["model_loaded"], bool)


def test_forecast_returns_one_point_per_requested_day(client: TestClient):
    response = client.post(
        "/forecast",
        json={
            "start_day": "2026-03-01",
            "horizon_days": 7,
            "series": [
                {
                    "branch_id": "Sucursal Principal",
                    "product_type": "Pan",
                    "history": history_payload(90),
                }
            ],
        },
    )
    assert response.status_code == 200

    forecasts = response.json()["forecasts"]
    assert len(forecasts) == 1
    assert len(forecasts[0]["predictions"]) == 7
    assert forecasts[0]["predictions"][0]["day"] == "2026-03-01"


def test_short_history_is_labelled_as_a_baseline(client: TestClient):
    """The honesty mechanism: a cold-start row must never claim to be a model prediction."""
    response = client.post(
        "/forecast",
        json={
            "start_day": "2026-02-01",
            "horizon_days": 7,
            "series": [{"branch_id": "B1", "product_type": "Pan", "history": history_payload(10)}],
        },
    )
    assert response.status_code == 200

    forecast = response.json()["forecasts"][0]
    assert forecast["model_version"].startswith("baseline")
    assert forecast["observed_days"] == 10


def test_predictions_are_never_negative(client: TestClient):
    response = client.post(
        "/forecast",
        json={
            "start_day": "2026-02-01",
            "horizon_days": 7,
            "series": [{"branch_id": "B1", "product_type": "Pan", "history": history_payload(20)}],
        },
    )
    assert all(p["predicted"] >= 0 for p in response.json()["forecasts"][0]["predictions"])


def test_mixed_batch_routes_each_series_independently(client: TestClient):
    """A tenant has old product types and new ones; each should get the best estimator it qualifies
    for, rather than the whole batch dropping to baseline because one series is young."""
    response = client.post(
        "/forecast",
        json={
            "start_day": "2026-06-01",
            "horizon_days": 7,
            "series": [
                {"branch_id": "B1", "product_type": "Pan", "history": history_payload(120)},
                {"branch_id": "B1", "product_type": "Nuevo", "history": history_payload(5)},
            ],
        },
    )
    assert response.status_code == 200

    by_type = {f["product_type"]: f for f in response.json()["forecasts"]}
    assert len(by_type) == 2
    assert by_type["Nuevo"]["model_version"].startswith("baseline")
    assert all(len(f["predictions"]) == 7 for f in by_type.values())


def test_duplicate_days_in_history_are_rejected(client: TestClient):
    """Duplicates would silently corrupt the dense-calendar reindex and therefore every lag."""
    response = client.post(
        "/forecast",
        json={
            "start_day": "2026-02-01",
            "horizon_days": 7,
            "series": [
                {
                    "branch_id": "B1",
                    "product_type": "Pan",
                    "history": [
                        {"day": "2026-01-01", "demand": 10},
                        {"day": "2026-01-01", "demand": 12},
                    ],
                }
            ],
        },
    )
    assert response.status_code == 422


def test_negative_demand_is_rejected(client: TestClient):
    response = client.post(
        "/forecast",
        json={
            "start_day": "2026-02-01",
            "horizon_days": 7,
            "series": [
                {
                    "branch_id": "B1",
                    "product_type": "Pan",
                    "history": [{"day": "2026-01-01", "demand": -5}],
                }
            ],
        },
    )
    assert response.status_code == 422


def test_empty_series_list_is_rejected(client: TestClient):
    response = client.post(
        "/forecast", json={"start_day": "2026-02-01", "horizon_days": 7, "series": []}
    )
    assert response.status_code == 422


def test_api_key_is_enforced_when_configured(monkeypatch):
    monkeypatch.setenv("ML_API_KEY", "secret-key")
    with TestClient(app) as client:
        payload = {
            "start_day": "2026-02-01",
            "horizon_days": 7,
            "series": [{"branch_id": "B1", "product_type": "Pan", "history": history_payload(20)}],
        }
        assert client.post("/forecast", json=payload).status_code == 401
        assert (
            client.post("/forecast", json=payload, headers={"x-api-key": "secret-key"}).status_code
            == 200
        )
