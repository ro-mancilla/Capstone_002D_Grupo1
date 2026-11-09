"""FastAPI inference service.

Two endpoints, no database, no tenant state (plan 08 §5). Everything interesting about this file is
in three places: how the model is loaded, how the per-series routing between model and baseline is
decided, and why prediction does not run on the event loop.
"""

import os
from contextlib import asynccontextmanager
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path

import pandas as pd
from fastapi import Depends, FastAPI, Header, HTTPException, status
from fastapi.concurrency import run_in_threadpool

from ecopan_ml.baselines.naive import cold_start_forecast
from ecopan_ml.features.build import SERIES_KEYS, CategoryEncoding, FeatureConfig
from ecopan_ml.models.predict import recursive_forecast
from ecopan_ml.models.train import load_artifacts
from ecopan_ml.service.schemas import (
    MIN_HISTORY_FOR_MODEL,
    ROUTE_UNSEEN_TO_BASELINE,
    CalendarDay,
    ForecastPoint,
    ForecastRequest,
    ForecastResponse,
    HealthResponse,
    SeriesForecast,
    SeriesHistory,
)

ARTIFACT_DIR = Path(os.environ.get("ML_ARTIFACT_DIR", "data/artifacts"))
API_KEY_ENV = "ML_API_KEY"


@dataclass
class ModelBundle:
    """The loaded model and everything needed to reproduce its input schema."""

    booster: object | None
    config: FeatureConfig
    encoding: CategoryEncoding
    version: str

    @property
    def loaded(self) -> bool:
        return self.booster is not None


def _baseline_only_bundle() -> ModelBundle:
    return ModelBundle(
        booster=None,
        config=FeatureConfig(),
        encoding=CategoryEncoding(),
        version="baseline-only",
    )


