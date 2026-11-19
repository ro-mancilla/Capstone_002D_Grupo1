"""Enforces the demo/evaluation firewall (plan 08 §6).

Evaluating on synthetic data is circular, so plan 08 forbids it. A comment saying so is not
enforcement -- this file is. If someone later points the training config at the demo output, or
drops the warning header, the build fails instead of the defence.
"""

import json
from pathlib import Path

from ecopan_ml.config import load_config
from ecopan_ml.demo.generate import DEMO_HEADER, DemoConfig, generate, write

DEMO_DIR = Path("data/demo")


def test_training_paths_are_disjoint_from_demo_paths():
    """The config must never read from anywhere under `data/demo/`."""
    config = load_config("configs/favorita.yaml")

    demo_root = DEMO_DIR.resolve()
    for path in (config.dataset.raw_dir, config.dataset.processed_path):
        resolved = path.resolve()
        assert demo_root not in resolved.parents, f"training path {path} lives under the demo dir"
        assert resolved != demo_root


def test_generated_output_carries_the_quarantine_header(tmp_path: Path):
    config = DemoConfig(days=14)
    rows = generate(config)
    output = write(rows, tmp_path / "demo_sales.json", config=config)

    document = json.loads(output.read_text(encoding="utf-8"))
    assert document["_warning"] == DEMO_HEADER
    assert "NEVER for evaluation" in document["_warning"]


def test_generation_is_deterministic_for_a_given_seed():
    """The demo has to be rehearsable, and a seeded dataset regenerable rather than committed."""
    first = generate(DemoConfig(days=30, seed=99))
    second = generate(DemoConfig(days=30, seed=99))
    assert first == second

    different = generate(DemoConfig(days=30, seed=100))
    assert first != different


def test_generated_demand_is_non_negative_and_weekend_heavy():
    """Sanity-checks the generator's shape, not any model's accuracy.

    Saturday carries the largest weekday multiplier, so mean Saturday demand should exceed mean
    Tuesday demand. If that fails the generator is broken and the demo would show a flat, unhelpful
    chart.
    """
    import datetime as dt

    rows = generate(DemoConfig(days=364, seed=7))
    assert all(row["demand"] >= 0 for row in rows)

    def mean_for_weekday(weekday: int) -> float:
        values = [
            row["demand"] for row in rows if dt.date.fromisoformat(row["day"]).weekday() == weekday
        ]
        return sum(values) / len(values)

    assert mean_for_weekday(5) > mean_for_weekday(1)
