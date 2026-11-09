#!/usr/bin/env bash
#
# Acceptance check for a running ecopan stack -- local compose or Azure, same script.
#
#   infra/verify.sh http://localhost:8080
#   infra/verify.sh https://ecopan-app.azurewebsites.net \
#       --web-url https://<swa>.azurestaticapps.net --expect-products 10 --expect-photos 7
#
# Black box: HTTP only. Every check runs even after one fails, so a single run lists everything that
# is wrong; the exit status is non-zero if any check failed. What it proves is what a demo depends
# on -- the same state scripts/demo-up.sh leaves the local stack in:
#
#   health, logins for the three seeded roles, catalog (counts, photo URLs resolve), sales history,
#   opening stock per branch, Almacén product stock per branch, DEMAND_FORECASTING on, forecast rows,
#   (optionally) the ML service's model, the dashboard + CORS, and finally scripts/verify-sse.sh.
#
# Flags:
#   --tenant SLUG           default reference
#   --web-url URL           also check the dashboard loads and the API answers its CORS preflight
#   --ml-url URL            also check GET /health of the ML service (must be reachable from here:
#                           on Azure it is IP-restricted to the plan, so this is off by default)
#   --expect-ml-model X     true | false | any   (default any; needs --ml-url)
#   --expect-products N     exact catalog size (default: at least 1)
#   --expect-photos N       exact number of products with a photo (default: not checked)
#   --forecast-wait SECS    how long to wait for forecast rows (default 120; the worker is async)
#   --no-sse                skip scripts/verify-sse.sh (it records one real 10 kg batch)
#   --own-storage MARKER    substring identifying photo URLs on this stack's own storage (default
#                           "blob.core.windows.net" or ":10000/"); a broken one fails, a broken external one warns
#
# Environment: MANAGER / MANAGER_PASSWORD, CASHIER / CASHIER_PASSWORD, BAKER / BAKER_PASSWORD
# (gerente/gerente123, cajero/cajero123, panadero/panadero123).
#
# Needs: python3 (stdlib only), curl for the SSE step.

set -Eeuo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

[[ $# -ge 1 && "$1" != -* ]] || { sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
API="${1%/}"; shift

TENANT=reference WEB_URL="" ML_URL="" EXPECT_ML=any EXPECT_PRODUCTS="" EXPECT_PHOTOS="" FORECAST_WAIT=120 RUN_SSE=true OWN_STORAGE=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --tenant) TENANT="$2"; shift 2 ;;
    --web-url) WEB_URL="${2%/}"; shift 2 ;;
    --ml-url) ML_URL="${2%/}"; shift 2 ;;
    --expect-ml-model) EXPECT_ML="$2"; shift 2 ;;
    --expect-products) EXPECT_PRODUCTS="$2"; shift 2 ;;
    --expect-photos) EXPECT_PHOTOS="$2"; shift 2 ;;
    --forecast-wait) FORECAST_WAIT="$2"; shift 2 ;;
    --own-storage) OWN_STORAGE="$2"; shift 2 ;;
    --no-sse) RUN_SSE=false; shift ;;
    -h|--help) sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done
command -v python3 >/dev/null || { echo "python3 not found" >&2; exit 2; }

export API TENANT WEB_URL ML_URL EXPECT_ML EXPECT_PRODUCTS EXPECT_PHOTOS FORECAST_WAIT OWN_STORAGE
export MANAGER="${MANAGER:-gerente}" MANAGER_PASSWORD="${MANAGER_PASSWORD:-gerente123}"
export CASHIER="${CASHIER:-cajero}" CASHIER_PASSWORD="${CASHIER_PASSWORD:-cajero123}"
export BAKER="${BAKER:-panadero}" BAKER_PASSWORD="${BAKER_PASSWORD:-panadero123}"

status=0
python3 -I - <<'PY' || status=$?
import json, os, ssl, sys, time, urllib.error, urllib.request

API, TENANT = os.environ["API"], os.environ["TENANT"]
results = []  # (ok, name, detail)


