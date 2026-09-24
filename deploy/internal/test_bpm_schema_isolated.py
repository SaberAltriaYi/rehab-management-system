#!/usr/bin/env python3
"""Opt-in BPM candidate migration test, dedicated disposable MySQL 8.4 only.

Does not test Flowable engine tables or server-level tenant interceptor. Requires
REHAB_TEST_MYSQL_CONTAINER and ACK on a dedicated test container, never prod.
"""
import os
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'sql/mysql/rehab-bpm-core-v1.sql'
PREFIX = 'rehab_bpm_schema_test_'
TABLES = ('bpm_category', 'bpm_form', 'bpm_process_definition_info',
          'bpm_process_expression', 'bpm_process_listener', 'bpm_user_group',
          'bpm_oa_leave', 'bpm_process_instance_copy')


def main():
    container = os.environ.get('REHAB_TEST_MYSQL_CONTAINER', '')
    if not container or os.environ.get('REHAB_TEST_MYSQL_ACK') != 'DEDICATED_SYNTHETIC_CONTAINER':
        raise SystemExit('A dedicated test container and explicit acknowledgement are required')
    database = PREFIX + uuid.uuid4().hex[:20]
    created = False

    def sql(text, use_db=False):
        cmd = ['docker', 'exec', '-i', container, 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names' +
               (' "$1"' if use_db else ''), 'bpm-test']
        if use_db:
            cmd.append(database)
        return subprocess.run(cmd, input=text, text=True, capture_output=True, check=True, timeout=45).stdout.strip()

    try:
        sql('CREATE DATABASE `' + database + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        created = True
        sql(SCRIPT.read_text(), True)
        result = sql('SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE();', True)
        assert set(result.splitlines()) == set(TABLES), sorted(result.splitlines())
        sql("""INSERT INTO bpm_form (name, status, conf, fields, tenant_id)
                VALUES ('synthetic', 0, '{"form":1}', '["a","b"]', 101);
                INSERT INTO bpm_process_definition_info
                  (model_id, form_fields, start_user_ids, process_id_rule, tenant_id)
                VALUES ('synthetic-model', '["a"]', '101,202', '{"type":1}', 101);
                INSERT INTO bpm_user_group (name, user_ids, tenant_id)
                VALUES ('synthetic group', '[101,202]', 101);
                INSERT INTO bpm_oa_leave (user_id, status, tenant_id)
                VALUES (202, 0, 202);""", True)
        assert sql("SELECT fields FROM bpm_form WHERE tenant_id=101;", True) == '["a","b"]'
        assert sql("SELECT COUNT(*) FROM bpm_process_definition_info WHERE tenant_id=101 AND FIND_IN_SET('202', start_user_ids);", True) == '1'
        assert sql("SELECT COUNT(*) FROM bpm_oa_leave WHERE tenant_id=101;", True) == '0'
        print('PASS: 022 eight BPM DO tables, JSON/CSV handler columns, two synthetic tenants')
    finally:
        if created and database.startswith(PREFIX):
            sql('DROP DATABASE `' + database + '`;')


if __name__ == '__main__':
    main()