def _load_bundle(artifact_dir: Path) -> ModelBundle:
    """Load model + encoding + feature config from disk, or degrade to baseline-only.

    A missing artifact is deliberately **not** fatal. The service still starts and still answers
    `POST /forecast` using the cold-start baselines, and `/health` reports `model_loaded: false`.

    That is the correct failure mode for this system: the bakery's forecasts are an operational
    convenience, and a baseline forecast is strictly better than a 503. It also means the service is
    useful on day one of a deployment -- before any model has been trained on that tenant's data --
    which is exactly the cold-start story plan 08 §5 commits to.

    The `except Exception` is deliberate and was earned: the first version caught
    `(FileNotFoundError, OSError)`, which reads as obviously sufficient and is not -- LightGBM's
    loader raises its own `LightGBMError` for a missing file, so every request crashed with a 500
    on precisely the fresh-deployment path this fallback exists to serve. Any failure to load an
    artifact must degrade to baselines, whatever exception type the loader chose; a broad catch is
    the correct instrument when the goal is "never let this stop the service starting".
    """
    if not (artifact_dir / "model.txt").exists():
        return _baseline_only_bundle()

    try:
        booster, metadata, encoding = load_artifacts(artifact_dir)
    except Exception:
        return _baseline_only_bundle()

    config = FeatureConfig(
        **{
            key: tuple(value) if isinstance(value, list) else value
            for key, value in metadata.get("feature_config", {}).items()
        }
    )
    return ModelBundle(
        booster=booster,
        config=config,
        encoding=encoding,
        version=metadata.get("model_version", "lgbm"),
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Load the model once, at startup.

    The lifespan context manager is the current FastAPI pattern (the old `@app.on_event("startup")`
    is deprecated). Loading here rather than per-request matters more than it looks: deserialising a
    LightGBM booster costs tens of milliseconds and would otherwise be paid on every call, and
    holding one immutable booster for the process lifetime is safe because prediction does not
    mutate it.
    """
    app.state.bundle = _load_bundle(ARTIFACT_DIR)
    yield
    app.state.bundle = None


app = FastAPI(
    title="ecopan ML — demand forecasting",
    version="0.1.0",
    summary="7-day demand forecasts per (branch, product_type), LightGBM with baseline cold start.",
    lifespan=lifespan,
)


async def require_api_key(x_api_key: str | None = Header(default=None)) -> None:
    """Shared-secret auth for the single trusted caller (`backend:worker`, plan 08 §5 / 02 §7).

    A shared key rather than JWT because there is exactly one caller and no user identity involved:
    the service never makes an authorisation decision about a *person*, only about whether the
    request came from our backend. Adding JWT here would add a token lifecycle and a public-key
    fetch to buy nothing.

    If `ML_API_KEY` is unset the check is skipped, so local development and tests need no secret.
    That is a deliberate convenience with a sharp edge, so it is loud rather than silent: the
    deployment checklist in the README requires the variable, and `/health` does not report auth
    state to unauthenticated callers.
    """
    expected = os.environ.get(API_KEY_ENV)
    if not expected:
        return
    if x_api_key != expected:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid API key")


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    """Liveness plus what the service can actually do right now.

    Reports `model_loaded` rather than just "ok" because "up but serving baselines" is an
    operationally distinct state that a monitor should be able to see: the service is healthy, the
    forecasts are degraded.
    """
    bundle: ModelBundle = app.state.bundle
    return HealthResponse(
        status="ok",
        model_loaded=bundle.loaded,
        model_version=bundle.version,
    )


def _history_frame(series: SeriesHistory) -> pd.DataFrame:
    """One series' request payload -> the internal frame shape the pipeline expects.

    `branch_id`/`product_type` map onto `store_nbr`/`family`, which are the column names the model
    was trained with (Favorita's vocabulary). Renaming here, at the edge, keeps one vocabulary
    inside the pipeline instead of two.
    """
    return pd.DataFrame(
        [
            {
                "store_nbr": series.branch_id,
                "family": series.product_type,
                "date": pd.Timestamp(point.day),
                "demand": float(point.demand),
                "is_holiday": int(point.is_holiday),
                "is_day_before_holiday": int(point.is_day_before_holiday),
            }
            for point in series.history
        ]
    )


def _calendar_frame(calendar: list[CalendarDay]) -> pd.DataFrame | None:
    """Request calendar -> the frame `recursive_forecast` wants, or None if not supplied."""
    if not calendar:
        return None
    return pd.DataFrame(
        [
            {
                "date": pd.Timestamp(day.day),
                "is_holiday": int(day.is_holiday),
                "is_day_before_holiday": int(day.is_day_before_holiday),
            }
            for day in calendar
        ]
    )


def _is_unseen(bundle: ModelBundle, series: SeriesHistory) -> bool:
    """True when the branch or product key was not in the training data (encodes to -1)."""
    # branch_id/product_type are encoded under the training vocabulary's names (SERIES_KEYS).
    branch_key, product_key = SERIES_KEYS
    maps = bundle.encoding.maps
    return series.branch_id not in maps.get(branch_key, {}) or (
        series.product_type not in maps.get(product_key, {})
    )


def _model_eligible(bundle: ModelBundle, series: SeriesHistory) -> bool:
    if series.observed_days < MIN_HISTORY_FOR_MODEL:
        return False
    return not (ROUTE_UNSEEN_TO_BASELINE and _is_unseen(bundle, series))


def _forecast_sync(bundle: ModelBundle, request: ForecastRequest) -> list[SeriesForecast]:
    """The actual work. Synchronous on purpose -- see the endpoint's threadpool note.

    Routing is **per series, not per request**: a tenant will typically have some product types with
    years of history and some added last week, and the useful behaviour is to serve each with the
    best estimator it qualifies for rather than dropping the whole batch to baseline.
    """
    target_dates = pd.date_range(
        pd.Timestamp(request.start_day), periods=request.horizon_days, freq="D"
    )

    # A series qualifies for the model on history length AND on being known to the artifact.
    # Unseen series fail the transfer bar (RESULTS.md §4.7: the model collapses on the bakery's
    # gram-scale demand, WAPE 0.975 against the baseline's 0.192), so they are served by the
    # labelled baseline however long their history is.
    model_series = [s for s in request.series if _model_eligible(bundle, s)]
    baseline_series = [s for s in request.series if not _model_eligible(bundle, s)]

    if not bundle.loaded:
        # No artifact: everything falls back, and every row is labelled as a baseline so the
        # dashboard never implies a trained model produced it.
        baseline_series = list(request.series)
        model_series = []

    results: list[SeriesForecast] = []

    if model_series:
        # Batched: all model-eligible series go through one recursive forecast so each horizon step
        # is a single predict call over the whole batch (see models/predict.py).
        combined = pd.concat([_history_frame(s) for s in model_series], ignore_index=True)
        predicted = recursive_forecast(
            bundle.booster,
            combined,
            target_dates,
            bundle.config,
            bundle.encoding,
            model_version=bundle.version,
            future_calendar=_calendar_frame(request.calendar),
        )
        for series in model_series:
            rows = predicted.loc[
                (predicted["store_nbr"] == series.branch_id)
                & (predicted["family"] == series.product_type)
            ].sort_values("date")
            results.append(
                SeriesForecast(
                    branch_id=series.branch_id,
                    product_type=series.product_type,
                    model_version=bundle.version,
                    observed_days=series.observed_days,
                    predictions=[
                        ForecastPoint(day=row.date.date(), predicted=float(row.y_pred))
                        for row in rows.itertuples()
                    ],
                )
            )

    for series in baseline_series:
        frame = _history_frame(series)
        if frame.empty:
            # No history at all: nothing to forecast from, and inventing a number would be worse
            # than saying so. The worker leaves the row unfilled.
            results.append(
                SeriesForecast(
                    branch_id=series.branch_id,
                    product_type=series.product_type,
                    model_version="baseline-default",
                    observed_days=0,
                    predictions=[
                        ForecastPoint(day=day.date(), predicted=0.0) for day in target_dates
                    ],
                )
            )
            continue

        predicted = cold_start_forecast(frame, target_dates).sort_values("date")
        label = str(predicted["model_version"].iloc[0])
        results.append(
            SeriesForecast(
                branch_id=series.branch_id,
                product_type=series.product_type,
                model_version=label,
                observed_days=series.observed_days,
                predictions=[
                    ForecastPoint(day=row.date.date(), predicted=max(0.0, float(row.y_pred)))
                    for row in predicted.itertuples()
                ],
            )
        )

    return results


@app.post("/forecast", response_model=ForecastResponse, dependencies=[Depends(require_api_key)])
async def forecast(request: ForecastRequest) -> ForecastResponse:
    """Forecast every series in the batch.

    Declared `async` but the work is pushed to a threadpool, which is the important detail. LightGBM
    prediction is CPU-bound C++ that does not release control to the event loop; running it inline
    in an async endpoint would block the single event-loop thread and stall *every* concurrent
    request, including `/health`. `run_in_threadpool` hands it to a worker thread so the loop stays
    responsive.

    (The equivalent idiom is to declare the endpoint as plain `def`, which FastAPI automatically
    runs in a threadpool. Doing it explicitly here makes the reason visible rather than implicit.)
    """
    bundle: ModelBundle = app.state.bundle
    forecasts = await run_in_threadpool(_forecast_sync, bundle, request)

    return ForecastResponse(
        forecasts=forecasts,
        generated_at=datetime.now(UTC).isoformat(),
        service_model_version=bundle.version,
    )
