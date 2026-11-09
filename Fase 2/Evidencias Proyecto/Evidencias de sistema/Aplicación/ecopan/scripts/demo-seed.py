#!/usr/bin/env python3
"""The two API-driven demo steps shared by scripts/demo-up.sh (local) and infra/deploy.sh (Azure).

    demo-seed.py opening-stock <api-base-url> <tenant-slug>
    demo-seed.py forecast      <api-base-url> <tenant-slug>

opening-stock  The ledger starts at zero and is append-only, so the first batch would read as
               *negative* stock on stage. One purchase per ingredient per branch fixes that.
               Movement ids are derived (uuid5) from branch+ingredient, so re-running never
               double-counts.
forecast       The reference seed ships with forecasting OFF (the entitlement demo toggles it
               live), so the forecast page would be hidden. Turn it on and queue a refresh per
               branch.
"""
import json
import os
import ssl
import sys
import time
import urllib.request
import uuid

KG = 1000
OPENING_GRAMS = {"Harina": 200 * KG, "Agua": 150 * KG, "Sal": 10 * KG,
                 "Levadura": 5 * KG, "Manteca": 20 * KG, "Azúcar": 20 * KG}


def _tls_context():
    """A python.org build of Python on macOS ships no CA bundle, so every https:// call fails with
    CERTIFICATE_VERIFY_FAILED (invisible against http://localhost). Fall back to the system bundle."""
    context = ssl.create_default_context()
    if not ssl.get_default_verify_paths().cafile or not os.path.exists(ssl.get_default_verify_paths().cafile):
        for cafile in ("/etc/ssl/cert.pem", "/etc/ssl/certs/ca-certificates.crt"):
            if os.path.exists(cafile):
                context.load_verify_locations(cafile)
                break
    return context


TLS = _tls_context()


def call(api, method, path, body=None, token=None):
    req = urllib.request.Request(api + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(req, timeout=30, context=TLS) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


def login(api, slug):
    return call(api, "POST", "/auth/login", {"tenantSlug": slug, "username": "gerente",
                                             "password": "gerente123", "deviceName": "demo-up.sh"})


def opening_stock(api, slug):
    session = login(api, slug)
    token = session["accessToken"]
    ingredients = call(api, "GET", "/inventory/ingredients", token=token)
    if isinstance(ingredients, dict):
        ingredients = ingredients.get("ingredients") or ingredients.get("items") or []
    opened = 0
    for branch in session["branches"]:
        for ing in ingredients:
            grams = OPENING_GRAMS.get(ing["name"])
            if not grams:
                continue
            call(api, "POST", "/inventory/movements", {
                "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "ecopan-demo-opening/%s/%s" % (branch["id"], ing["id"]))),
                "branchId": branch["id"], "ingredientId": ing["id"], "deltaGrams": grams,
                "kind": "PURCHASE", "note": "Stock inicial (demo)",
                "occurredAt": int((time.time() - 86400) * 1000),
            }, token)
            opened += 1
    print("  ✓ %d opening purchases (flour 200 kg, water 150 kg, ...) across %d branches"
          % (opened, len(session["branches"])))


def forecast(api, slug):
    session = login(api, slug)
    token = session["accessToken"]
    modules = list(session["tenant"]["enabledModules"])
    if "DEMAND_FORECASTING" not in modules:
        modules.append("DEMAND_FORECASTING")
        call(api, "PUT", "/tenant/modules", {"enabledModules": modules}, token)
        print("  ✓ DEMAND_FORECASTING enabled")
    else:
        print("  ✓ DEMAND_FORECASTING already enabled")
    for branch in session["branches"]:
        call(api, "POST", "/forecasts/refresh", {"branchId": branch["id"]}, token)
        print("  ✓ forecast refresh queued for", branch["name"])


STEPS = {"opening-stock": opening_stock, "forecast": forecast}

if __name__ == "__main__":
    if len(sys.argv) != 4 or sys.argv[1] not in STEPS:
        sys.exit(__doc__)
    STEPS[sys.argv[1]](sys.argv[2].rstrip("/"), sys.argv[3])
