"""Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
Diagnostic RR evidence, NOT proof that production snapshot consistency is fixed.
Uses the actual assessment-lock SQL and explicit synthetic source projections.
"""
from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
import re
import secrets
import threading
import unittest
from synthetic_mysql import SyntheticMySQL
from test_report_version_mysql import Session

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.environ.get("REHAB_TEST_MYSQL") == "1", "explicit isolated MySQL opt-in required")
class AssessmentSnapshotEvidenceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mysql = SyntheticMySQL()
        cls.addClassCleanup(cls.mysql.close)
        cls.mysql.start()
        source = (ROOT / "yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/report/RehabReportVersionSequence.java").read_text()
        match = re.search(r'static final String LOCK_ASSESSMENT_SQL = "([^"]+)";', source)
        if not match or match[1].count("?") != 2:
            raise RuntimeError("production source-lock contract changed; review diagnostic evidence")
        cls.lock = match[1].replace("?", "71", 1).replace("?", "801", 1)

    def setUp(self):
        self.database = "synthetic_source_" + secrets.token_hex(4)
        self.mysql.sql(f"CREATE DATABASE `{self.database}`;")
        self.mysql.sql("""
CREATE TABLE rehab_assessment_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,chief_focus VARCHAR(32),deleted BOOLEAN DEFAULT FALSE) ENGINE=InnoDB;
CREATE TABLE rehab_assessment_module_data(assessment_id BIGINT,module_type VARCHAR(16),tenant_id BIGINT,data_json VARCHAR(32),deleted BOOLEAN DEFAULT FALSE,
 PRIMARY KEY(assessment_id,module_type)) ENGINE=InnoDB;
INSERT INTO rehab_assessment_record VALUES(801,71,'SYNTHETIC_A',FALSE);
INSERT INTO rehab_assessment_module_data VALUES(801,'nasm',71,'SYNTHETIC_A',FALSE),(801,'sfma',71,'SYNTHETIC_A',FALSE);
""", self.database)
        self.primary = "SELECT chief_focus FROM rehab_assessment_record WHERE tenant_id=71 AND id=801 AND deleted=FALSE"
        self.modules = "SELECT data_json FROM rehab_assessment_module_data WHERE tenant_id=71 AND assessment_id=801 AND deleted=FALSE ORDER BY module_type"
        self.edit = ("UPDATE rehab_assessment_record SET chief_focus='SYNTHETIC_B' WHERE tenant_id=71 AND id=801; "
                     "UPDATE rehab_assessment_module_data SET data_json='SYNTHETIC_B' WHERE tenant_id=71 AND assessment_id=801")

    def session(self):
        session = Session(self.mysql, self.database)
        self.addCleanup(session.close)
        session.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION")
        return session

    def test_evidence_locking_after_first_read_does_not_refresh_source_snapshot(self):
        reader = self.session()
        self.assertEqual(["SYNTHETIC_A"], reader.execute(self.primary))
        self.mysql.sql("START TRANSACTION; " + self.edit + "; COMMIT", self.database)
        self.assertEqual(["801"], reader.execute(self.lock))
        # A row lock is not a source-revision refresh: both ordinary reads stay old.
        self.assertEqual(["SYNTHETIC_A"], reader.execute(self.primary))
        self.assertEqual(["SYNTHETIC_A", "SYNTHETIC_A"], reader.execute(self.modules))
        # A future fix must read fresh primary AND modules, not reuse the old DO.
        self.assertEqual(["SYNTHETIC_B"], reader.execute(self.primary + " FOR UPDATE"))
        self.assertEqual(["SYNTHETIC_B", "SYNTHETIC_B"], reader.execute(self.modules + " FOR UPDATE"))
        reader.execute("ROLLBACK")

    def test_evidence_shared_primary_lock_orders_proposed_writer_after_source_capture(self):
        reader, writer = self.session(), self.session()
        reader.execute(self.lock)
        attempting = threading.Event()

        def edit_after_same_lock():
            attempting.set()
            writer.execute(self.lock)
            writer.execute(self.edit + "; COMMIT")

        with ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(edit_after_same_lock)
            self.assertTrue(attempting.wait(5))
            self.assertEqual(["SYNTHETIC_A"], reader.execute(self.primary + " FOR UPDATE"))
            self.assertEqual(["SYNTHETIC_A", "SYNTHETIC_A"], reader.execute(self.modules + " FOR UPDATE"))
            reader.execute("COMMIT")
            future.result(timeout=20)
        self.assertEqual("SYNTHETIC_B\n", self.mysql.sql(self.primary, self.database).stdout)
        self.assertEqual("SYNTHETIC_B\nSYNTHETIC_B\n", self.mysql.sql(self.modules, self.database).stdout)


if __name__ == "__main__":
    unittest.main()