def _tls_context():
    # A python.org build of Python on macOS ships no CA bundle: every https:// request fails with
    # CERTIFICATE_VERIFY_FAILED, which looks like "the deployment is down". Use the system bundle.
    context = ssl.create_default_context()
    cafile = ssl.get_default_verify_paths().cafile
    if not cafile or not os.path.exists(cafile):
        for candidate in ("/etc/ssl/cert.pem", "/etc/ssl/certs/ca-certificates.crt"):
            if os.path.exists(candidate):
                context.load_verify_locations(candidate)
                break
    return context


TLS = _tls_context()


def record(ok, name, detail=""):
    results.append((ok, name, detail))
    print(("  PASS  " if ok else "  FAIL  ") + name + (f"  -- {detail}" if detail else ""), flush=True)


def http(method, url, body=None, token=None, headers=None, timeout=30):
    """-> (status, headers, body-bytes); network errors come back as status 0."""
    req = urllib.request.Request(url, method=method, data=json.dumps(body).encode() if body is not None else None)
    req.add_header("User-Agent", "ecopan-verify/1")
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=TLS) as r:
            return r.status, r.headers, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.headers, e.read()
    except Exception as e:  # noqa: BLE001 - any transport failure is a failed check, not a crash
        return 0, {}, str(e).encode()


def check(name):
    """Decorator: run the check, turn exceptions into a FAIL so the remaining checks still run."""
    def wrap(fn):
        try:
            fn()
        except AssertionError as e:
            record(False, name, str(e))
        except Exception as e:  # noqa: BLE001
            record(False, name, f"{type(e).__name__}: {e}")
        return fn
    return wrap


def jget(path, token, **query):
    qs = ("?" + "&".join(f"{k}={v}" for k, v in query.items())) if query else ""
    status, _, body = http("GET", API + path + qs, token=token)
    assert status == 200, f"GET {path}{qs} -> HTTP {status} {body[:120].decode(errors='replace')}"
    return json.loads(body)


def login(user, password):
    status, _, body = http("POST", API + "/auth/login",
                           {"tenantSlug": TENANT, "username": user, "password": password, "deviceName": "verify.sh"})
    assert status == 200, f"login as {user} -> HTTP {status} {body[:120].decode(errors='replace')}"
    return json.loads(body)


print(f"== ecopan acceptance check: {API} (tenant {TENANT})")

# -- health -----------------------------------------------------------------------------------
@check("backend /health")
def _():
    status, _, body = http("GET", API + "/health")
    assert status == 200, f"HTTP {status} {body[:120].decode(errors='replace')}"
    record(True, "backend /health")

# -- logins -----------------------------------------------------------------------------------
sessions = {}
for role in ("MANAGER", "CASHIER", "BAKER"):
    @check(f"login as {role.lower()}")
    def _(role=role):
        sessions[role] = login(os.environ[role], os.environ[role + "_PASSWORD"])
        assert sessions[role].get("accessToken"), "no accessToken in the response"
        record(True, f"login as {role.lower()}")

mgr = sessions.get("MANAGER")
token = mgr["accessToken"] if mgr else None
branches = mgr["branches"] if mgr else []


def need_manager():
    assert mgr, "skipped: the manager could not log in"


# -- catalog + photos -------------------------------------------------------------------------
_products = []


@check("catalog")
def _():
    need_manager()
    # includeInactive: the API hides inactive products by default, but the deployment must hold the same
    # catalog as the local database, which counts them (two of them carry the uploaded photos).
    _products.extend(jget("/catalog/products", token, includeInactive="true"))
    expect = os.environ["EXPECT_PRODUCTS"]
    if expect:
        assert len(_products) == int(expect), f"{len(_products)} products, expected {expect}"
    else:
        assert len(_products) > 0, "the catalog is empty"
    photos = [p for p in _products if p.get("imageUrl")]
    expect_photos = os.environ["EXPECT_PHOTOS"]
    if expect_photos:
        assert len(photos) == int(expect_photos), f"{len(photos)} products with a photo, expected {expect_photos}"
    record(True, "catalog", f"{len(_products)} products, {len(photos)} with a photo")


