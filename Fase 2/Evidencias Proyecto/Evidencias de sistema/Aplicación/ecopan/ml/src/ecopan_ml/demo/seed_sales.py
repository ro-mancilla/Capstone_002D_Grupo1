"""Emit the demo demand curve as a backend seed resource — DEMO ONLY, NEVER EVALUATED ON.

Plan 11 SEED-2 wants "90 days of sales ... so dashboard KPIs and the forecast chart have something
to show". `generate.py` models daily demand per (branch, product_type); this module resolves that
against a seed profile's real branch/product ids and writes a file `SeedLoader` can ingest.

Everything `generate.py` says about quarantine applies unchanged, and is why this lives here rather
than in the backend: synthetic, never evaluated on, `DEMO DATA` header on the file itself.

--- What this file does and does NOT contain ----------------------------------------------------

It contains **daily item counts per (branch, product)** — roughly a thousand rows. It deliberately
does not contain individual tickets: exploding the same curve into sales and lines produced a 37 MB
resource and ~36k boot-time inserts, which is not something to commit or to run on every container
start.

So the split is: **Python owns the demand model, Kotlin owns transaction shaping.** `SeedLoader`
groups each day's items into baskets, prices them from the catalogue it already loaded, and batch
inserts. That also keeps money arithmetic on one side of the wire — the backend's integer-minor
rules (01 §5) are applied by the backend, not re-implemented here in floats.

--- Why absolute dates are still not stale ------------------------------------------------------

Storing day *offsets* and resolving them at boot looks fresher and is subtly wrong: weekday
seasonality dominates bakery retail (Saturday ≈ 1.45× Monday), so a curve generated without knowing
each row's weekday would put the Saturday peak on an arbitrary day.

Dates are therefore absolute and weekday-correct here, and `SeedLoader` slides the series forward by
a whole number of *weeks* at load time. A multiple of 7 preserves every row's weekday exactly, so
the peak stays on Saturday however long the file sits in git.
"""

import argparse
import json
from datetime import date, timedelta
from pathlib import Path

import numpy as np

from ecopan_ml.demo.generate import (
    DEMO_HEADER,
    WEEKDAY_MULTIPLIER,
    DemoConfig,
    _negative_binomial,
)

# A neighbourhood bakery, not a supermarket. `generate.py`'s defaults (320 loaves/day) are a
# plausible shop but explode into ~160 tickets/branch/day, which is a lot of rows to insert on every
# boot for no extra demonstrative value. These volumes still fill every chart and keep the seed
# fast. They are demo staging, not a claim about any real bakery.
DEMO_BASE_DEMAND = {"Pan": 95.0, "Pastelería": 28.0}


def _row_rng(*parts: object) -> np.random.Generator:
    """Seeded only by its key, so a row never depends on iteration order."""
    import hashlib

    digest = hashlib.sha256("|".join(str(p) for p in parts).encode()).digest()
    return np.random.default_rng(int.from_bytes(digest[:8], "big"))


def build_curve(profile: dict, config: DemoConfig, end: date) -> list[dict]:
    """`config.days` of per-(day, branch, product) item counts, ending on `end` inclusive."""
    branches = {b["name"]: b["id"] for b in profile["branches"]}
    products_by_type: dict[str, list[dict]] = {}
    for product in profile["products"]:
        products_by_type.setdefault(product["productType"], []).append(product)

    holidays = set(config.holidays)
    rows: list[dict] = []

    for offset in range(config.days):
        day = end - timedelta(days=config.days - 1 - offset)
        seasonal = 1.0 + config.seasonal_amplitude * np.sin(
            2 * np.pi * day.timetuple().tm_yday / 365.0
        )
        weekday = WEEKDAY_MULTIPLIER[day.weekday()]
        holiday = config.holiday_multiplier if day in holidays else 1.0

        for branch_name, branch_id in branches.items():
            for product_type, candidates in products_by_type.items():
                mean = (
                    config.base_demand.get(product_type, 60.0)
                    * config.branch_scale.get(branch_name, 1.0)
                    * weekday
                    * seasonal
                    * holiday
                )
                rng = _row_rng(branch_id, product_type, day)
                total = _negative_binomial(rng, mean, config.dispersion)
                if total <= 0:
                    continue

                # Split the type's demand across its products with fixed, uneven shares, so the
                # per-product breakdown on the sales page is not a flat line.
                share_rng = _row_rng("share", branch_id, product_type, day)
                weights = share_rng.dirichlet(np.full(len(candidates), 2.5))
                counts = np.floor(weights * total).astype(int)
                counts[0] += total - int(counts.sum())  # keep the day's total exact

                for product, count in zip(candidates, counts, strict=True):
                    if count > 0:
                        rows.append(
                            {
                                "day": day.isoformat(),
                                "branchId": branch_id,
                                "productId": product["id"],
                                "items": int(count),
                            }
                        )

    return rows


def write(rows: list[dict], output_path: Path, *, config: DemoConfig, end: date) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    document = {
        "_warning": DEMO_HEADER,
        "generator": "ecopan_ml.demo.seed_sales",
        "seed": config.seed,
        "days": config.days,
        "lastDay": end.isoformat(),
        "rows": rows,
    }
    output_path.write_text(json.dumps(document, ensure_ascii=False, indent=0), encoding="utf-8")
    return output_path


def main() -> None:
    parser = argparse.ArgumentParser(description=DEMO_HEADER)
    parser.add_argument(
        "--profile",
        type=Path,
        default=Path("../backend/app/src/main/resources/seed/reference.json"),
    )
    parser.add_argument(
        "--out",
        type=Path,
        default=Path("../backend/app/src/main/resources/seed/reference-sales.json"),
    )
    parser.add_argument("--days", type=int, default=120)
    parser.add_argument("--seed", type=int, default=DemoConfig.seed)
    parser.add_argument("--end", type=date.fromisoformat, default=date.today())
    args = parser.parse_args()

    profile = json.loads(args.profile.read_text(encoding="utf-8"))
    config = DemoConfig(days=args.days, seed=args.seed, base_demand=dict(DEMO_BASE_DEMAND))
    rows = build_curve(profile, config, args.end)
    path = write(rows, args.out, config=config, end=args.end)

    items = sum(r["items"] for r in rows)
    print(
        f"{path}: {len(rows)} rows, {items} items across {args.days} days ending {args.end} "
        f"({path.stat().st_size / 1024:.0f} KB)"
    )


if __name__ == "__main__":
    main()
