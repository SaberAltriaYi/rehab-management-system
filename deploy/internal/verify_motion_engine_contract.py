#!/usr/bin/env python3
"""Compare Java motion identifiers, sides and FMS tiers with the Python Engine.

Read-only; requires explicit local paths to both checked-out source trees.
Never reads clinical case files or connects to a network service/database.
"""

import argparse
import importlib
import json
import re
import subprocess
import sys
from pathlib import Path


JAVA_CONSTANTS = (
    "yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/"
    "enums/RehabMotionConstants.java"
)
FAMILIES = {
    "FMS_TESTS": "FMS",
    "NASM_TESTS": "NASM_CES",
    "OTHER_TESTS": None,
}
OTHER_FAMILIES = {
    "YBT_LQ": "YBT_LQ",
    "TUCK_JUMP": "TUCK_JUMP",
    "LESS": "LESS",
}


def parse_java_registry(source: str) -> dict:
    """Extract constant lists only; fail rather than guess when structure changes."""

    def read_list(name: str) -> list[str]:
        pattern = (
            rf"\b{re.escape(name)}\s*=\s*Collections\.unmodifiable(?:List|Set)"
            rf"\(.*?Arrays\.asList\((.*?)\)\)"
        )
        match = re.search(pattern, source, flags=re.DOTALL)
        if match is None:
            raise ValueError(f"Java constant not found: {name}")
        values = re.findall(r'"([A-Z][A-Z0-9_]*)"', match.group(1))
        if not values or len(values) != len(set(values)):
            raise ValueError(f"Java constant empty or duplicate: {name}")
        return values

    registry = {}
    for name, family in FAMILIES.items():
        for code in read_list(name):
            if code in registry:
                raise ValueError(f"Java test code duplicated: {code}")
            if family is None and code not in OTHER_FAMILIES:
                raise ValueError(f"Unmapped Java test code: {code}")
            registry[code] = family or OTHER_FAMILIES[code]
    bilateral = set(read_list("BILATERAL_TESTS"))
    if bilateral - registry.keys():
        raise ValueError("Bilateral test not registered in Java family lists")
    return {
        code: {"family": family, "sides": ["bilateral"] if code in bilateral else ["left", "right"]}
        for code, family in registry.items()
    }


def parse_java_fms_conditions(source: str) -> dict[str, list[str]]:
    """Extract the ordered Java FMS tier map, rejecting unknown syntax."""
    match = re.search(
        r"\bFMS_CONDITIONS\s*=\s*Collections\.unmodifiableMap"
        r"\(new LinkedHashMap<.*?>\(\)\s*\{\{(.*?)\}\}\);",
        source,
        flags=re.DOTALL,
    )
    if match is None:
        raise ValueError("Java FMS_CONDITIONS map not found")
    body = match.group(1)
    rows = re.findall(
        r'\bput\("([A-Z][A-Z0-9_]*)",\s*'
        r'Collections\.unmodifiableList\(Arrays\.asList\((.*?)\)\)\);',
        body, flags=re.DOTALL,
    )
    if not rows or len(rows) != len(re.findall(r"\bput\(", body)):
        raise ValueError("Java FMS_CONDITIONS contains unsupported entries")
    result = {}
    for code, literals in rows:
        names = re.findall(r'"([a-z][a-z0-9_]*)"', literals)
        if code in result or len(names) != 2 or len(set(names)) != 2:
            raise ValueError("Java FMS_CONDITIONS duplicate or invalid tiers")
        if re.sub(r'"[a-z][a-z0-9_]*"|[\s,]', "", literals):
            raise ValueError("Java FMS_CONDITIONS contains unsupported values")
        result[code] = names
    return result


