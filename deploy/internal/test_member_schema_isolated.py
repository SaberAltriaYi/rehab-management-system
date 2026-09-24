#!/usr/bin/env python3
"""Opt-in schema-only test for candidate migration 021 on a disposable MySQL 8.4 container.

Never connect to an application database. A uniquely named synthetic database is
created and removed, and no pre-existing database/schema is queried or modified.
"""
import os
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'sql/mysql/rehab-member-remaining-v1.sql'
PREFIX = 'rehab_member_schema_test_'
TABLES = ('member_address', 'member_config', 'member_experience_record',
          'member_level_record', 'member_point_record', 'member_sign_in_config',
          'member_sign_in_record')


def main():
    container = os.environ.get('REHAB_TEST_MYSQL_CONTAINER', '')
    if not container or os.environ.get('REHAB_TEST_MYSQL_ACK') != 'DEDICATED_SYNTHETIC_CONTAINER':
        raise SystemExit('A dedicated disposable test container and explicit acknowledgement are required')
    database = PREFIX + uuid.uuid4().hex[:20]
    created = False

    def sql(text, use_db=False):
        cmd = ['docker', 'exec', '-i', container, 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names' +
               (' "$1"' if use_db else ''), 'member-test']
        if use_db:
            cmd.append(database)
        return subprocess.run(cmd, input=text, text=True, capture_output=True, check=True, timeout=45).stdout.strip()

    try:
        sql('CREATE DATABASE `' + database + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        created = True
        sql(SCRIPT.read_text(), True)
        names = set(sql('SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE();',
                        True).splitlines())
        assert names == set(TABLES), sorted(names)
        sql("""INSERT INTO member_address
            (user_id, name, mobile, area_id, detail_address, tenant_id)
            VALUES (100, 'synthetic A', '00000000001', 1, 'synthetic', 101),
                   (200, 'synthetic B', '00000000002', 1, 'synthetic', 202);
            INSERT INTO member_level_record (user_id, experience, user_experience, tenant_id)
            VALUES (100, -1, 0, 101);
            INSERT INTO member_config (tenant_id) VALUES (101), (202);
            INSERT INTO member_sign_in_record (user_id, day, tenant_id) VALUES (100, 1, 101);
        """, True)
        assert sql('SELECT name FROM member_address WHERE tenant_id=101 AND user_id=100 AND deleted=b\'0\';',
                   True) == 'synthetic A'
        assert sql('SELECT COUNT(*) FROM member_address WHERE tenant_id=101 AND user_id=200;', True) == '0'
        assert sql('SELECT COUNT(*) FROM member_level_record WHERE level_id IS NULL;', True) == '1'
        assert sql('SELECT COUNT(*) FROM member_config WHERE point_trade_give_point=0;', True) == '2'
        sql('UPDATE member_address SET deleted=b\'1\' WHERE tenant_id=101 AND user_id=100;', True)
        assert sql('SELECT COUNT(*) FROM member_address WHERE deleted=b\'0\';', True) == '1'
        print('PASS: 021 seven Member tables; synthetic tenants, nullable cancellation, defaults, soft delete')
    finally:
        if created and database.startswith(PREFIX):
            sql('DROP DATABASE `' + database + '`;')


if __name__ == '__main__':
    main()
