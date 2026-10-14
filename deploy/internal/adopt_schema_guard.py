#!/usr/bin/env python3
"""Fail-closed, offline parser for legacy pure-CREATE migration adoption.

Compares expected DDL with read-only information_schema metadata supplied via
stdin. Never reads patient rows, executes SQL, or contacts a database itself.
"""
import json
import re
import sys
from pathlib import Path

CREATE = re.compile(r'^CREATE TABLE \x60([A-Za-z0-9_]+)\x60 \($')
COLUMN = re.compile(r'^  \x60([A-Za-z0-9_]+)\x60 ([A-Z]+(?:\([0-9,]+\))?(?: UNSIGNED)?)(.*?)(?:,)?$')
INDEX = re.compile(r'^  (PRIMARY KEY|UNIQUE KEY \x60([A-Za-z0-9_]+)\x60|KEY \x60([A-Za-z0-9_]+)\x60) \(([^)]+)\),?$')
END = re.compile(r'^\) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci(?: COMMENT=.*)?;$')
DEFAULT = re.compile(r"\bDEFAULT\s+(B'[01]'|'[^']*'|CURRENT_TIMESTAMP(?:\(\))?|NULL|-?\d+(?:\.\d+)?)", re.I)


def normalize_default(value):
    if value is None or str(value).upper() == 'NULL':
        return None
    value = str(value).strip()
    if value.startswith("'") and value.endswith("'"):
        value = value[1:-1]
    if re.fullmatch(r'current_timestamp(?:\(\))?', value, re.I):
        return 'current_timestamp'
    return value.lower() if value.lower().startswith("b'") else value


def parse_schema(path):
    tables = {}
    active = None
    for number, line in enumerate(Path(path).read_text(encoding='utf-8').splitlines(), 1):
        line = line.rstrip('\r')
        if not line.strip() or line.startswith('--'):
            continue
        match = CREATE.fullmatch(line)
        if match and active is None:
            name = match.group(1)
            if name in tables:
                raise ValueError(f'duplicate table {name}')
            active = {'columns': [], 'indexes': []}
            tables[name] = active
            continue
        if active is None:
            raise ValueError(f'not pure CREATE TABLE at line {number}')
        if END.fullmatch(line):
            if not active['columns'] or not active['indexes']:
                raise ValueError(f'incomplete CREATE TABLE at line {number}')
            active = None
            continue
        col = COLUMN.fullmatch(line)
        if col:
            name, data_type, rest = col.groups()
            rest = re.sub(r"\s+COMMENT\s+'[^']*'\s*$", '', rest.rstrip(',')).strip()
            rest_upper = rest.upper()
            if not re.fullmatch(
                r"(?:NOT NULL|NULL)?(?:\s*DEFAULT\s+(?:B'[01]'|'[^']*'|CURRENT_TIMESTAMP(?:\(\))?|NULL|-?\d+(?:\.\d+)?))?"
                r"(?:\s+AUTO_INCREMENT)?(?:\s+ON UPDATE CURRENT_TIMESTAMP(?:\(\))?)?",
                rest, re.I,
            ):
                raise ValueError(f'unsupported column definition at line {number}: {name}')
            default = DEFAULT.search(rest)
            active['columns'].append({
                'name': name, 'type': data_type.lower(), 'nullable': 'NO' if 'NOT NULL' in rest_upper else 'YES',
                'default': normalize_default(default.group(1)) if default else None,
                'auto': 'AUTO_INCREMENT' in rest_upper,
                'on_update': 'ON UPDATE CURRENT_TIMESTAMP' in rest_upper,
            })
            continue
        index = INDEX.fullmatch(line)
        if index:
            kind, unique_name, ordinary_name, keys = index.groups()
            cols = re.findall(r'\x60([A-Za-z0-9_]+)\x60', keys)
            if not cols or re.sub(r'\x60[A-Za-z0-9_]+\x60|[\s,]', '', keys):
                raise ValueError(f'unsupported index at line {number}')
            active['indexes'].append({
                'name': 'PRIMARY' if kind == 'PRIMARY KEY' else unique_name or ordinary_name,
                'unique': kind == 'PRIMARY KEY' or kind.startswith('UNIQUE'), 'columns': cols,
            })
            continue
        raise ValueError(f'unsupported CREATE TABLE declaration at line {number}')
    if active is not None or not tables:
        raise ValueError('unfinished or empty pure CREATE TABLE script')
    return tables


def actual_signature(columns, indexes):
    if not isinstance(columns, list) or not isinstance(indexes, list):
        raise ValueError('invalid information_schema metadata')
    cols = []
    for item in sorted(columns, key=lambda row: int(row['ordinal'])):
        extra = (item.get('extra') or '').lower()
        cols.append({
            'name': item['name'], 'type': item['type'].lower(), 'nullable': item['nullable'].upper(),
            'default': normalize_default(item.get('default')),
            'auto': 'auto_increment' in extra,
            'on_update': 'on update current_timestamp' in extra,
        })
    grouped = {}
    for item in indexes:
        name = item['name']
        if item.get('sub_part') is not None or (item.get('index_type') or '').upper() != 'BTREE':
            raise ValueError('unsupported index prefix or type')
        entry = grouped.setdefault(name, {'name': name, 'unique': not bool(int(item['non_unique'])), 'columns': []})
        if entry['unique'] != (not bool(int(item['non_unique']))):
            raise ValueError('inconsistent index uniqueness')
        entry['columns'].append((int(item['seq']), item['column']))
    keys = []
    for entry in grouped.values():
        entry['columns'] = [x[1] for x in sorted(entry['columns'])]
        keys.append(entry)
    return {'columns': cols, 'indexes': sorted(keys, key=lambda item: item['name'])}


def verify(path, table, columns, indexes, table_options):
    expected = parse_schema(path).get(table)
    if expected is None:
        raise ValueError('table not declared in migration')
    actual = actual_signature(columns, indexes)
    if not isinstance(table_options, dict) or (
        (table_options.get('engine') or '').lower() != 'innodb'
        or (table_options.get('collation') or '').lower() != 'utf8mb4_unicode_ci'
    ):
        raise ValueError(f'{table}: storage engine or table collation mismatch')
    expected = {'columns': expected['columns'], 'indexes': sorted(expected['indexes'], key=lambda item: item['name'])}
    for field in ('columns', 'indexes'):
        if actual[field] != expected[field]:
            raise ValueError(f'{table}: {field} schema mismatch')
    return True


def main():
    try:
        if len(sys.argv) == 3 and sys.argv[1] == 'tables':
            print('\n'.join(parse_schema(sys.argv[2])))
        elif len(sys.argv) == 4 and sys.argv[1] == 'verify':
            columns = json.loads(sys.stdin.readline())
            indexes = json.loads(sys.stdin.readline())
            options = json.loads(sys.stdin.readline())
            verify(sys.argv[2], sys.argv[3], columns, indexes, options)
            print('PASS')
        else:
            raise ValueError('usage: adopt_schema_guard.py tables FILE | verify FILE TABLE')
    except (ValueError, KeyError, TypeError, OSError, json.JSONDecodeError) as error:
        print(f'FAIL: strict adopt schema verification: {error}', file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()
