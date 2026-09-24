#!/usr/bin/env python3
"""Opt-in ERP candidate migration 023 test in one random disposable MySQL database.

Never connects to an application DB; requires a dedicated synthetic test container.
Checks schema coverage and inventory decimal arithmetic, not business API flows.
"""
import os
from pathlib import Path
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / 'sql/mysql/rehab-erp-core-v1.sql'
PREFIX = 'rehab_erp_schema_test_'


def main():
    container = os.environ.get('REHAB_TEST_MYSQL_CONTAINER', '')
    if not container or os.environ.get('REHAB_TEST_MYSQL_ACK') != 'DEDICATED_SYNTHETIC_CONTAINER':
        raise SystemExit('A dedicated test container and explicit acknowledgement are required')
    database = PREFIX + uuid.uuid4().hex[:20]
    created = False

    def sql(text, use_db=False):
        cmd = ['docker', 'exec', '-i', container, 'sh', '-c',
               'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names' +
               (' "$1"' if use_db else ''), 'erp-test']
        if use_db:
            cmd.append(database)
        return subprocess.run(cmd, input=text, text=True, capture_output=True, check=True, timeout=45).stdout.strip()

    try:
        sql('CREATE DATABASE `' + database + '` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;')
        created = True
        schema = SCRIPT.read_text()
        table_names = re.findall(r'^CREATE TABLE `([^`]+)`', schema, re.M)
        assert len(table_names) == len(set(table_names)) == 33
        sql(schema, True)
        actual = set(sql('SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE();',
                         True).splitlines())
        assert actual == set(table_names), sorted(actual ^ set(table_names))
        sql("""INSERT INTO erp_stock (product_id, warehouse_id, count, tenant_id)
               VALUES (100, 1, 1.125000, 101), (100, 1, 2.000000, 202);
               INSERT INTO erp_product (name, bar_code, sale_price, tenant_id)
               VALUES ('synthetic gauze', 'TEST-ONLY', 12.345678, 101);
               UPDATE erp_stock SET count=count+0.125 WHERE tenant_id=101 AND product_id=100;
        """, True)
        assert sql('SELECT count FROM erp_stock WHERE tenant_id=101;', True) == '1.250000'
        assert sql('SELECT count FROM erp_stock WHERE tenant_id=202;', True) == '2.000000'
        assert sql('SELECT sale_price FROM erp_product WHERE tenant_id=101;', True) == '12.345678'
        assert sql('SELECT COUNT(*) FROM erp_stock WHERE tenant_id=101 AND product_id=100;', True) == '1'
        print('PASS: 023 all 33 ERP tables, decimal inventory and synthetic tenant stock')
    finally:
        if created and database.startswith(PREFIX):
            sql('DROP DATABASE `' + database + '`;')


if __name__ == '__main__':
    main()
