#!/usr/bin/env python3
"""L5 provisional CycloneDX 1.5 LOCKFILE dependency inventory.

This is not an artifact SBOM, vulnerability scan, provenance attestation,
or evidence of a signed/released bundle. Never include credentials, clinical
records, patient data or untracked files. Source lockfiles are read-only.
"""
import argparse
import hashlib
import json
import re
import sys
import tomllib
from pathlib import Path
from urllib.parse import quote

import yaml

ROOT = Path(__file__).resolve().parents[2]
SOURCES = {
    "cargo": ROOT / "desktop/launcher/src-tauri/Cargo.lock",
    "frontend-pnpm": ROOT / "yudao-ui/yudao-ui-admin-vue3-app/pnpm-lock.yaml",
    "launcher-pnpm": ROOT / "desktop/launcher/pnpm-lock.yaml",
}


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def component(ecosystem, name, version, *, kind="library"):
    if not name or not version or any(c in version for c in '\x00\n\r'):
        raise ValueError("Malformed dependency version")
    namespace = name.split("/", 1)
    normalized = name
    if ecosystem == "npm" and name.startswith("@") and len(namespace) == 2:
        normalized = "%40" + quote(namespace[0][1:], safe="") + "/" + quote(namespace[1], safe="")
    else:
        normalized = quote(name, safe="/")
    purl = f"pkg:{ecosystem}/{normalized}@{quote(version, safe='.-_+')}"
    return {"type": kind, "name": name, "version": version, "purl": purl, "bom-ref": purl}


def cargo_components(path):
    obj = tomllib.loads(path.read_text(encoding="utf8"))
    return [component("cargo", pkg["name"], pkg["version"])
            for pkg in obj["package"] if pkg.get("source", "").startswith("registry+")]


def pnpm_components(path):
    obj = yaml.safe_load(path.read_text(encoding="utf8"))
    result = []
    for key in obj.get("packages", {}):
        raw = key.lstrip("/")
        if "@" not in raw:
            raise ValueError("Invalid pnpm package entry")
        name, version = raw.rsplit("@", 1)
        version = version.split("(", 1)[0]
        if not name or not re.fullmatch(r"[a-zA-Z0-9][0-9A-Za-z.+_-]*", version):
            raise ValueError("Malformed pnpm package/version")
        result.append(component("npm", name, version))
    return result


def maven_components(path):
    if not path or not path.is_file():
        return [], ["MAVEN_TRANSITIVE_TREE_MISSING"]
    root = json.loads(path.read_text(encoding="utf8"))
    out = []

    def visit(node):
        name = node.get("artifactId")
        version = node.get("version")
        group = node.get("groupId")
        if group and name and version:
            purl = f"pkg:maven/{quote(group, safe='.')}/{quote(name)}@{quote(version)}"
            out.append({"type": "library", "name": name,
                        "group": group, "version": version, "purl": purl, "bom-ref": purl})
        for item in node.get("children", []):
            visit(item)

    visit(root)
    return out, []


def make_bom(maven_tree=None):
    all_components = cargo_components(SOURCES["cargo"])
    all_components.extend(pnpm_components(SOURCES["frontend-pnpm"]))
    all_components.extend(pnpm_components(SOURCES["launcher-pnpm"]))
    maven, warnings = maven_components(maven_tree)
    all_components.extend(maven)
    dedup = {x["bom-ref"]: x for x in all_components}
    if len(dedup) < 100:
        raise ValueError("Too few components found; lockfiles may be incomplete")
    props = [{"name": "rehab:l5:completeness", "value": "provisional-lockfile-only"},
             {"name": "rehab:l5:artifact-verification", "value": "not-performed"},
             {"name": "rehab:l5:online-vulnerability-scan", "value": "not-performed"},
             {"name": "rehab:l5:maven-tree-warning", "value": "reactor-local-dependency-POMs-may-be-incomplete"}]
    for name, file in SOURCES.items():
        props.append({"name": "rehab:l5:source-sha256:" + name, "value": sha(file)})
    if maven_tree:
        props.append({"name": "rehab:l5:source-sha256:maven-tree", "value": sha(maven_tree)})
    for warning in warnings:
        props.append({"name": "rehab:l5:warning", "value": warning})
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "version": 1,
        "metadata": {"component": {
            "type": "application", "name": "rehab-management-provisional-source-locks"
        }, "properties": props},
        "components": [dedup[key] for key in sorted(dedup)]
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--maven-tree", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("refusing to overwrite existing evidence")
    bom = make_bom(args.maven_tree)
    with args.output.open("x", encoding="utf8") as handle:
        json.dump(bom, handle, indent=2, ensure_ascii=False, sort_keys=True)
        handle.write("\n")
    args.output.chmod(0o600)
    print(f"L5_PROVISIONAL_SOURCE_LOCKFILE_COMPONENTS={len(bom['components'])}")
    print("ARTIFACT_SBOM_COMPLETE=false")
    print("VULNERABILITY_SCAN_PERFORMED=false")


if __name__ == "__main__":
    main()
