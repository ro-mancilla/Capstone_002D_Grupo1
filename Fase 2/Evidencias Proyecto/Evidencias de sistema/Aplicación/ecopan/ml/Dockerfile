# ecopan ML inference service.
#
# uv-based, multi-stage. The build stage resolves dependencies into a virtualenv; the runtime stage
# copies only that venv and the source, so neither uv nor the build cache ships in the final image.

FROM ghcr.io/astral-sh/uv:python3.14-bookworm-slim AS build

# Compile to bytecode at install time rather than on first request, and copy packages into the venv
# instead of symlinking them (symlinks would dangle once we leave this stage behind).
ENV UV_COMPILE_BYTECODE=1 \
    UV_LINK_MODE=copy \
    UV_PYTHON_DOWNLOADS=never

WORKDIR /app

# Dependencies first, in their own layer keyed only on the lockfile: source edits then rebuild in
# seconds instead of re-resolving the whole dependency graph.
#
# --frozen fails the build if uv.lock is out of date with pyproject.toml, rather than silently
# resolving something different from what was tested. --no-dev drops ruff/pytest from the image.
COPY pyproject.toml uv.lock ./
# No `--mount=type=cache` on these two: it needs BuildKit, and `az acr build` (what infra/deploy.sh uses)
# runs the classic builder -- "the --mount option requires BuildKit". A cache mount only speeds up a repeat
# build on one machine; a registry build starts cold either way, so nothing is lost there.
RUN uv sync --frozen --no-install-project --no-dev

COPY src ./src
COPY configs ./configs
# README.md is not documentation ballast here: pyproject.toml declares `readme = "README.md"`, so
# the build backend reads it while building the project itself. Its absence does not fail the
# dependency-only sync above (--no-install-project never builds the package), only this one.
COPY README.md ./
RUN uv sync --frozen --no-dev

# --- Model artifact: fail loudly if it is missing -------------------------------------------------
#
# The trained model (data/artifacts: model.txt ~23 MB, metadata.json, encoding.json) is gitignored,
# so it exists only on a machine that has run `make train` (see ml/README.md "Shipping the model").
# A build that silently lacks it would deploy a service that quietly serves baselines only -- which is
# exactly how Azure ended up running baseline-only. So a missing or unloadable artifact fails the
# build, unless the caller says so on purpose:
#
#   docker build --build-arg ALLOW_BASELINE_ONLY=true ml      # deliberate baseline-only image;
#                                                              # never copies an artifact, even if present
#
# Derived from `build` so the check uses the real venv and the same LightGBM that serves: a truncated
# or version-incompatible model.txt fails here, not at 3 a.m. behind the broad except in
# service/app.py that would degrade it to baselines without a word.
FROM build AS model
ARG ALLOW_BASELINE_ONLY=false
# Same OpenMP runtime the runtime stage installs (see below): importing lightgbm needs it.
RUN apt-get update \
    && apt-get install --no-install-recommends -y libgomp1 \
    && rm -rf /var/lib/apt/lists/*
# The wildcard makes a missing directory match nothing instead of erroring (BuildKit), and
# pyproject.toml is a second source that is always present, because the legacy builder rejects a
# COPY whose only source matched nothing. It is deleted again below. The presence check is the RUN.
COPY pyproject.toml dat[a]/artifacts* /model/
RUN rm -f /model/pyproject.toml \
    && if [ "$ALLOW_BASELINE_ONLY" = "true" ]; then \
         echo "WARNING: BASELINE-ONLY image (ALLOW_BASELINE_ONLY=true); any artifact in the context is NOT copied"; \
         rm -rf /model && mkdir /model; \
       elif [ -f /model/model.txt ] && [ -f /model/metadata.json ] && [ -f /model/encoding.json ]; then \
         /app/.venv/bin/python -c "from pathlib import Path; from ecopan_ml.models.train import load_artifacts; \
_, meta, _ = load_artifacts(Path('/model')); print('model artifact OK:', meta.get('model_version'))"; \
       else \
         echo "ERROR: ml/data/artifacts/{model.txt,metadata.json,encoding.json} not found in the build context." >&2; \
         echo "  Train on this machine ('make train') or copy the artifact into ml/data/artifacts," >&2; \
         echo "  or build baseline-only on purpose with --build-arg ALLOW_BASELINE_ONLY=true." >&2; \
         exit 1; \
       fi

FROM python:3.14-slim-bookworm AS runtime

# LightGBM links against the OpenMP runtime. The manylinux wheel does not bundle it on Debian slim,
# so `import lightgbm` fails at startup without libgomp1 — the same class of failure as needing
# `brew install libomp` on macOS, and it surfaces only at runtime, not at build time.
RUN apt-get update \
    && apt-get install --no-install-recommends -y libgomp1 \
    && rm -rf /var/lib/apt/lists/*

# Non-root. Container Apps does not require it, but a service that never needs to write outside its
# own venv has no reason to run as root.
RUN useradd --create-home --uid 10001 appuser
USER appuser

WORKDIR /app
COPY --from=build --chown=appuser:appuser /app/.venv /app/.venv
COPY --from=build --chown=appuser:appuser /app/src /app/src
COPY --from=build --chown=appuser:appuser /app/configs /app/configs

ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    ML_ARTIFACT_DIR=/app/data/artifacts

# The trained model (~23 MB) is baked in from the `model` stage above; /health reports its version.
# With ALLOW_BASELINE_ONLY=true this directory is empty and the service starts in baseline-only mode
# (service/app.py), saying so on /health.
COPY --from=model --chown=appuser:appuser /model /app/data/artifacts

EXPOSE 8000

# fastapi CLI rather than a bare uvicorn invocation: `fastapi run` is the supported production
# entry point and already defaults to no reload and the right worker setup.
CMD ["fastapi", "run", "src/ecopan_ml/service/app.py", "--host", "0.0.0.0", "--port", "8000"]
