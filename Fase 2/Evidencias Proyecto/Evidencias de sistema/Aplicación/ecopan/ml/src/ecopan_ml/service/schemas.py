"""Pydantic contract for `POST /forecast`.

This is the whole external surface of the ML service (plan 08 §5). The caller is
`backend:worker`, which on a `ForecastRefreshRequested` message reads the tenant's daily aggregates
straight out of Postgres, posts them here, and upserts the response into the `forecasts` table.
Clients and the dashboard never call this service -- they read the cached rows through the Kotlin
backend (architecture decision 01 §8).

That indirection is deliberate and worth being able to justify at the defence:

  * **One auth model.** Tenant/branch authorisation lives in the Kotlin backend; the ML service
    never has to reason about who may see which tenant's numbers. It authenticates one caller with
    one shared key and is otherwise oblivious to tenancy.
  * **Forecasting is slow and bursty; reads are fast and frequent.** Precomputing into a table keeps
    the dashboard's read path a simple indexed query, and a dead ML service degrades freshness
    rather than breaking the dashboard.
  * **Stateless service.** No database, no tenant state, no migrations. It is a pure function from
    history to forecast, which is also what makes it trivially testable.

--- Why history travels in the request ----------------------------------------------------------

The service could have been given database access instead. Passing history in the body keeps it a
pure function: the same payload always produces the same forecast, tests need no fixtures beyond
JSON, and the service can be scaled or replaced without touching data plumbing. The cost is request
size -- ~90 days × a few hundred series -- which is a few hundred KB of JSON, entirely acceptable
for a batch call that runs on a schedule.
"""

from datetime import date

from pydantic import BaseModel, Field, field_validator, model_validator

from ecopan_ml.features.build import FeatureConfig

# A series shorter than this is served by the cold-start baseline instead of the model (plan 08 §5).
#
# Derived, not chosen: it is the feature set's own longest lookback, so a series qualifies for the
# model exactly when every feature the model expects can be computed from real observations rather
# than warm-up NaNs. Tying it to the config means changing `lags` or `rolling_windows` moves the
# threshold with them instead of leaving a stale constant behind.
#
# This was 42 -- 28 plus two weeks of "margin for the rolling windows to be meaningful". The
# cold-start curve (`make coldstart`, reports/evaluation/coldstart_curve.json) measured that margin
# and found it buys nothing: validation WAPE is 0.1163 at 28 days and identical to four decimal
# places at 35, 42, 56, 90 and 180. The model already beats the cold-start baseline at 14 days
# (+24.3%) and reaches its plateau at 28 (+37.5%), so the old value withheld the model from a new
# series for two weeks longer than the data supports.
#
# Caveat worth carrying: that curve is Favorita, whose series are denser and higher-volume than a
# single bakery's. If the client's early data is noisy enough to argue for margin, this is the knob,
# and it should be moved with a measurement rather than a feeling.
MIN_HISTORY_FOR_MODEL = FeatureConfig().max_lookback

# Series whose branch or product key is absent from the artifact's encoding are served by the
# labelled baseline regardless of history length. Set by the pre-registered transfer experiment
# (RESULTS.md §4.7, `make transfer`): on unseen, gram-rescaled series the model scored WAPE 0.975
# against swlw's 0.192 (-408% gain), failing the 10% bar. Flip to False only when a later,
# pre-registered run of that experiment passes.
ROUTE_UNSEEN_TO_BASELINE = True


class HistoryPoint(BaseModel):
    """One observed day for one series."""

    day: date
    demand: float = Field(ge=0, description="Units or grams sold that day; never negative.")
    is_holiday: bool = False
    is_day_before_holiday: bool = False


class SeriesHistory(BaseModel):
    """One (branch, product_type) series and everything known about its past.

    `branch_id` and `product_type` are opaque strings here. The service does not interpret them --
    it forwards them onto the response so the worker can map predictions back onto its own rows.
    They are fed to the model as the categorical identity features, where an unseen value encodes to
    -1. Series with an unseen key are not sent to the model at all (`ROUTE_UNSEEN_TO_BASELINE`).
    """

    branch_id: str
    product_type: str
    history: list[HistoryPoint]

    @field_validator("history")
    @classmethod
    def _reject_duplicate_days(cls, value: list[HistoryPoint]) -> list[HistoryPoint]:
        days = [point.day for point in value]
        if len(days) != len(set(days)):
            raise ValueError("history contains duplicate days for one series")
        return value

    @property
    def observed_days(self) -> int:
        return len(self.history)


class CalendarDay(BaseModel):
    """One future day's calendar flags, shared by every series in the request.

    Holidays are a property of the date, not of the branch or product, so this is a flat list rather
    than something repeated per series.
    """

    day: date
    is_holiday: bool = False
    is_day_before_holiday: bool = False


class ForecastRequest(BaseModel):
    """A batch of series to forecast, and how far ahead."""

    series: list[SeriesHistory] = Field(min_length=1)
    # Optional, but supply it. The model is trained with real holiday flags; omitting them here
    # zero-fills the forecast horizon, which tells the model no upcoming day is a holiday and biases
    # exactly the days demand moves most. Optional only because a caller that genuinely has no
    # calendar should degrade rather than fail -- `HistoryPoint` already carries the same two flags
    # for observed days, and this is their counterpart for the days being predicted.
    calendar: list[CalendarDay] = Field(default_factory=list)
    horizon_days: int = Field(default=7, ge=1, le=14)
    # The first date to predict. Explicit rather than "today" so the call is deterministic and
    # replayable -- a service whose output depends on the wall clock cannot be regression-tested,
    # and the worker already knows which dates it wants to fill.
    start_day: date

    @model_validator(mode="after")
    def _horizon_beyond_recursive_comfort(self) -> ForecastRequest:
        # Not an error, just refusing to silently over-promise: past ~7 days a recursive one-step
        # model is extrapolating from its own predictions for most of the window (see
        # models/predict.py). Allowed up to 14 because the caller may knowingly want it.
        return self


class ForecastPoint(BaseModel):
    day: date
    predicted: float = Field(ge=0)


class SeriesForecast(BaseModel):
    """Predictions for one series, labelled with what produced them.

    `model_version` is the honesty mechanism. A row produced by the cold-start baseline is labelled
    `baseline-swlw`, not `lgbm-<sha>`, so the dashboard can say "estimate based on last week" rather
    than implying a trained model when there was not enough history to run one. The worker persists
    this string into the `forecasts` table's `model_version` column.
    """

    branch_id: str
    product_type: str
    model_version: str
    observed_days: int
    predictions: list[ForecastPoint]


class ForecastResponse(BaseModel):
    forecasts: list[SeriesForecast]
    generated_at: str
    service_model_version: str


class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    model_version: str
    min_history_for_model: int = MIN_HISTORY_FOR_MODEL
