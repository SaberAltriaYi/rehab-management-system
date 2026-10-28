"""Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
Actual clinical source SQL on random, private, synthetic MySQL only.
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
class ClinicalSourcesMySQLTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mysql = SyntheticMySQL(); cls.addClassCleanup(cls.mysql.close); cls.mysql.start()
        source = (ROOT / "yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/RehabClinicalSources.java").read_text()
        cls.sqls = {}
        for name in ("PATIENT_SQL", "ASSESSMENT_SQL", "EPISODE_SQL", "MODULES_SQL", "ACTIVE_EPISODE_SQL"):
            match = re.search(r'static final String ' + name + r' = "([^"]+)";', source)
            if not match or match[1].count("?") != 2: raise RuntimeError("review production clinical SQL contract")
            cls.sqls[name] = match[1]
        for name,count in (("PLAN_SQL",2),("ACTIVE_PLAN_SQL",3)):
            match = re.search(r'static final String ' + name + r' = "([^"]+)";',source)
            if not match or match[1].count("?") != count: raise RuntimeError("review production plan SQL contract")
            cls.sqls[name]=match[1]

    def setUp(self):
        self.database = "synthetic_clinical_" + secrets.token_hex(4)
        self.mysql.sql(f"CREATE DATABASE `{self.database}`;")
        self.mysql.sql("""
