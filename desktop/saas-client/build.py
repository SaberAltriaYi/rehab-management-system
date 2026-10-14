"""Build explicitly unsigned developer packages without touching installed versions."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAME = "RehabSaaSClient-DEV"


def run(*args):
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip())
    if dirty:
        raise RuntimeError("Refusing to package an uncommitted source tree")
    work = output / "work"
    work.mkdir()
    dist = output / "application"
    cmd = [
        sys.executable,
        "-m",
        "PyInstaller",
        "--onedir",
        "--windowed",
        "--name",
        NAME,
        "--distpath",
        dist,
        "--workpath",
        work,
        "--specpath",
        work,
    ]
    if sys.platform == "darwin":
        cmd += ["--osx-bundle-identifier", "org.rehab.saas.client.dev"]
    run(*cmd, ROOT / "desktop/saas-client/app.py")
    label = f"RehabSaaSClient-DEV-{platform.system()}-{platform.machine()}-{commit[:10]}"
    if sys.platform == "darwin":
        stage = output / "dmg-stage"
        stage.mkdir()
        shutil.copytree(dist / (NAME + ".app"), stage / (NAME + ".app"), symlinks=True)
        (stage / "Applications").symlink_to("/Applications")
        (stage / "README.txt").write_text(
            "DEVELOPMENT BUILD: no publisher signature or notarization.\n"
            "Not a medical diagnostic device. SaaS browser connector; backend is deployed separately, not bundled.\n"
            "Uses a separate user data directory; no old version is replaced by this build.\n"
        )
        artifact = output / (label + ".dmg")
        run(
            "hdiutil",
            "create",
            "-volname",
            "Rehab SaaS DEV",
            "-srcfolder",
            stage,
            "-ov",
            "-format",
            "UDZO",
            artifact,
        )
    elif sys.platform == "win32":
        compiler = shutil.which("ISCC") or r"C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
        if not Path(compiler).is_file():
            raise RuntimeError("Inno Setup 6 is required")
        run(
            compiler,
            "/DSourceDir=" + str(dist / NAME),
            "/DOutputDir=" + str(output),
            "/DOutputName=" + label,
            ROOT / "desktop/saas-client/client.iss",
        )
        artifact = output / (label + ".exe")
    else:
        raise RuntimeError("Installers require a native macOS or Windows build host")
    manifest = {
        "edition": "saas-client",
        "backend_bundled": False,
        "source_commit": commit,
        "dirty_tree": dirty,
        "platform": platform.system(),
        "architecture": platform.machine(),
        "python": platform.python_version(),
        "publisher_signed": False,
        "notarized": False,
        "release_status": "development-only",
        "artifact": artifact.name,
        "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
        "browser_validation": "must run desktop/saas-client/smoke.py with CLIENT_EXECUTABLE",
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(json.dumps(manifest))


if __name__ == "__main__":
    main()
