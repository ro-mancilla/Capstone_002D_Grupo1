"""`/health` is how a deploy proves the image really carries the model (infra/deploy.sh checks it).

Both states must be unambiguous: a loaded model reports its version, an absent or unloadable one
reports `baseline-only` -- never a stale or empty string a script could mistake for success.
"""

from fastapi.testclient import TestClient

from ecopan_ml.service import app as service


def test_missing_artifact_reports_baseline_only(tmp_path, monkeypatch):
    monkeypatch.setattr(service, "ARTIFACT_DIR", tmp_path)
    with TestClient(service.app) as client:
        body = client.get("/health").json()
    assert body["model_loaded"] is False
    assert body["model_version"] == "baseline-only"


def test_unloadable_artifact_reports_baseline_only(tmp_path, monkeypatch):
    (tmp_path / "model.txt").write_text("not a lightgbm model")
    monkeypatch.setattr(service, "ARTIFACT_DIR", tmp_path)
    with TestClient(service.app) as client:
        body = client.get("/health").json()
    assert body["model_loaded"] is False
    assert body["model_version"] == "baseline-only"