CREATE TABLE rehab_patient(id BIGINT PRIMARY KEY,tenant_id BIGINT,name VARCHAR(32),deleted BOOLEAN DEFAULT FALSE) ENGINE=InnoDB;
CREATE TABLE rehab_episode(id BIGINT PRIMARY KEY,patient_id BIGINT,tenant_id BIGINT,note VARCHAR(32),status VARCHAR(16),create_time DATETIME DEFAULT CURRENT_TIMESTAMP,deleted BOOLEAN DEFAULT FALSE) ENGINE=InnoDB;
CREATE TABLE rehab_assessment_record(id BIGINT PRIMARY KEY,patient_id BIGINT,episode_id BIGINT,tenant_id BIGINT,chief_focus VARCHAR(32),deleted BOOLEAN DEFAULT FALSE) ENGINE=InnoDB;
CREATE TABLE rehab_assessment_module_data(id BIGINT PRIMARY KEY,assessment_id BIGINT,module_type VARCHAR(16),tenant_id BIGINT,data_json VARCHAR(32),deleted BOOLEAN DEFAULT FALSE) ENGINE=InnoDB;
CREATE TABLE rehab_care_plan(id BIGINT PRIMARY KEY,patient_id BIGINT,episode_id BIGINT,source_assessment_id BIGINT,tenant_id BIGINT,status VARCHAR(16),deleted BOOLEAN DEFAULT FALSE) ENGINE=InnoDB;
INSERT INTO rehab_patient VALUES(701,71,'SYNTHETIC_A',FALSE),(702,72,'SYNTHETIC_OTHER',FALSE);
INSERT INTO rehab_episode(id,patient_id,tenant_id,note,status) VALUES(801,701,71,'SYNTHETIC_A','paused');
INSERT INTO rehab_assessment_record VALUES(901,701,801,71,'SYNTHETIC_A',FALSE);
INSERT INTO rehab_assessment_module_data VALUES(1001,901,'nasm',71,'SYNTHETIC_A',FALSE),(1002,901,'sfma',71,'SYNTHETIC_A',FALSE);
INSERT INTO rehab_care_plan VALUES(1101,701,801,901,71,'paused',FALSE);
""", self.database)

    def query(self, name, id, tenant=71):
        return self.sqls[name].replace("?", str(tenant), 1).replace("?", str(id), 1)

    def session(self):
        session = Session(self.mysql, self.database); self.addCleanup(session.close)
        session.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION")
        return session

    def edit(self, session):
        session.execute(self.query("PATIENT_SQL", 701))
        session.execute("UPDATE rehab_patient SET name='SYNTHETIC_B' WHERE id=701; "
                        "UPDATE rehab_episode SET note='SYNTHETIC_B',status='active' WHERE id=801; "
                        "UPDATE rehab_assessment_record SET chief_focus='SYNTHETIC_B' WHERE id=901; "
                        "UPDATE rehab_assessment_module_data SET data_json='SYNTHETIC_B' WHERE assessment_id=901; COMMIT")

    def test_all_actual_current_reads_ignore_prior_rr_snapshot_without_mixing_tenants(self):
        reader, writer = self.session(), self.session()
        self.assertEqual(["SYNTHETIC_A"], reader.execute("SELECT name FROM rehab_patient WHERE id=701"))
        self.edit(writer)
        self.assertEqual(["SYNTHETIC_A"], reader.execute("SELECT name FROM rehab_patient WHERE id=701"))
        for name, id in (("PATIENT_SQL",701),("ASSESSMENT_SQL",901),("EPISODE_SQL",801),("MODULES_SQL",901)):
            rows = reader.execute(self.query(name,id))
            self.assertTrue(rows); self.assertTrue(all("SYNTHETIC_B" in row for row in rows))
            self.assertEqual([], reader.execute(self.query(name,id,72)))
        reader.execute("ROLLBACK")

    def test_patient_root_serializes_stage_and_source_edits_until_capture_finishes(self):
        reader, writer = self.session(), self.session(); attempting = threading.Event()
        reader.execute(self.query("PATIENT_SQL",701))
        def waiting_writer():
            attempting.set(); self.edit(writer)
        with ThreadPoolExecutor(max_workers=1) as pool:
            pending = pool.submit(waiting_writer); self.assertTrue(attempting.wait(5))
            for name,id in (("ASSESSMENT_SQL",901),("EPISODE_SQL",801),("MODULES_SQL",901)):
                self.assertTrue(all("SYNTHETIC_A" in row for row in reader.execute(self.query(name,id))))
            reader.execute("COMMIT"); pending.result(timeout=20)
        self.assertIn("SYNTHETIC_B", self.mysql.sql(self.query("PATIENT_SQL",701),self.database).stdout)

    def test_current_active_episode_read_prevents_creating_from_an_old_empty_snapshot(self):
        reader, writer = self.session(), self.session()
        self.assertEqual(["0"],reader.execute("SELECT COUNT(*) FROM rehab_episode WHERE patient_id=701 AND status='active'"))
        self.edit(writer); reader.execute(self.query("PATIENT_SQL",701))
        self.assertEqual(["0"],reader.execute("SELECT COUNT(*) FROM rehab_episode WHERE patient_id=701 AND status='active'"))
        rows = reader.execute(self.query("ACTIVE_EPISODE_SQL",701))
        self.assertEqual(1,len(rows)); self.assertIn("active",rows[0]); reader.execute("ROLLBACK")

    def test_deleted_sources_are_never_current_sources(self):
        self.mysql.sql("UPDATE rehab_patient SET deleted=TRUE WHERE id=701; UPDATE rehab_episode SET deleted=TRUE; "
                       "UPDATE rehab_assessment_record SET deleted=TRUE; UPDATE rehab_assessment_module_data SET deleted=TRUE; UPDATE rehab_care_plan SET deleted=TRUE",self.database)
        reader=self.session()
        for name,id in (("PATIENT_SQL",701),("ASSESSMENT_SQL",901),("EPISODE_SQL",801),("MODULES_SQL",901),("ACTIVE_EPISODE_SQL",701),("PLAN_SQL",1101)):
            self.assertEqual([],reader.execute(self.query(name,id)))
        reader.execute("ROLLBACK")

    def test_current_active_plan_read_does_not_reuse_an_old_empty_snapshot(self):
        reader,writer=self.session(),self.session()
        count="SELECT COUNT(*) FROM rehab_care_plan WHERE patient_id=701 AND episode_id=801 AND status='active'"
        self.assertEqual(["0"],reader.execute(count))
        writer.execute(self.query("PATIENT_SQL",701))
        writer.execute("UPDATE rehab_care_plan SET status='active' WHERE id=1101; COMMIT")
        reader.execute(self.query("PATIENT_SQL",701))
        self.assertEqual(["0"],reader.execute(count))
        active=self.query("ACTIVE_PLAN_SQL",701).replace("?","801",1)
        rows=reader.execute(active); self.assertEqual(1,len(rows)); self.assertIn("active",rows[0])
        self.assertEqual([],reader.execute(self.query("PLAN_SQL",1101,72)))
        reader.execute("ROLLBACK")

    def test_current_module_presence_prevents_placeholder_initialization_from_old_empty_snapshot(self):
        self.mysql.sql("DELETE FROM rehab_assessment_module_data",self.database)
        reader,writer=self.session(),self.session()
        count="SELECT COUNT(*) FROM rehab_assessment_module_data WHERE assessment_id=901"
        self.assertEqual(["0"],reader.execute(count))
        writer.execute(self.query("PATIENT_SQL",701))
        writer.execute("INSERT INTO rehab_assessment_module_data VALUES(1003,901,'sfma',71,'SYNTHETIC_KEEP',FALSE); COMMIT")
        reader.execute(self.query("PATIENT_SQL",701))
        self.assertEqual(["0"],reader.execute(count))
        rows=reader.execute(self.query("MODULES_SQL",901))
        self.assertEqual(1,len(rows)); self.assertIn("SYNTHETIC_KEEP",rows[0]); reader.execute("ROLLBACK")


if __name__ == "__main__": unittest.main()
