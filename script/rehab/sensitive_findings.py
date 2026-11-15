#!/usr/bin/env python3
"""Review grep findings without printing credential contents.

Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT
"""

import re
import sys


MAX_BYTES = 1024 * 1024
PARSER_PATH = (
    "yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/"
    "service/hep/RehabHepTokenService.java"
)
KEY_MARKER = "-----BEGIN " + "PRIVATE KEY-----"
PARSER_LITERAL = f'String base64=pem.replace("{KEY_MARKER}","")'


def review(records):
    """Allow one exact marker-only parser expression, never an entire file."""
    if len(records.encode("utf-8")) > MAX_BYTES:
        raise ValueError("Finding input exceeds limit")
    findings = []
    for record in records.splitlines():
        if not record:
            continue
        match = re.fullmatch(r"([A-Za-z0-9_./-]+):([0-9]+):(.*)", record)
        if not match:
            raise ValueError("Malformed finding; manual review required")
        path, line, source = match.groups()
        if path == PARSER_PATH and source.strip() == PARSER_LITERAL:
            continue
        findings.append((path, int(line)))
    return findings


def main():
    try:
        data = sys.stdin.read(MAX_BYTES + 1)
        findings = review(data)
    except (ValueError, UnicodeError):
        print("FAIL: secret finding input could not be safely reviewed", file=sys.stderr)
        return 2
    for path, line in findings:
        print(f"  {path}:{line}: [sensitive match redacted]")
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
