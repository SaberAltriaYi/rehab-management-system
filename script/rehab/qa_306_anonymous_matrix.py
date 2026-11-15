#!/usr/bin/env python3
"""Read-only, fail-closed anonymous API matrix for 306-case acceptance.

No passwords, cookies, tokens, patient IDs, database writes or disabled TLS.
Core is probed only from its own Docker container on its private HTTP port;
the Owner request uses the platform's normally trusted HTTPS endpoint.
"""
from __future__ import annotations

import json
import subprocess
import sys
from datetime import datetime, timezone


CORE = "rehab-studio-internal-server-1"
OWNER = "https://127.0.0.1:9444"
MOTION = "http://127.0.0.1:8806"
HEP = "http://127.0.0.1:8088"


def request(base: str, path: str, *, via_core: bool = False,
            method: str = "GET", origin: str | None = None,
            headers: tuple[str, ...] = ()) -> tuple[int, int, object]:
    cmd = ["curl", "--silent", "--show-error", "--max-time", "6",
           "--request", method, "--write-out", "\n__QA_HTTP__%{http_code}__TLS__%{ssl_verify_result}"]
    if origin is not None:
        cmd += ["--header", "Origin: " + origin]
    for value in headers:
        cmd += ["--header", value]
    cmd += [base + path]
    if via_core:
        cmd = ["docker", "exec", CORE, *cmd]
    completed = subprocess.run(cmd, text=True, capture_output=True, timeout=12,
                               check=False)
    if completed.returncode or "\n__QA_HTTP__" not in completed.stdout:
        raise RuntimeError("HTTP_REQUEST_OR_TLS_FAILED")
    body, meta = completed.stdout.rsplit("\n__QA_HTTP__", 1)
    code, tls = meta.strip().split("__TLS__", 1)
    payload = json.loads(body) if body.strip() else None
    return int(code), int(tls), payload


def check(condition: bool, case: str, label: str, result: str, collection: list) -> None:
    collection.append({"case": case, "check": label, "result": "PASS" if condition else "FAIL",
                       "detail": result})


def run() -> dict:
    checks: list[dict] = []
    page = "?pageNo=1&pageSize=10"
    core_routes = [
        "/admin-api/rehab/patient/page", "/admin-api/rehab/episode/page",
        "/admin-api/rehab/assessment/page", "/admin-api/rehab/report/page",
        "/admin-api/rehab/plan/page", "/admin-api/system/user/page",
    ]
    for route in core_routes:
        try:
            status, tls, data = request("http://127.0.0.1:48080", route + page,
                                        via_core=True)
            ok = status == 200 and isinstance(data, dict) and data.get("code") == 401 \
                and data.get("data") is None
            detail = f"HTTP {status}, business code {data.get('code') if isinstance(data, dict) else None}, data=null={isinstance(data, dict) and data.get('data') is None}"
        except (RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
            ok, detail = False, type(error).__name__
        check(ok, "T-243", "Core " + route, detail, checks)

    hep_routes = [
        "/api/admin/overview", "/api/admin/core-sync/status",
        "/api/admin/list/prescriptions",
        "/api/therapist/patients", "/api/therapist/templates",
        "/api/therapist/plans", "/api/therapist/risks", "/api/patient/me",
        "/api/patient/today", "/api/patient/history",
    ]
    for route in hep_routes:
        try:
            status, tls, data = request(HEP, route)
            ok, detail = status == 401 and data is None, f"HTTP {status}, empty body={data is None}"
        except (RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
            ok, detail = False, type(error).__name__
        check(ok, "T-243", "HEP " + route, detail, checks)

    for route in ("/api/assessments", "/api/assessments/a-000000000000000000000000",
                  "/api/assessments/a-000000000000000000000000/files"):
        try:
            status, tls, data = request(MOTION, route)
            ok = status == 401 and isinstance(data, dict) and "error" in data
            detail = f"HTTP {status}, error present={isinstance(data, dict) and 'error' in data}"
        except (RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
            ok, detail = False, type(error).__name__
        check(ok, "T-243", "Motion " + route, detail, checks)

    private = "/owner/v1/applications"
    owner_matrix = [
        ("https://untrusted.example", "GET", 403, "CROSS_ORIGIN_FORBIDDEN"),
        ("https://127.0.0.1:18443", "GET", 403, "CROSS_ORIGIN_FORBIDDEN"),
        ("https://127.0.0.1:9444", "GET", 401, "OWNER_UNAUTHORIZED"),
        ("https://untrusted.example", "OPTIONS", 403, "CROSS_ORIGIN_FORBIDDEN"),
        ("https://127.0.0.1:9444", "OPTIONS", 405, "NOT_ALLOWED"),
    ]
    for origin, method, expected_http, expected_error in owner_matrix:
        try:
            status, tls, data = request(
                OWNER, private, method=method, origin=origin,
                headers=("Access-Control-Request-Method: POST",) if method == "OPTIONS" else ())
            ok = (status == expected_http and tls == 0 and isinstance(data, dict)
                  and data.get("error") == expected_error)
            detail = f"HTTP {status}, strict TLS verified={tls == 0}, expected error={data.get('error') == expected_error if isinstance(data, dict) else False}"
        except (RuntimeError, ValueError, subprocess.TimeoutExpired) as error:
            ok, detail = False, type(error).__name__
        check(ok, "T-044", method + " private API, Origin=" + origin,
              detail, checks)

    return {"schema": "rehab-306-live-anonymous-qa/1", "timeUtc": datetime.now(timezone.utc).isoformat(),
            "environment": "MAC1 installed Core + independent HEP/Motion/Owner",
            "scope": "read-only anonymous API and cross-origin checks",
            "noClinicalAuthorizationClaim": True, "checks": checks,
            "pass": sum(c["result"] == "PASS" for c in checks),
            "fail": sum(c["result"] != "PASS" for c in checks)}


if __name__ == "__main__":
    report = run()
    print(json.dumps(report, ensure_ascii=False, indent=2))
    sys.exit(0 if report["fail"] == 0 else 1)
