#!/usr/bin/env python3
"""Read-only S0 inventory. Never opens env files, databases or patient media.

Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT
Requires Python 3.11+. JSON goes only to stdout; callers choose a new output file.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile


REPO = Path(__file__).resolve().parents[2]
SOURCE_DIRS = ("src", "tests", "schemas")
SOURCE_ROOT_FILES = ("README.md", "pyproject.toml", "uv.lock", "LICENSE", "LICENSE.md")
SOURCE_SUFFIXES = {
    ".py", ".md", ".toml", ".lock", ".json", ".yaml", ".yml",
    ".txt", ".mjs", ".ts", ".html", ".css", ".j2",
}
SKIP_DIRS = {"__pycache__", ".git", ".venv", "node_modules", "target", "dist", ".pytest_cache"}
ANCHORS = {
    "core": (
        "pom.xml", "yudao-server/pom.xml", "deploy/internal/migrations.manifest",
        "deploy/internal/docker-compose.yml",
        "yudao-ui/yudao-ui-admin-vue3-app/package.json", "desktop/launcher/package.json",
    ),
    "reporter": (
        "AGENTS.md", "requirements.txt", "requirements-motion.txt",
        "rehab_reporter/assessment_workspace/motion.py",
        "rehab_reporter/assessment_workspace/core_bridge.py",
    ),
    "hep": (
        "backend/pom.xml", "db/migrations/V001__hep_core.sql",
        "uniapp-patient/package.json", "uniapp-patient/src/App.vue",
        "uniapp-patient/src/lib/api.ts",
    ),
}
PROBES = {
    "core-health": "http://127.0.0.1:48080/actuator/health",
    "hep-health": "http://127.0.0.1:8088/health",
    "motion-login": "http://127.0.0.1:8806/",
    "motion-unauthenticated": "http://127.0.0.1:8806/api/me",
    "core-web": "http://127.0.0.1:5173/",
    "hep-patient-web": "http://127.0.0.1:5191/",
}
PROBE_PORTS = {48080, 8088, 8806, 5173, 5191}


def scoped(root, relative):
    """Reject absolute paths, traversal and symlinks rather than following them."""
    rel = PurePosixPath(relative)
    if not relative or rel.is_absolute() or ".." in rel.parts or "\\" in relative:
        raise ValueError("Unsafe registry path")
    path = root
    for part in rel.parts:
        path = path / part
        if path.is_symlink():
            raise ValueError("Symlinked inventory input refused")
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError("Inventory path escapes root")
    return path


def digest(path):
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def git_read(path, args):
    result = subprocess.run(
        ["git", "--no-optional-locks", "-c", "core.fsmonitor=false", "-C", str(path), *args],
        capture_output=True, text=True, timeout=20, check=False,
    )
    if result.returncode:
        raise ValueError("Git metadata read failed")
    return result.stdout.strip()


def git_metadata(path):
    if not (path / ".git").exists():
        return {"state": "not_git"}
    top = git_read(path, ["rev-parse", "--show-toplevel"])
    if Path(top).resolve() != path.resolve():
        return {"state": "nested_noncanonical"}
    status = git_read(path, ["status", "--porcelain=v1", "--untracked-files=no"])
    return {
        "state": "git", "commit": git_read(path, ["rev-parse", "HEAD"]),
        "branch": git_read(path, ["branch", "--show-current"]) or "DETACHED",
        "trackedWorktreeDirty": bool(status),
        "untrackedFiles": "not_collected_to_avoid_exporting_private_names",
    }


def source_hashes(root):
    """Only named source directories/root files; no fixtures, data or runtime scan."""
    hashes = {}
    for name in SOURCE_ROOT_FILES:
        path = scoped(root, name)
        if path.is_file():
            hashes[name] = digest(path)
    for name in SOURCE_DIRS:
        base = scoped(root, name)
        if not base.is_dir():
            continue
        for directory, names, files in os.walk(base, followlinks=False):
            for child in names:
                if (Path(directory) / child).is_symlink():
                    raise ValueError("Symlinked source directory refused")
            names[:] = sorted(n for n in names if n not in SKIP_DIRS)
            for filename in sorted(files):
                relative = (Path(directory) / filename).relative_to(root).as_posix()
                path = scoped(root, relative)
                if path.suffix in SOURCE_SUFFIXES:
                    hashes[relative] = digest(path)
    return dict(sorted(hashes.items()))


def compare_sources(left, right):
    a, b = source_hashes(left), source_hashes(right)
    common = a.keys() & b.keys()
    changed = sorted(name for name in common if a[name] != b[name])
    return {
        "scope": "README/pyproject/lock/license plus source/test/schema text files only; not whole-directory equivalence",
        "leftFiles": len(a), "rightFiles": len(b),
        "same": len(common) - len(changed), "different": len(changed),
        "leftOnly": sorted(a.keys() - b.keys()), "rightOnly": sorted(b.keys() - a.keys()),
        "changed": [{"path": n, "leftSha256": a[n], "rightSha256": b[n]} for n in changed],
        "decision": "preserve_both_pending_provenance_and_license_review",
    }


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def validate_probe_url(url):
    parsed = urllib.parse.urlsplit(url)
    if (parsed.scheme != "http" or parsed.hostname != "127.0.0.1"
            or parsed.port not in PROBE_PORTS or parsed.username or parsed.password
            or parsed.query or parsed.fragment or url not in PROBES.values()):
        raise ValueError("Only fixed unauthenticated loopback probes are permitted")


def probe(url):
    validate_probe_url(url)
    # No proxy inheritance, cookies, credentials, redirects or TLS bypass.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    try:
        with opener.open(url, timeout=3) as response:
            result = {"httpStatus": response.status}
            if url.endswith("/health"):
                body = json.loads(response.read(4096))
                result["health"] = "UP" if isinstance(body, dict) and body.get("status") == "UP" else "not_up"
            return result
    except urllib.error.HTTPError as error:
        return {"httpStatus": error.code}  # Never read an error payload.
    except (OSError, ValueError, urllib.error.URLError):
        return {"state": "unavailable_or_unreadable"}


def artifact_metadata(root):
    # Read one specifically known immutable binary, never runtime configuration.
    relative = "rehab-runtime/core-local/releases/20261026-motion-v1/yudao-server.jar"
    path = scoped(root, relative)
    if not path.is_file():
        return {"state": "not_found", "activeProcess": "not_verified"}
    with zipfile.ZipFile(path) as jar:
        modules = sorted({m.group(1) for name in jar.namelist()
                          if (m := re.fullmatch(r"BOOT-INF/lib/(yudao-module-[a-z-]+)-[0-9][^/]*\.jar", name))})
    return {"state": "known_immutable_artifact", "path": relative,
            "sha256": digest(path), "modules": modules,
            "activeProcess": "not_verified_by_this_read_only_tool"}


def collect(root, registry, with_probes=False):
    ids, paths, assets = set(), set(), []
    for entry in registry["assets"]:
        if entry["id"] in ids or entry["path"] in paths:
            raise ValueError("Duplicate asset id or path")
        ids.add(entry["id"])
        paths.add(entry["path"])
        path = scoped(root, entry["path"])
        item = {**entry, "exists": path.is_dir()}
        if path.is_dir() and entry["git"]:
            item["sourceVersion"] = git_metadata(path)
        if entry["id"] in ANCHORS and path.is_dir():
            item["anchors"] = {}
            for relative in ANCHORS[entry["id"]]:
                anchor = scoped(path, relative)
                item["anchors"][relative] = digest(anchor) if anchor.is_file() else None
        assets.append(item)
    left = scoped(root, "rehab/services/rehab-biomechanics-engine")
    right = scoped(root, "rehab-biomechanics-engine")
    return {
        "schemaVersion": 1, "capturedAt": datetime.now(timezone.utc).isoformat(),
        "purpose": "S0 implementation input baseline, not a release approval or a final delivery commit",
        "rootLabel": "my-project", "workspaceReadmeSha256": digest(scoped(root, "README.md")),
        "assets": assets,
        "engineComparison": compare_sources(left, right) if left.is_dir() and right.is_dir() else {"state": "inputs_missing"},
        "coreArtifact": artifact_metadata(root),
        "loopbackProbes": {key: probe(url) for key, url in PROBES.items()} if with_probes else {"state": "not_requested"},
        "notInspected": ["runtime env/secrets/databases/media/backups", "archive/evidence/data contents", "unregistered directories", "remote GitHub state or external accounts"],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=REPO.parents[1])
    parser.add_argument("--probe", action="store_true", help="Fixed unauthenticated loopback HTTP status checks only")
    args = parser.parse_args()
    root = args.root.absolute()
    if root.is_symlink() or root == Path(root.anchor) or not (root / "README.md").is_file():
        raise ValueError("An explicit workspace directory containing README.md is required")
    registry = json.loads((REPO / "docs/s0/assets.json").read_text())
    print(json.dumps(collect(root, registry, args.probe), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.SubprocessError, zipfile.BadZipFile):
        print("S0 inventory failed safely; no business files were written. Check explicit paths and source metadata.", file=sys.stderr)
        sys.exit(1)
