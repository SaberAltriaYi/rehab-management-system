#!/usr/bin/env python3
"""Check local input availability WITHOUT reading/hydrating cloud file contents.
A pass only means paths appear locally materialized; it does not prove a build.
No downloads, deletes, environment inspection, Git changes or credential reads.
"""
import argparse
import json
import os
from pathlib import Path
import stat
import sys

DATALESS_FLAG = 0x40000000  # Darwin SF_DATALESS
EXCLUDED = {'node_modules', 'target', 'dist', 'dist-internal', '.idea', '.vscode',
            '__pycache__', '_archives', 'certs', 'secrets'}


def is_dataless(info):
    return bool(getattr(info, 'st_flags', 0) & DATALESS_FLAG)


def inspect(scopes, root, include_dependencies=False):
    issues = []
    checked = 0
    excluded = EXCLUDED - {'node_modules'} if include_dependencies else EXCLUDED

    def visit(path):
        nonlocal checked
        try:
            info = path.lstat()
        except OSError as exc:
            issues.append({'path': str(path.relative_to(root)), 'reason': type(exc).__name__})
            return
        checked += 1
        if is_dataless(info):
            issues.append({'path': str(path.relative_to(root)), 'reason': 'dataless'})
            if stat.S_ISDIR(info.st_mode):
                return
        if stat.S_ISDIR(info.st_mode):
            try:
                children = sorted(path.iterdir())
            except OSError as exc:
                issues.append({'path': str(path.relative_to(root)), 'reason': type(exc).__name__})
                return
            for child in children:
                if child.name not in excluded:
                    visit(child)
        # Deliberately never follow symlinks out of the requested tree.

    for scope in scopes:
        visit(scope)
    return {'status': 'blocked' if issues else 'materialized', 'checked_entries': checked,
            'issue_count': len(issues), 'issues': issues}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--include-dependencies', action='store_true')
    parser.add_argument('--json', action='store_true')
    args = parser.parse_args()
    root = args.root.resolve()
    names = ['.git', 'pom.xml', 'yudao-dependencies', 'yudao-framework', 'yudao-server',
             'yudao-ui/yudao-ui-admin-vue3-app', 'yudao-ui/yudao-ui-rehab-uniapp',
             'desktop/scripts', 'deploy/internal', 'script/rehab']
    scopes = [root / n for n in names] + sorted(root.glob('yudao-module-*'))
    report = inspect(scopes, root, args.include_dependencies)
    report['root'] = str(root)
    report['includes_dependencies'] = args.include_dependencies
    report['meaning'] = 'Availability preflight only; not build/test/release approval'
    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        print('Local inputs:', report['status'], '; unavailable:', report['issue_count'])
        for issue in report['issues'][:30]:
            print(issue['reason'] + ': ' + issue['path'])
        if report['issues']:
            print('Download/Keep Downloaded in Finder first. Do not reset or replace cloud-only worktree files.')
    return 2 if report['issues'] else 0


if __name__ == '__main__':
    sys.exit(main())
