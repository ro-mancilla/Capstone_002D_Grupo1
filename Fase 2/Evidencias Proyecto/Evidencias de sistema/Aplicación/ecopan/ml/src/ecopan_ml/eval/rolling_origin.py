"""Rolling-origin evaluation: the protocol that makes the comparison meaningful.

--- Why not a random train/test split ----------------------------------------------------------

The reflex from tabular ML -- shuffle rows, hold out 20% -- is invalid for time series and would
silently inflate every number in RESULTS.md. Two distinct failures:

1.  *Lookahead leakage.* A randomly held-out Tuesday sits between two training days. Any feature
    built from neighbouring days (a rolling mean, a lag from the other side) lets the model see the
    future of the very day it is scoring. Even without explicit future features, the model learns
    the level of that specific week from its neighbours.

2.  *It answers the wrong question.* We never have to interpolate a missing Tuesday in the middle
    of known history. We always have to extrapolate forward from today into a week nobody has
    observed. Interpolation is a strictly easier problem, so a random split measures a skill the
    deployed system will never exercise.

So splits are strictly temporal: train ≤ T₀ < validation < test, no shuffling anywhere.

--- Why one final holdout is still not enough --------------------------------------------------

A single train/test cut gives exactly one forecast origin. Whether that particular week happened to
be calm or chaotic then dominates the result, and with one number there is no way to tell skill
from luck.

Rolling-origin evaluation (also called walk-forward, or time-series cross-validation) instead
sweeps the origin across the test window:

    origin o₁:  ... history ... | predict d+1 .. d+7
    origin o₂:  ...... history ...... | predict d+8 .. d+14
    origin o₃:  ......... history ......... | predict d+15 .. d+21

Each origin yields a full horizon of predictions; metrics aggregate over all origins × all series.
That is many more scored points, spread across different weeks, so the estimate is far less
hostage to one lucky window -- and the spread across origins is itself reportable evidence of
stability.

--- Refit-free, and why that is the honest choice ----------------------------------------------

Strict walk-forward would retrain the model at every origin. We deliberately do not, and say so:

  * It mirrors deployment. The production model is trained periodically and serves for days or
    weeks; scoring a freshly-retrained model at every origin would measure a system we do not run.
  * It is the *conservative* direction. A refit-at-every-origin model would see more recent data
    and score better. By not refitting, any margin over the baseline is if anything understated --
    the safe direction for a claim we intend to defend.

The baselines recompute trivially at each origin (they are pure functions of recent history), which
is not a favour to them or to us: it is simply what those estimators are. Both are driven through
*this same splitter and this same scoring code*, which is the point -- plan 08 §3's rule that
numbers from different evaluation code paths are never compared. Most reported "model beats
baseline" results die right here, on the baseline having been evaluated slightly differently.
"""

from collections.abc import Iterator
from dataclasses import dataclass

import pandas as pd


@dataclass(frozen=True)
class Origin:
    """One forecast origin: everything strictly before `first_target_date` may be used as history.

    `index` exists so per-origin metrics can be reported in order, making a degradation over
    successive origins (a model going stale) visible instead of averaged away.
    """

    index: int
    first_target_date: pd.Timestamp
    last_target_date: pd.Timestamp

    @property
    def horizon(self) -> int:
        return int((self.last_target_date - self.first_target_date).days) + 1

    def __str__(self) -> str:
        return (
            f"origin[{self.index}] "
            f"{self.first_target_date.date()}..{self.last_target_date.date()} (h={self.horizon})"
        )


def generate_origins(
    *,
    test_start: pd.Timestamp,
    test_end: pd.Timestamp,
    horizon: int = 7,
    step: int = 7,
) -> list[Origin]:
    """Origins tiling [test_start, test_end].

    With the defaults (horizon 7, step 7) the origins partition the window into consecutive
    non-overlapping weeks: every test day is scored exactly once, at a horizon between 1 and 7 days
    out. Setting step < horizon makes windows overlap, which scores each day several times at
    different horizons -- useful for studying error growth with horizon, but it double-counts days
    in a pooled metric, so the default keeps them disjoint.

    A trailing partial window (fewer than `horizon` days left) is dropped rather than scored short.
    Including it would mix horizons into the pooled average and quietly make the last origin look
    better, since near-horizon days are easier.
    """
    if horizon < 1 or step < 1:
        raise ValueError("horizon and step must be >= 1")
    if test_end < test_start:
        raise ValueError("test_end precedes test_start")

    origins: list[Origin] = []
    cursor = pd.Timestamp(test_start)
    index = 0

    while True:
        last = cursor + pd.Timedelta(days=horizon - 1)
        if last > test_end:
            break
        origins.append(Origin(index=index, first_target_date=cursor, last_target_date=last))
        cursor = cursor + pd.Timedelta(days=step)
        index += 1

    if not origins:
        raise ValueError(
            f"test window {test_start.date()}..{test_end.date()} is shorter than horizon={horizon}"
        )

    return origins


def iter_origin_slices(
    frame: pd.DataFrame,
    origins: list[Origin],
    *,
    date_col: str = "date",
) -> Iterator[tuple[Origin, pd.DataFrame, pd.DataFrame]]:
    """Yield `(origin, history, target)` per origin.

    `history` is every row *strictly before* the origin's first target date -- the single line of
    code that enforces no-lookahead for every estimator in this project. Both the baselines and the
    recursive LightGBM predictor receive exactly this frame and nothing else, so neither can
    accidentally read the future: the restriction is applied here, once, rather than trusted to
    each estimator.

    `target` is the actuals for the horizon, used only for scoring, never passed to an estimator.
    """
    dates = pd.to_datetime(frame[date_col])

    for origin in origins:
        history_mask = dates < origin.first_target_date
        target_mask = (dates >= origin.first_target_date) & (dates <= origin.last_target_date)
        yield origin, frame.loc[history_mask], frame.loc[target_mask]


def temporal_split(
    frame: pd.DataFrame,
    *,
    train_end: str | pd.Timestamp,
    val_end: str | pd.Timestamp,
    test_end: str | pd.Timestamp,
    date_col: str = "date",
) -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame]:
    """Split into train / validation / test by date boundaries (all inclusive of their end).

    The three-way split exists to keep the test window genuinely untouched. Validation absorbs
    everything that involves *looking* at results -- early stopping, hyperparameter choice, feature
    selection, and (per plan 08 §3) picking the reference baseline and the success threshold. Test
    is scored once, at the end.

    This matters more than it sounds. Every time a decision is made by looking at a score, that
    score's window is partly consumed: the choice adapts to its noise. Reuse the test set for
    tuning and its number stops estimating generalisation and starts estimating how hard we tuned.
    Two windows keep those roles apart, and the git history of RESULTS.md is the evidence that the
    threshold was fixed before the test window was ever scored.
    """
    dates = pd.to_datetime(frame[date_col])
    train_end, val_end, test_end = (pd.Timestamp(x) for x in (train_end, val_end, test_end))

    if not (train_end < val_end < test_end):
        raise ValueError(
            "split boundaries must increase: "
            f"{train_end.date()} < {val_end.date()} < {test_end.date()}"
        )

    train = frame.loc[dates <= train_end]
    validation = frame.loc[(dates > train_end) & (dates <= val_end)]
    test = frame.loc[(dates > val_end) & (dates <= test_end)]

    for name, part in (("train", train), ("validation", validation), ("test", test)):
        if part.empty:
            raise ValueError(f"{name} split is empty -- check split dates against the data range")

    return train, validation, test
