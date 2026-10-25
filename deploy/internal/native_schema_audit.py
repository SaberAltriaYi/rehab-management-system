"""Explicit legacy snapshot adoption, only on a named upgrade copy.

No historical seed/cleanup scripts are executed. A separately built reference
database supplies metadata, not patient rows. Unknown differences fail closed.
Credentials come from a private MySQL option file, never command-line passwords.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess

ROOT = Path(__file__).resolve().parents[2]
COPY_NAME = re.compile(r"rehab_upgrade_[a-z0-9_]{6,60}")
REFERENCE_NAME = re.compile(r"rehab_reference_[a-z0-9_]{6,60}")
CREATE_TABLE = re.compile(
    r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)", re.I
)


def manifest():
    rows = []
    for line in (ROOT / "deploy/internal/migrations.manifest").read_text().splitlines():
        if not line or line.startswith("#"):
            continue
        version, checksum, relative, description = line.split("|")
        content = (ROOT / relative).read_bytes()
        if hashlib.sha256(content).hexdigest() != checksum:
            raise ValueError("fixed migration checksum mismatch: " + version)
        rows.append((version, checksum, relative, description))
    return rows


class Mysql:
    def __init__(self, config, port=3306):
        path = Path(config)
        if path.is_symlink() or not path.is_file():
            raise ValueError("private client configuration required")
        info = path.stat()
        if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) != 0o600:
            raise ValueError(
                "client configuration must be owned by current user, mode 600"
            )
        if not 1 <= int(port) <= 65535:
            raise ValueError("invalid port")
        self.args = [
            "mysql",
            "--defaults-extra-file=" + str(path.resolve()),
            "--protocol=TCP",
            "--host=127.0.0.1",
            "--port=" + str(port),
            "--batch",
            "--raw",
            "--skip-column-names",
        ]

    def sql(self, database, query):
        if not re.fullmatch(r"[a-zA-Z0-9_-]+", database):
            raise ValueError("invalid database")
        p = subprocess.run(
            self.args + ["--database=" + database],
            input=query,
            capture_output=True,
            text=True,
            timeout=120,
        )
        if p.returncode:
            # Do not echo SQL, connection options or database patient payloads.
            raise RuntimeError(
                "MySQL metadata/adoption operation failed; private operator log required"
            )
        return p.stdout.strip()

    def schema(self, database, tables):
        result = {}
        for table in sorted(tables):
            if not re.fullmatch(r"[a-zA-Z0-9_]+", table):
                raise ValueError("invalid table")
            query = f"""
SELECT JSON_OBJECT('columns',
 (SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('name',column_name,'ordinal',ordinal_position,
 'type',column_type,'nullable',is_nullable,'default',column_default,'extra',extra,
 'collation',collation_name)),JSON_ARRAY()) FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='{table}'),
 'indexes',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('name',index_name,'seq',seq_in_index,
 'non_unique',non_unique,'column',column_name,'sub_part',sub_part,'type',index_type,'direction',collation)),JSON_ARRAY())
 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='{table}'),
 'foreign_keys',(SELECT COALESCE(JSON_ARRAYAGG(JSON_OBJECT('name',k.constraint_name,'column',k.column_name,
 'referenced_table',k.referenced_table_name,'referenced_column',k.referenced_column_name,
 'reference_scope',IF(k.referenced_table_schema=DATABASE(),'self','external'),
 'update_rule',r.update_rule,'delete_rule',r.delete_rule)),JSON_ARRAY())
 FROM information_schema.key_column_usage k JOIN information_schema.referential_constraints r
 ON r.constraint_schema=k.constraint_schema AND r.constraint_name=k.constraint_name AND r.table_name=k.table_name
 WHERE k.table_schema=DATABASE() AND k.table_name='{table}' AND k.referenced_table_name IS NOT NULL),
 'options',(SELECT JSON_OBJECT('engine',engine,'collation',table_collation)
 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='{table}'));
