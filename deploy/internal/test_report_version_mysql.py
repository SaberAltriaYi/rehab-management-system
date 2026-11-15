"""Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.
Actual allocator SQL on synthetic MySQL REPEATABLE READ sessions, no business DB.
"""
from concurrent.futures import ThreadPoolExecutor
import os
from pathlib import Path
import re
import secrets
import select
import subprocess
import threading
import time
import unittest
from synthetic_mysql import SyntheticMySQL

ROOT = Path(__file__).resolve().parents[2]


class Session:
    def __init__(self, mysql, database):
        self.process = subprocess.Popen([
            "docker", "exec", "--interactive", "--env", "MYSQL_PWD", mysql.container,
            "mysql", "-uroot", "--batch", "--skip-column-names", "--unbuffered", "--skip-reconnect", database,
        ], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=mysql.environment())
        self.buffer = b""

    def execute(self, sql):
        marker = "SYNTHETIC_END_" + secrets.token_hex(8)
        self.process.stdin.write((sql + ";\nSELECT '" + marker + "';\n").encode())
        self.process.stdin.flush()
        rows = []
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            while b"\n" in self.buffer:
                line, self.buffer = self.buffer.split(b"\n", 1)
                if line.decode().strip() == marker:
                    return rows
                rows.append(line.decode().strip())
            ready, _, _ = select.select([self.process.stdout], [], [], max(0, deadline - time.monotonic()))
            if not ready:
                break
            data = os.read(self.process.stdout.fileno(), 65536)
            if not data:
                raise RuntimeError("synthetic MySQL session ended; response omitted")
            self.buffer += data
            if len(self.buffer) > 65536:
                raise RuntimeError("unexpected synthetic query output size")
        raise TimeoutError("synthetic MySQL query timeout; response omitted")

    def close(self):
        if self.process.poll() is None:
            try:
                self.process.stdin.write(b"ROLLBACK;\nQUIT;\n")
                self.process.stdin.flush()
                self.process.wait(timeout=5)
            except (BrokenPipeError, subprocess.TimeoutExpired):
                self.process.kill()
                self.process.wait(timeout=5)
        self.process.stdin.close()
        self.process.stdout.close()


@unittest.skipUnless(os.environ.get("REHAB_TEST_MYSQL") == "1", "explicit isolated MySQL opt-in required")
class ReportVersionMySQLTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.mysql = SyntheticMySQL()
        cls.addClassCleanup(cls.mysql.close)
        cls.mysql.start()
        source = (ROOT / "yudao-module-rehab/src/main/java/cn/iocoder/yudao/module/rehab/service/report/RehabReportVersionSequence.java").read_text()
        # Read the exact parameterized production SQL, not a divergent test copy.
        cls.queries = {}
        for name in ("LOCK_ASSESSMENT_SQL", "LATEST_VERSION_SQL"):
            match = re.search(r'static final String ' + name + r' = "([^"]+)";', source)
            if not match or match[1].count("?") != 2:
                raise RuntimeError("allocator SQL contract changed; review MySQL proof")
            cls.queries[name] = match[1].replace("?", "71", 1).replace("?", "801", 1)

    def setUp(self):
        self.database = "synthetic_report_" + secrets.token_hex(4)
        self.mysql.sql(f"CREATE DATABASE `{self.database}`;")
        self.mysql.sql("""
CREATE TABLE rehab_assessment_record(id BIGINT PRIMARY KEY,tenant_id BIGINT,deleted BOOLEAN DEFAULT FALSE) ENGINE=InnoDB;
CREATE TABLE rehab_report(id BIGINT AUTO_INCREMENT PRIMARY KEY,tenant_id BIGINT,assessment_id BIGINT,report_version INT,
 deleted BOOLEAN DEFAULT FALSE,original_payload VARCHAR(64),KEY idx_assessment(assessment_id)) ENGINE=InnoDB;
INSERT INTO rehab_assessment_record VALUES(801,71,FALSE);
""", self.database)

    def session(self):
        session = Session(self.mysql, self.database)
        self.addCleanup(session.close)
        return session

    def test_stale_repeatable_read_and_concurrent_waiter_use_current_highest_version(self):
        a, b = self.session(), self.session()
        for session in (a, b):
            session.execute("SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ; START TRANSACTION")
            self.assertEqual(["0"], session.execute("SELECT COUNT(*) FROM rehab_report"))
        self.assertEqual(["801"], a.execute(self.queries["LOCK_ASSESSMENT_SQL"]))
        self.assertEqual([], a.execute(self.queries["LATEST_VERSION_SQL"]))
        a.execute("INSERT INTO rehab_report(tenant_id,assessment_id,report_version,original_payload) VALUES(71,801,1,'SYNTHETIC_FIRST')")
        attempting = threading.Event()

        def second():
            attempting.set()
            self.assertEqual(["801"], b.execute(self.queries["LOCK_ASSESSMENT_SQL"]))
            # Ordinary reads STILL see the earlier empty snapshot even after a commits.
            self.assertEqual(["0"], b.execute("SELECT COUNT(*) FROM rehab_report"))
            current = b.execute(self.queries["LATEST_VERSION_SQL"])
            self.assertEqual(["1"], current)
            b.execute("INSERT INTO rehab_report(tenant_id,assessment_id,report_version,original_payload) VALUES(71,801,2,'SYNTHETIC_SECOND'); COMMIT")

        with ThreadPoolExecutor(max_workers=1) as pool:
            future = pool.submit(second)
            self.assertTrue(attempting.wait(5))
            a.execute("COMMIT")
            future.result(timeout=20)
        self.assertEqual("1\n2\n", self.mysql.sql("SELECT report_version FROM rehab_report ORDER BY id", self.database).stdout)

    def test_deleted_and_gapped_versions_are_retained_and_cannot_be_reused(self):
        self.mysql.sql("INSERT INTO rehab_report(tenant_id,assessment_id,report_version,deleted,original_payload) VALUES"
                       "(71,801,1,FALSE,'SYNTHETIC_ORIGINAL_1'),(71,801,5,TRUE,'SYNTHETIC_ORIGINAL_5')", self.database)
        session = self.session()
        session.execute("START TRANSACTION")
        session.execute(self.queries["LOCK_ASSESSMENT_SQL"])
        self.assertEqual(["5"], session.execute(self.queries["LATEST_VERSION_SQL"]))
        session.execute("COMMIT")
        self.assertEqual("1\tSYNTHETIC_ORIGINAL_1\n5\tSYNTHETIC_ORIGINAL_5\n", self.mysql.sql(
            "SELECT report_version,original_payload FROM rehab_report ORDER BY id", self.database).stdout)

    def test_rollback_keeps_original_history_and_releases_allocation_lock(self):
        a = self.session()
        a.execute("START TRANSACTION")
        a.execute(self.queries["LOCK_ASSESSMENT_SQL"])
        a.execute("INSERT INTO rehab_report(tenant_id,assessment_id,report_version) VALUES(71,801,1); ROLLBACK")
        b = self.session()
        b.execute("START TRANSACTION")
        self.assertEqual(["801"], b.execute(self.queries["LOCK_ASSESSMENT_SQL"]))
        self.assertEqual([], b.execute(self.queries["LATEST_VERSION_SQL"]))
        b.execute("ROLLBACK")


if __name__ == "__main__":
    unittest.main()
