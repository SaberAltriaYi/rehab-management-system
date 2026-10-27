"""Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
Append-only report request migration and actual current-read SQL; synthetic only.
"""
from concurrent.futures import ThreadPoolExecutor
import hashlib
import os
from pathlib import Path
import re
import secrets
import threading
import unittest
from synthetic_mysql import SyntheticMySQL
import test_report_version_mysql as sessions

ROOT = Path(__file__).resolve().parents[2]
KEY_HASH = hashlib.sha256(b"019f1234-abcd-4abc-8abc-0123456789ab").hexdigest()
FINGERPRINT = hashlib.sha256(b"SYNTHETIC_PARAMETERS").hexdigest()


def bind(sql, values):
    if sql.count("?") != len(values):
        raise ValueError("synthetic SQL placeholder contract changed")
    for value in values:
        if isinstance(value, int):
            literal = str(value)
        elif isinstance(value, str) and re.fullmatch(r"[a-f0-9]{64}", value):
            literal = "'" + value + "'"
        else:
            raise ValueError("test refuses non-numeric/non-hash SQL bindings")
        sql = sql.replace("?", literal, 1)
    return sql


@unittest.skipUnless(os.environ.get("REHAB_TEST_MYSQL") == "1", "explicit isolated MySQL opt-in required")
class ReportRequestsMySQLTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mysql = SyntheticMySQL()
        cls.addClassCleanup(cls.mysql.close)
        cls.mysql.start()
        cls.migration = (ROOT / "sql/mysql/rehab-report-generation-request-v1.sql").read_text()
        report_source = ROOT / "yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/report"
        cls.queries = {}
        for name, file in (("FIND_SQL", "RehabReportGenerationRequests.java"),
                           ("RESULT_SQL", "RehabReportGenerationRequests.java"),
                           ("LOCK_ASSESSMENT_SQL", "RehabReportVersionSequence.java"),
                           ("VERSION_SQL", "RehabReportGenerationContract.java"),
                           ("ENGINE_SQL", "RehabReportGenerationContract.java"),
                           ("PK_SQL", "RehabReportGenerationContract.java"),
                           ("SHAPE_SQL", "RehabReportGenerationContract.java")):
            match = re.search(r'static final String ' + name + r' = "([^"]+)";', (report_source / file).read_text())
            if not match:
                raise RuntimeError("production SQL contract changed; review synthetic MySQL proof")
            cls.queries[name] = match[1]
        contract_source = (report_source / "RehabReportGenerationContract.java").read_text()
        cls.contract_checksum = re.search(r'static final String CHECKSUM = "([a-f0-9]{64})";', contract_source)[1]

    def setUp(self):
        self.database = "synthetic_requests_" + secrets.token_hex(4)
        self.mysql.sql(f"CREATE DATABASE `{self.database}`;")
        self.mysql.sql("""
CREATE TABLE rehab_assessment_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BOOLEAN DEFAULT FALSE) ENGINE=InnoDB;
CREATE TABLE rehab_report(id BIGINT PRIMARY KEY,tenant_id BIGINT,assessment_id BIGINT,generated_by BIGINT,
 deleted BOOLEAN DEFAULT FALSE,original_payload VARCHAR(64)) ENGINE=InnoDB;
INSERT INTO rehab_assessment_record VALUES(801,71,FALSE);
INSERT INTO rehab_report VALUES(100,71,801,51,FALSE,'SYNTHETIC_ORIGINAL_UNCHANGED');
""", self.database)

    def sql(self, query, check=True):
        return self.mysql.sql(query, self.database, check)

    def session(self):
        session = sessions.Session(self.mysql, self.database)
        self.addCleanup(session.close)
        return session

    def insert(self, tenant=71, operator=51):
        return ("INSERT INTO rehab_report_generation_request(tenant_id,assessment_id,operator_user_id,request_key_hash,"
                "request_fingerprint,report_id,report_no,report_version,fallback_used) VALUES "
                f"({tenant},801,{operator},'{KEY_HASH}','{FINGERPRINT}',100,'SYNTHETIC-100',1,b'1')")

    def test_read_only_contract_queries_require_fixed_ledger_and_transactional_request_shape(self):
        self.assertEqual(hashlib.sha256(self.migration.encode()).hexdigest(), self.contract_checksum)
        # These are the exact metadata queries consumed by the Java readiness guard.
        version = self.queries["VERSION_SQL"].replace("?", "'036'")
        self.assertNotEqual(0, self.sql(version, False).returncode)
        self.sql("CREATE TABLE internal_schema_history(version VARCHAR(8) PRIMARY KEY,checksum CHAR(64)) ENGINE=InnoDB")
        self.assertEqual("", self.sql(version).stdout)
        self.sql(f"INSERT INTO internal_schema_history VALUES('036','{self.contract_checksum}')")
        self.assertEqual(self.contract_checksum, self.sql(version).stdout.strip())
        self.assertNotEqual(0, self.sql(self.queries["SHAPE_SQL"], False).returncode)
        self.sql(self.migration)
        self.assertEqual("InnoDB", self.sql(self.queries["ENGINE_SQL"]).stdout.strip())
        self.assertEqual(["tenant_id", "assessment_id", "operator_user_id", "request_key_hash"],
                         self.sql(self.queries["PK_SQL"]).stdout.splitlines())
        self.assertEqual("", self.sql(self.queries["SHAPE_SQL"]).stdout)
        self.sql("ALTER TABLE rehab_report_generation_request DROP COLUMN fallback_message")
        self.assertNotEqual(0, self.sql(self.queries["SHAPE_SQL"], False).returncode)
        # Original synthetic report content has not been queried or modified by readiness.
        self.assertEqual("SYNTHETIC_ORIGINAL_UNCHANGED", self.sql("SELECT original_payload FROM rehab_report").stdout.strip())

    def test_migration_preserves_original_reports_and_contains_no_raw_request_or_clinical_columns(self):
        before = self.sql("SELECT * FROM rehab_report").stdout
        self.sql(self.migration)
        self.assertEqual(before, self.sql("SELECT * FROM rehab_report").stdout)
        columns = self.sql("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() "
                           "AND table_name='rehab_report_generation_request' ORDER BY ordinal_position").stdout.splitlines()
        self.assertEqual(["tenant_id", "assessment_id", "operator_user_id", "request_key_hash", "request_fingerprint",
                          "report_id", "report_no", "report_version", "fallback_used", "fallback_message", "create_time"], columns)

    def test_unknown_preexisting_table_is_not_overwritten(self):
        self.sql("CREATE TABLE rehab_report_generation_request(marker VARCHAR(64)); INSERT INTO rehab_report_generation_request VALUES('SYNTHETIC_PRESERVE')")
        self.assertNotEqual(0, self.sql(self.migration, False).returncode)
        self.assertEqual("SYNTHETIC_PRESERVE\n", self.sql("SELECT marker FROM rehab_report_generation_request").stdout)

    def test_unique_scope_rejects_duplicate_but_does_not_mix_operators_or_tenants(self):
        self.sql(self.migration)
        self.sql(self.insert())
        self.assertNotEqual(0, self.sql(self.insert(), False).returncode)
        self.sql(self.insert(operator=52))
        self.sql(self.insert(tenant=72))
        for tenant, operator, expected in ((71, 51, 1), (71, 52, 1), (72, 51, 1), (72, 52, 0)):
            query = bind(self.queries["FIND_SQL"], (tenant, 801, operator, KEY_HASH))
            self.assertEqual(expected, len(self.sql(query).stdout.splitlines()))
        # Corrupted/reassigned targets cannot masquerade as the original result.
        self.assertEqual("100\n", self.sql(bind(self.queries["RESULT_SQL"], (71, 100, 801, 51))).stdout)
        self.assertEqual("", self.sql(bind(self.queries["RESULT_SQL"], (72, 100, 801, 51))).stdout)
        self.assertEqual("", self.sql(bind(self.queries["RESULT_SQL"], (71, 100, 802, 51))).stdout)

    def test_repeatable_read_waiter_sees_completed_request_even_with_older_empty_snapshot(self):
        self.sql(self.migration)
        a, b = self.session(), self.session()
        for session in (a, b):
            session.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION")
            self.assertEqual(["0"], session.execute("SELECT COUNT(*) FROM rehab_report_generation_request"))
        lock = bind(self.queries["LOCK_ASSESSMENT_SQL"], (71, 801))
        a.execute(lock)
        a.execute(self.insert())
        attempting = threading.Event()

        def retry():
            attempting.set()
            b.execute(lock)
            self.assertEqual(["0"], b.execute("SELECT COUNT(*) FROM rehab_report_generation_request"))
            rows = b.execute(bind(self.queries["FIND_SQL"], (71, 801, 51, KEY_HASH)))
            self.assertEqual(1, len(rows))
            self.assertEqual("100", rows[0].split("\t")[1])
            b.execute("COMMIT")

        with ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(retry)
            self.assertTrue(attempting.wait(5))
            a.execute("COMMIT")
            future.result(timeout=20)
        self.assertEqual("1\n", self.sql("SELECT COUNT(*) FROM rehab_report_generation_request").stdout)

    def test_completed_ledger_survives_backup_restore_and_rollback_never_reserves_a_key(self):
        self.sql(self.migration)
        session = self.session()
        session.execute("START TRANSACTION")
        session.execute(self.insert())
        session.execute("ROLLBACK")
        self.assertEqual("0\n", self.sql("SELECT COUNT(*) FROM rehab_report_generation_request").stdout)
        self.sql(self.insert())
        dump = self.mysql.command(["exec", "--env", "MYSQL_PWD", self.mysql.container, "mysqldump", "-uroot",
                                   "--no-tablespaces", "--set-gtid-purged=OFF", self.database]).stdout
        restored = "synthetic_restored_" + secrets.token_hex(4)
        self.mysql.sql(f"CREATE DATABASE `{restored}`;")
        self.mysql.sql(dump, restored)
        query = bind(self.queries["FIND_SQL"], (71, 801, 51, KEY_HASH))
        self.assertEqual(self.sql(query).stdout, self.mysql.sql(query, restored).stdout)
        self.assertEqual(self.sql("SELECT * FROM rehab_report").stdout, self.mysql.sql("SELECT * FROM rehab_report", restored).stdout)


if __name__ == "__main__":
    unittest.main()
