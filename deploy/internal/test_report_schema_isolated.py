#!/usr/bin/env python3
"""Opt-in MySQL 8.4 smoke test for migration 020 in a new synthetic database.

Set REHAB_TEST_MYSQL_CONTAINER to a *dedicated disposable test container* and
REHAB_TEST_MYSQL_ACK=DEDICATED_SYNTHETIC_CONTAINER. Never point at patient data.
This test does not run historical bootstrap SQL or alter any existing schema.
"""
import os
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'sql/mysql/rehab-report-go-view-project-v1.sql'
PREFIX = 'rehab_report_schema_test_'


def main():
    container = os.environ.get('REHAB_TEST_MYSQL_CONTAINER', '')
    if not container or os.environ.get('REHAB_TEST_MYSQL_ACK') != 'DEDICATED_SYNTHETIC_CONTAINER':
        raise SystemExit('Only a dedicated test container is allowed; set opt-in container and acknowledgement.')
    database = PREFIX + uuid.uuid4().hex[:20]
    created = False

    def sql(text, *, use_db=False):
        cmd = ['docker', 'exec', '-i', container, 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names' +
               (' "$1"' if use_db else ''), 'report-test']
        if use_db:
            cmd.append(database)
        return subprocess.run(cmd, input=text, text=True, capture_output=True, check=True, timeout=45).stdout.strip()

    try:
        sql('CREATE DATABASE `' + database + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        created = True
        sql(SCRIPT.read_text(), use_db=True)
        result = sql('''SELECT data_type FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'report_go_view_project'
            AND column_name = 'status';''', use_db=True)
        assert result == 'tinyint', result
        sql('''INSERT INTO report_go_view_project (name, status, creator, tenant_id, content)
               VALUES ('synthetic alpha', 1, '100', 1001, '{"ok":true}'),
                      ('synthetic beta', 0, '200', 2002, '{"ok":false}');''', use_db=True)
        assert sql('''SELECT name FROM report_go_view_project WHERE tenant_id=1001
                      AND creator='100' AND deleted=b'0';''', use_db=True) == 'synthetic alpha'
        assert sql('''SELECT COUNT(*) FROM report_go_view_project WHERE tenant_id=1001
                      AND creator='200' AND deleted=b'0';''', use_db=True) == '0'
        sql("UPDATE report_go_view_project SET deleted=b'1' WHERE tenant_id=1001 AND creator='100';",
            use_db=True)
        assert sql("SELECT COUNT(*) FROM report_go_view_project WHERE deleted=b'0';",
                   use_db=True) == '1'
        print('PASS: migration 020 table, integer status, two synthetic tenants and logical delete')
    finally:
        if created and database.startswith(PREFIX):
            sql('DROP DATABASE `' + database + '`;')


if __name__ == '__main__':
    main()