"""
            result[table] = json.loads(self.sql(database, query))
        return result


def canonical(value):
    if isinstance(value, list):
        return sorted(
            (canonical(v) for v in value), key=lambda v: json.dumps(v, sort_keys=True)
        )
    if isinstance(value, dict):
        return {k: canonical(v) for k, v in sorted(value.items())}
    return value


def differences(expected, actual):
    """Extra legacy columns/indexes may stay; required definitions cannot drift."""
    issues = []
    for table, ref in expected.items():
        current = actual.get(table, {})
        if not ref.get("columns") or not current.get("columns"):
            issues.append(table + ": table missing")
            continue
        cols = {v["name"]: v for v in current["columns"]}
        for want in ref["columns"]:
            got = cols.get(want["name"])
            if got is None:
                issues.append(table + "." + want["name"] + ": column missing")
                continue
            # Position and DEFAULT_GENERATED are server-version details. No type,
            # nullability, declared default, auto increment or on-update relaxation.
            for key in ("type", "nullable", "default", "collation"):
                if got.get(key) != want.get(key):
                    issues.append(table + "." + want["name"] + ": " + key + " mismatch")
            clean = lambda s: " ".join(
                (s or "").lower().replace("default_generated", "").split()
            )
            if clean(got.get("extra")) != clean(want.get("extra")):
                issues.append(table + "." + want["name"] + ": extra mismatch")
        for section in ("indexes", "foreign_keys"):

            def semantic(values):
                if section == "indexes":
                    grouped = {}
                    for value in values:
                        grouped.setdefault(value["name"], []).append(value)
                    return [
                        canonical(
                            [
                                {k: v for k, v in row.items() if k != "name"}
                                for row in group
                            ]
                        )
                        for group in grouped.values()
                    ]
                return [
                    canonical({k: v for k, v in row.items() if k != "name"})
                    for row in values
                ]

            have = semantic(current.get(section, []))
            for value in semantic(ref.get(section, [])):
                if value not in have:
                    issues.append(table + ": required " + section + " mismatch")
        if ref.get("options") != current.get("options"):
            issues.append(table + ": table options mismatch")
    return sorted(set(issues))


def audit(db, database, reference, mode, output, adopt=False):
    if (
        not COPY_NAME.fullmatch(database)
        or not REFERENCE_NAME.fullmatch(reference)
        or database == reference
    ):
        raise ValueError(
            "only explicit separate upgrade-copy/reference databases are accepted"
        )
    rows = manifest()
    versions = range(1, 20) if mode == "baseline" else (24, 25)
    chosen = [row for row in rows if int(row[0]) in versions]
    tables = set()
    for row in chosen:
        tables.update(CREATE_TABLE.findall((ROOT / row[2]).read_text()))
    expected = db.schema(reference, tables)
    actual = db.schema(database, tables)
    issues = differences(expected, actual)
    proof = {
        "format": "rehab-native-schema-audit/1",
        "mode": mode,
        "database": database,
        "reference": reference,
        "issues": issues,
        "schema_sha256": hashlib.sha256(
            json.dumps(canonical(actual), sort_keys=True).encode()
        ).hexdigest(),
        "reference_sha256": hashlib.sha256(
            json.dumps(canonical(expected), sort_keys=True).encode()
        ).hexdigest(),
        "manifest": [{"version": r[0], "checksum": r[1]} for r in chosen],
        "seed_cleanup_replayed": False,
        "historical_execution_claimed": False,
    }
    output = Path(output)
    if output.exists() or output.is_symlink():
        raise ValueError("audit output must be a new file")
    output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    with output.open("x", encoding="utf-8") as f:
        os.chmod(output, 0o600)
        json.dump(proof, f, ensure_ascii=False, indent=2)
    if issues or not adopt:
        return proof
    if mode == "baseline":
        exists = db.sql(
            database,
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='internal_schema_history';",
        )
        if exists != "0":
            raise ValueError(
                "baseline adoption requires absent ledger; never overwrite history"
            )
        ddl = (
            (ROOT / "deploy/internal/init-schema-history.sql")
            .read_text()
            .split("INSERT INTO `internal_schema_history`", 1)[0]
        )
        db.sql(database, ddl)
    else:
        present = db.sql(
            database,
            "SELECT COUNT(*) FROM internal_schema_history WHERE CAST(version AS UNSIGNED) BETWEEN 1 AND 23;",
        )
        if present != "23":
            raise ValueError("motion adoption requires all preceding versions")
    # baseline=1 explicitly means audited legacy snapshot, not executed SQL.
    # Writes are confined to the upgrade copy after re-reading all metadata.
    if differences(expected, db.schema(database, tables)):
        raise ValueError("schema changed during audit")
    statements = ["START TRANSACTION;"]
    for version, checksum, relative, description in chosen:
        if any("'" in s for s in (relative, description)):
            raise ValueError("unsupported manifest text")
        statements.append(
            "INSERT INTO internal_schema_history(version,checksum,script_path,description,installed_by,baseline,execution_ms) "
            f"VALUES ('{version}','{checksum}','{relative}','{description}','native-audited-snapshot',b'1',0);"
        )
    statements.append("COMMIT;")
    db.sql(database, "\n".join(statements))
    return proof


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=("baseline", "motion"))
    p.add_argument("--client-config", required=True)
    p.add_argument("--port", type=int, default=3306)
    p.add_argument("--database", required=True)
    p.add_argument("--reference", required=True)
    p.add_argument("--output", required=True)
    p.add_argument("--adopt-reviewed-copy", action="store_true")
    args = p.parse_args()
    result = audit(
        Mysql(args.client_config, args.port),
        args.database,
        args.reference,
        args.mode,
        args.output,
        args.adopt_reviewed_copy,
    )
    print(
        json.dumps(
            {
                "issues": result["issues"],
                "adopted": args.adopt_reviewed_copy and not result["issues"],
            },
            ensure_ascii=False,
        )
    )
    if result["issues"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