@check("product photos resolve")
def _():
    # Photos uploaded to the deployment's own storage must load; seeded ones point at third-party
    # sites, so a failure there is a warning, not a failed demo.
    assert _products, "skipped: no catalog"
    marks = ["blob.core.windows.net", ":10000/"] + ([os.environ["OWN_STORAGE"]] if os.environ["OWN_STORAGE"] else [])
    own = lambda u: any(m in u for m in marks)  # noqa: E731
    bad, checked = [], 0
    for p in (p for p in _products if p.get("imageUrl")):
        status, hdrs, _ = http("GET", p["imageUrl"], timeout=20)
        is_image = str(hdrs.get("Content-Type", "")).startswith("image/") if hdrs else False
        if own(p["imageUrl"]):
            checked += 1
            if status != 200 or not is_image:
                bad.append(f'{p["name"]} (HTTP {status})')
        elif status != 200:
            print(f'  warn  external photo for {p["name"]} did not load (HTTP {status})')
    assert not bad, "own-storage photos that do not load: " + ", ".join(bad)
    record(True, "product photos resolve", f"{checked} on own storage")

# -- sales history ----------------------------------------------------------------------------
@check("sales history")
def _():
    need_manager()
    now = int(time.time() * 1000)
    rows = jget("/analytics/sales-by-day", token, **{"from": now - 130 * 86400000, "to": now})
    sales = sum(r["salesCount"] for r in rows)
    assert sales > 1000, f"only {sales} sales in the last 130 days (the backend reseeds ~120 days at boot)"
    days = len({r["date"] for r in rows})
    record(True, "sales history", f"{sales} sales over {days} days")

# -- opening stock ----------------------------------------------------------------------------
OPENING = ("Harina", "Agua", "Sal", "Levadura", "Manteca", "Azúcar")


@check("opening stock")
def _():
    need_manager()
    assert branches, "the manager has no branches"
    for b in branches:
        levels = {l["name"]: l["currentGrams"] for l in jget("/inventory/stock", token, branchId=b["id"])}
        missing = [n for n in OPENING if levels.get(n, 0) <= 0]
        assert not missing, f'{b["name"]}: no opening stock for {", ".join(missing)} (was scripts/demo-seed.py opening-stock run?)'
    record(True, "opening stock", f"{len(OPENING)} ingredients in {len(branches)} branches")


@check("Almacén product stock")
def _():
    need_manager()
    almacen = [p for p in _products if p.get("origin") == "ALMACEN" and p.get("active")]
    for b in branches:
        tracked = {s["productId"] for s in jget("/inventory/product-stock", token, branchId=b["id"])}
        missing = [p["name"] for p in almacen if p["id"] not in tracked]
        assert not missing, f'{b["name"]}: untracked Almacén products: {", ".join(missing)}'
    record(True, "Almacén product stock", f"{len(almacen)} products tracked in {len(branches)} branches")

# -- forecasting ------------------------------------------------------------------------------
@check("DEMAND_FORECASTING enabled")
def _():
    need_manager()
    modules = mgr["tenant"]["enabledModules"]
    assert "DEMAND_FORECASTING" in modules, "module is off (scripts/demo-seed.py forecast turns it on)"
    record(True, "DEMAND_FORECASTING enabled")


@check("forecast rows")
def _():
    need_manager()
    deadline = time.time() + int(os.environ["FORECAST_WAIT"])
    rows = []
    while True:
        now = int(time.time() * 1000)
        rows = []
        for b in branches:
            rows += jget("/forecasts", token, branchId=b["id"], **{"from": now - 86400000, "to": now + 14 * 86400000})
        if rows or time.time() > deadline:
            break
        time.sleep(5)
    assert rows, f'no forecast rows after {os.environ["FORECAST_WAIT"]} s (worker -> ML service path)'
    record(True, "forecast rows", f'{len(rows)} rows, model_version: {", ".join(sorted({r["modelVersion"] for r in rows}))}')

