# ecopan ML pipeline. Every target runs through `uv run` so there is no "activate the venv first"
# step and no ambiguity about which interpreter produced a result.
#
# The target ORDER encodes plan 08 §3's anti-target-shopping discipline:
#
#   data -> baselines -> [COMMIT RESULTS.md] -> train -> evaluate -> plots
#                         ^^^^^^^^^^^^^^^^^^
#   The threshold is fixed and committed after seeing BASELINE numbers and before seeing any MODEL
#   number. The git timestamp of that commit is the evidence. `evaluate` refuses to run if the
#   reference baseline was never recorded.

.DEFAULT_GOAL := help
PY := uv run

.PHONY: help install data baselines train evaluate backtest plots serve demo-data seed-sales ablation coldstart family transfer lint fmt test check clean

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

install: ## Create the venv and install everything (including dev tools)
	uv sync

data: ## Download Favorita from Kaggle and preprocess -> parquet  (needs Kaggle credentials)
	uv sync --extra data
	$(PY) python -m ecopan_ml.data.download --raw-dir data/raw
	$(PY) python -m ecopan_ml.pipeline data

baselines: ## Score both baselines on VALIDATION and pick the reference  (run before train)
	$(PY) python -m ecopan_ml.pipeline baselines
	@echo ""
	@echo "  ^ Record these numbers and your chosen threshold in RESULTS.md,"
	@echo "    set threshold.reference_baseline in configs/favorita.yaml, and COMMIT"
	@echo "    before running 'make train'."

train: ## Fit LightGBM with early stopping on validation
	$(PY) python -m ecopan_ml.pipeline train

ablation: ## Which feature groups carry the gain — scored on VALIDATION, never the test window
	$(PY) python -m ecopan_ml.pipeline ablation

coldstart: ## Accuracy vs available history — justifies MIN_HISTORY_FOR_MODEL (validation only)
	$(PY) python -m ecopan_ml.pipeline coldstart

family: ## Flagship-family metrics, recomputed from committed predictions (no re-scoring)
	$(PY) python -m ecopan_ml.pipeline family

backtest: ## Walk-forward robustness study across seasonal positions (retrains per fold)
	$(PY) python -m ecopan_ml.pipeline backtest
	@echo ""
	@echo "Robustness study, NOT the pre-registered claim. Retrains per fold; several minutes."

transfer: ## Unseen + rescaled series vs baseline-swlw — pre-registered RESULTS §4.7 (validation only)
	$(PY) python -m ecopan_ml.pipeline transfer

evaluate: ## Score baselines + model on the UNTOUCHED test window and state the verdict
	$(PY) python -m ecopan_ml.pipeline evaluate

plots: ## Render the defence figures into reports/plots/
	$(PY) python -m ecopan_ml.pipeline plots

serve: ## Run the inference API on :8000 with reload
	$(PY) fastapi dev src/ecopan_ml/service/app.py --port 8000

serve-prod: ## Run the inference API without reload (what the container runs)
	$(PY) fastapi run src/ecopan_ml/service/app.py --port 8000

demo-data: ## Generate synthetic demo sales — DEMO ONLY, never evaluated on
	$(PY) python -m ecopan_ml.demo.generate --out data/demo/demo_sales.json

seed-sales: ## Regenerate the backend's demo sales curve (seed/reference-sales.json)
	$(PY) python -m ecopan_ml.demo.seed_sales

lint: ## ruff check + format check
	$(PY) ruff check .
	$(PY) ruff format --check .

fmt: ## Apply ruff formatting and autofixes
	$(PY) ruff check --fix .
	$(PY) ruff format .

test: ## Run the test suite
	$(PY) pytest

check: lint test ## What CI runs

clean: ## Remove generated artifacts (never touches raw data)
	rm -rf data/processed data/artifacts reports/plots .pytest_cache
