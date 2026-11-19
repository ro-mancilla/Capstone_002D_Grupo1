"""Data transforms for the pre-registered transfer experiment (RESULTS.md §4.7).

The question: does the shipped model's gain over `baseline-swlw` survive the bakery's situation --
series whose branch/product keys the model has never seen, counted in a much larger unit (grams)?

Everything tunable is a module constant fixed by the pre-registration commit, so a reader can see
the protocol is not a parameter sweep: the seed, the number of held-out stores and the scale factor
were all written into RESULTS.md before this module existed.
"""

from collections.abc import Iterable
from dataclasses import dataclass

import numpy as np
import pandas as pd

from ecopan_ml.features.build import SERIES_KEYS, TARGET

TRANSFER_SEED = 50
N_HELD_OUT_STORES = 10
# One Favorita unit read as a 500 g loaf. Arbitrary and gram-like by design; see RESULTS.md §4.7.
SCALE_FACTOR = 500.0
UNSEEN_PREFIX = "unseen"


@dataclass(frozen=True)
class TransferArm:
    """One cell of the 2x2 design: are the keys unseen, and is demand rescaled."""

    name: str
    unseen: bool
    scale: float


ARMS = (
    TransferArm("A_control", unseen=False, scale=1.0),
    TransferArm("B_unseen_codes", unseen=True, scale=1.0),
    TransferArm("C_rescaled", unseen=False, scale=SCALE_FACTOR),
    TransferArm("D_unseen_rescaled", unseen=True, scale=SCALE_FACTOR),
)
BINDING_ARM = "D_unseen_rescaled"


def pick_holdout_stores(stores: Iterable[object], *, n: int, seed: int) -> list:
    """Draw `n` distinct stores reproducibly. Input order must not influence the draw."""
    ordered = sorted(set(stores))
    rng = np.random.default_rng(seed)
    chosen = rng.choice(len(ordered), size=n, replace=False)
    return sorted(ordered[i] for i in chosen)


def apply_arm(frame: pd.DataFrame, stores: Iterable[object], arm: TransferArm) -> pd.DataFrame:
    """Restrict to `stores`, then (optionally) mask the keys and rescale demand.

    Masking prefixes both keys with a string that is absent from any fitted encoding, so
    `CategoryEncoding.transform` maps them to -1 -- what a branch UUID / product id does at serving.
    The prefix keeps distinct series distinct, so the harness still scores them separately.
    """
    wanted = {str(s) for s in stores}
    out = frame.loc[frame[SERIES_KEYS[0]].astype(str).isin(wanted)].copy()
    if arm.unseen:
        for key in SERIES_KEYS:
            out[key] = f"{UNSEEN_PREFIX}-" + out[key].astype(str)
    out[TARGET] = out[TARGET] * arm.scale
    return out.reset_index(drop=True)
