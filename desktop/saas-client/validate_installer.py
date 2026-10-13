"""Validate DEV installer in a fresh native-runner temp directory, never an old installation."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path


def run(*args, **kwargs):
    subprocess.run(list(map(str, args)), check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--package-dir", required=True, type=Path)
    args = parser.parse_args()
    package = args.package_dir.resolve()
    manifest = json.loads((package / "manifest.json").read_text(encoding="utf-8"))
    artifact = package / manifest["artifact"]
    assert artifact.parent == package and artifact.is_file()
    assert manifest["release_status"] == "development-only"
    assert hashlib.sha256(artifact.read_bytes()).hexdigest() == manifest["sha256"]
    with tempfile.TemporaryDirectory(prefix="studio-installer-synthetic-") as folder:
        root = Path(folder)
        if sys.platform == "darwin":
            mount = root / "mounted"
            mount.mkdir()
            run("hdiutil", "attach", artifact, "-readonly", "-nobrowse", "-mountpoint", mount)
            executable = mount / "RehabSaaSClient-DEV.app/Contents/MacOS/RehabSaaSClient-DEV"
            try:
                run(
                    sys.executable,
                    "desktop/saas-client/smoke.py",
                    env={**os.environ, "CLIENT_EXECUTABLE": str(executable)},
                )
            finally:
                run("hdiutil", "detach", mount)
            mode = "read-only DMG mount, packaged workflow, detach"
        elif sys.platform == "win32":
            installed = root / "installation"
            run(
                artifact,
                "/VERYSILENT",
                "/SUPPRESSMSGBOXES",
                "/NORESTART",
                "/SP-",
                "/DIR=" + str(installed),
            )
            executable = installed / "RehabSaaSClient-DEV.exe"
            uninstaller = installed / "unins000.exe"
            assert executable.is_file() and uninstaller.is_file()
            try:
                run(
                    sys.executable,
                    "desktop/saas-client/smoke.py",
                    env={**os.environ, "CLIENT_EXECUTABLE": str(executable)},
                )
            finally:
                run(uninstaller, "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART")
            assert not executable.exists(), "Uninstall did not remove test application"
            mode = "per-user silent install, packaged workflow, uninstall"
        else:
            raise RuntimeError("Native macOS or Windows required")
    result = {
        "status": "passed",
        "platform": sys.platform,
        "mode": mode,
        "artifact_sha256": manifest["sha256"],
        "synthetic_only": True,
    }
    (package / "installer-validation.json").write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps(result))


if __name__ == "__main__":
    main()
