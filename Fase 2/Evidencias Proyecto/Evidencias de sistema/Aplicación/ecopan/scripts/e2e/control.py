#!/usr/bin/env python3
"""Fault control for device tests that cannot run docker themselves (the baker app on an emulator or simulator).

The runner starts this on 127.0.0.1:18999 for the length of an android/ios run; the device reaches it through
`adb reverse` (or directly on a simulator). It can only do what the till's journeys do to the THROWAWAY stack
`ecopan-acc`: stop/start the API container and run SQL in that stack's database. Nothing else, no other project.

    POST /stop              stop the API
    POST /start             start the API and wait until /health answers
    POST /sql   (body=SQL)  run it in the throwaway database
    POST /logs              the API's log so far (to count what the backend refused)
"""
import subprocess, sys, time, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PROJECT = "ecopan-acc"
API = "http://localhost:18080"
COMPOSE = ["docker", "compose", "-p", PROJECT, "-f", "docker-compose.yml", "-f", "scripts/acceptance/compose.yml", "-f", "scripts/e2e/compose.yml"]


def run(*args, stdin=None):
    done = subprocess.run([*COMPOSE, *args], input=stdin, capture_output=True, text=True)
    return done.returncode, (done.stdout + done.stderr)


def wait_healthy(timeout=120):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            if urllib.request.urlopen(f"{API}/health", timeout=2).status == 200:
                return True
        except Exception:
            time.sleep(1)
    return False


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length).decode() if length else ""
        if self.path == "/stop":
            code, out = run("stop", "web")
        elif self.path == "/start":
            code, out = run("start", "web")
            if code == 0 and not wait_healthy():
                code, out = 1, "the backend did not come back"
        elif self.path == "/logs":
            code, out = run("logs", "--no-color", "web")
        elif self.path == "/sql":
            code, out = run("exec", "-T", "db", "psql", "-U", "postgres", "-d", "ecopan", "-v", "ON_ERROR_STOP=1", "-X", "-q", "-t", "-A", "-c", body)
        else:
            code, out = 1, "unknown"
        self.send_response(200 if code == 0 else 500)
        self.end_headers()
        self.wfile.write(out.encode())

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 18999), Handler).serve_forever()