def compare_fms_tiers(java_tiers: dict, engine_items: dict) -> list[str]:
    issues = []
    if not isinstance(engine_items, dict):
        return ["Engine FMS scoring registry invalid"]
    engine_tiers = {}
    for code, item in engine_items.items():
        if not isinstance(item, dict):
            issues.append(f"{code}: Engine FMS item invalid")
            continue
        if item.get("kind") != "tiered":
            continue
        entries = item.get("tiers")
        if not isinstance(entries, list) or any(
            not isinstance(row, tuple) or len(row) < 2
            or not isinstance(row[0], str) or not isinstance(row[1], int)
            for row in entries
        ):
            issues.append(f"{code}: Engine FMS tiers invalid")
            continue
        engine_tiers[code] = [row[0] for row in entries]
        if [row[1] for row in entries] != [3, 2]:
            issues.append(f"{code}: Engine FMS tier grades invalid")
    for code in sorted(java_tiers.keys() - engine_tiers.keys()):
        issues.append(f"Java-only FMS tiered item: {code}")
    for code in sorted(engine_tiers.keys() - java_tiers.keys()):
        issues.append(f"Engine-only FMS tiered item: {code}")
    for code in sorted(java_tiers.keys() & engine_tiers.keys()):
        if java_tiers[code] != engine_tiers[code]:
            issues.append(f"{code}: FMS tier condition or order mismatch")
    return issues


def compare_contract(java: dict, engine_rows: list) -> list[str]:
    issues = []
    engine = {}
    for item in engine_rows:
        if not isinstance(item, dict) or not isinstance(item.get("code"), str):
            issues.append("Engine catalog contains malformed entry")
            continue
        code = item["code"]
        if code in engine:
            issues.append(f"Duplicate Engine code: {code}")
        engine[code] = item
    for code in sorted(java.keys() - engine.keys()):
        issues.append(f"Java-only test code: {code}")
    for code in sorted(engine.keys() - java.keys()):
        issues.append(f"Engine-only test code: {code}")
    for code in sorted(java.keys() & engine.keys()):
        actual = engine[code]
        if actual.get("family") != java[code]["family"]:
            issues.append(f"{code}: family mismatch")
        sides = actual.get("sides")
        if not isinstance(sides, list) or len(sides) != len(set(sides)):
            issues.append(f"{code}: invalid Engine side list")
        elif set(sides) != set(java[code]["sides"]):
            issues.append(f"{code}: permitted side mismatch")
    return issues


def git_info(path: Path) -> dict:
    def git(*args: str) -> str:
        return subprocess.check_output(
            ["git", "-C", str(path), *args], text=True
        ).strip()

    return {
        "head": git("rev-parse", "HEAD"),
        "branch": git("rev-parse", "--abbrev-ref", "HEAD"),
        "dirty": bool(git("status", "--porcelain")),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-tree", type=Path, required=True)
    parser.add_argument("--engine-tree", type=Path, required=True)
    args = parser.parse_args()

    java_tree = args.java_tree.resolve()
    engine_tree = args.engine_tree.resolve()
    path = java_tree / JAVA_CONSTANTS
    engine_src = engine_tree / "src"
    if not path.is_file() or not engine_src.is_dir():
        parser.error("Java constants file or Engine src tree missing; pass explicit checkout paths")
    java_source = path.read_text(encoding="utf-8")
    java = parse_java_registry(java_source)
    java_fms_tiers = parse_java_fms_conditions(java_source)
    sys.path.insert(0, str(engine_src))
    catalog = importlib.import_module("rehab_biomechanics.motion.tests_registry").catalog()
    fms_items = importlib.import_module("rehab_biomechanics.motion.scoring.fms").ITEMS
    issues = compare_contract(java, catalog) + compare_fms_tiers(java_fms_tiers, fms_items)
    print(json.dumps({
        "contract": "motion-test-catalog-families-sides-fms-tier-v2",
        "pass": not issues,
        "java": git_info(java_tree),
        "engine": git_info(engine_tree),
        "java_count": len(java),
        "engine_count": len(catalog),
        "fms_tiered_count": len(java_fms_tiers),
        "issues": issues,
        "note": "Static source/catalog comparison only; not an API E2E or release acceptance",
    }, ensure_ascii=False, indent=2))
    return 0 if not issues else 1


if __name__ == "__main__":
    sys.exit(main())