# -- ML service (optional) --------------------------------------------------------------------
if os.environ["ML_URL"]:
    @check("ML service /health")
    def _():
        status, _, body = http("GET", os.environ["ML_URL"] + "/health")
        assert status == 200, f"HTTP {status} (IP-restricted? run it from an allowed address)"
        health = json.loads(body)
        want = os.environ["EXPECT_ML"]
        if want in ("true", "false"):
            assert str(health.get("model_loaded")).lower() == want, f"model_loaded={health.get('model_loaded')}, expected {want}"
        record(True, "ML service /health", f'model_loaded={health.get("model_loaded")}, version={health.get("model_version")}')

# -- dashboard + CORS (optional) ----------------------------------------------------------------
if os.environ["WEB_URL"]:
    web = os.environ["WEB_URL"]

    @check("dashboard loads")
    def _():
        status, _, body = http("GET", web)
        assert status == 200 and b"<html" in body.lower(), f"HTTP {status}"
        record(True, "dashboard loads")

    @check("dashboard deep link loads (SPA fallback)")
    def _():
        # A client-side route requested directly (a refresh, a bookmark) must get index.html, not Azure's 404:
        # that is the navigationFallback in webApp/public/staticwebapp.config.json. A missing asset must stay a 404.
        status, _, body = http("GET", web + "/login")
        assert status == 200 and b"<html" in body.lower(), f"/login -> HTTP {status} (no navigationFallback in staticwebapp.config.json?)"
        status, _, _ = http("GET", web + "/assets/does-not-exist.js")
        assert status == 404, f"a missing asset answered HTTP {status}; it must be 404, not the SPA page"
        record(True, "dashboard deep link loads (SPA fallback)")

    @check("dashboard cannot be framed and carries a Content-Security-Policy")
    def _():
        # globalHeaders in staticwebapp.config.json, generated at deploy time by infra/web-headers.py.
        status, hdrs, _ = http("GET", web + "/")
        hdrs = {k.lower(): v for k, v in (hdrs or {}).items()}
        csp = hdrs.get("content-security-policy", "")
        assert "frame-ancestors 'none'" in csp, f"no frame-ancestors 'none' in the Content-Security-Policy ({csp or 'header missing'})"
        assert API in csp, f"the policy does not allow the API ({API}) in connect-src, the dashboard could not call it"
        assert hdrs.get("x-frame-options", "").upper() == "DENY", "X-Frame-Options is not DENY"
        assert hdrs.get("x-content-type-options", "").lower() == "nosniff", "X-Content-Type-Options is not nosniff"
        record(True, "dashboard cannot be framed and carries a Content-Security-Policy")

    @check("API answers the dashboard's CORS preflight")
    def _():
        status, hdrs, _ = http("OPTIONS", API + "/auth/login", headers={
            "Origin": web, "Access-Control-Request-Method": "POST", "Access-Control-Request-Headers": "content-type"})
        allowed = hdrs.get("Access-Control-Allow-Origin") if hdrs else None
        assert status in (200, 204) and allowed in (web, "*"), f"HTTP {status}, Access-Control-Allow-Origin={allowed}"
        record(True, "API answers the dashboard's CORS preflight")

failed = [r for r in results if not r[0]]
print(f"\n== {len(results) - len(failed)} passed, {len(failed)} failed")
sys.exit(1 if failed else 0)
PY

# The SSE path last: it writes one real batch (see its header), so it must not disturb the counts above.
if [[ $status -eq 0 && "$RUN_SSE" == true ]]; then
  echo
  echo "== SSE end to end (scripts/verify-sse.sh)"
  TENANT="$TENANT" MANAGER="$MANAGER" MANAGER_PASSWORD="$MANAGER_PASSWORD" \
    BAKER="$BAKER" BAKER_PASSWORD="$BAKER_PASSWORD" "$REPO_ROOT/scripts/verify-sse.sh" "$API" || status=1
fi
exit "$status"
