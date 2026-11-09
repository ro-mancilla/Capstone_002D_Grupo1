#!/usr/bin/env python3
"""Adds the security headers to the dashboard's staticwebapp.config.json (in the built folder).

Why a script and not a static file: the Content-Security-Policy has to name the backend and the photo
storage, which are only known at deploy time, and the inline theme bootstrap in index.html is allowed
by its hash, so any edit to it is picked up on the next build instead of silently breaking the page.

  web-headers.py --dist webApp/dist --api https://x.azurewebsites.net --images https://x.blob.core.windows.net
"""
import argparse, base64, hashlib, json, pathlib, re, sys


def inline_script_hashes(html: str) -> list[str]:
    hashes = []
    for attrs, body in re.findall(r"<script([^>]*)>(.*?)</script>", html, flags=re.S | re.I):
        if "src=" in attrs or not body.strip():
            continue
        digest = hashlib.sha256(body.encode()).digest()
        hashes.append("'sha256-" + base64.b64encode(digest).decode() + "'")
    return hashes


def policy(api: str, images: list[str], script_hashes: list[str]) -> str:
    return "; ".join([
        "default-src 'self'",
        "script-src 'self' " + " ".join(script_hashes),
        # Components position popovers and charts with style attributes, which only 'unsafe-inline' allows.
        "style-src 'self' 'unsafe-inline'",
        # Any https image: the demo products point at Wikimedia photos, and the product form accepts a URL.
        # An image cannot run code or read the tokens, so this is not where the policy earns its keep.
        "img-src 'self' data: blob: " + " ".join(images + ["https:"]),
        "font-src 'self' data:",
        f"connect-src 'self' {api}",
        "object-src 'none'",
        "base-uri 'self'",
        "form-action 'self'",
        "frame-ancestors 'none'",
    ])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dist", required=True)
    parser.add_argument("--api", required=True, help="backend origin")
    parser.add_argument("--images", action="append", default=[], help="origin product photos load from (repeatable)")
    parser.add_argument("--report-only", action="store_true")
    args = parser.parse_args()

    dist = pathlib.Path(args.dist)
    config_path = dist / "staticwebapp.config.json"
    config = json.loads(config_path.read_text()) if config_path.exists() else {}
    hashes = inline_script_hashes((dist / "index.html").read_text())
    csp_name = "Content-Security-Policy-Report-Only" if args.report_only else "Content-Security-Policy"
    config["globalHeaders"] = {
        csp_name: policy(args.api.rstrip("/"), [i.rstrip("/") for i in args.images], hashes),
        "X-Frame-Options": "DENY",
        "X-Content-Type-Options": "nosniff",
        "Referrer-Policy": "no-referrer",
    }
    config_path.write_text(json.dumps(config, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
