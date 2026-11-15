"""Opt-in real MySQL tests. Uses only uniquely named synthetic databases."""

import os
from pathlib import Path
import re
import subprocess
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(
    os.environ.get("REHAB_TEST_MYSQL_CONTAINER"), "dedicated MySQL required"
)
class MotionWorkspaceMysqlTests(unittest.TestCase):
    def sql(self, text, ok=True):
        command = [
            "docker",
            "exec",
            "-i",
            self.container,
            "sh",
            "-c",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names',
        ]
        result = subprocess.run(
            command, input=text, text=True, capture_output=True, timeout=30
        )
        if ok:
            self.assertEqual(result.returncode, 0, result.stderr)
        else:
            self.assertNotEqual(result.returncode, 0)
        return result.stdout.strip()

    def setUp(self):
        self.container = os.environ["REHAB_TEST_MYSQL_CONTAINER"]
        self.database = "rehab_workspace_test_" + uuid.uuid4().hex
        self.sql("CREATE DATABASE `" + self.database + "` CHARACTER SET utf8mb4;")
        self.addCleanup(self.cleanup)
        self.sql(
            "USE `"
            + self.database
            + "`;"
            + """
CREATE TABLE system_menu(id BIGINT PRIMARY KEY,name VARCHAR(128),permission VARCHAR(128),
 type INT,sort INT,parent_id BIGINT,path VARCHAR(128),icon VARCHAR(128),component VARCHAR(128),
 component_name VARCHAR(128),status INT,visible BIT,keep_alive BIT,always_show BIT,
 creator VARCHAR(64),updater VARCHAR(64),deleted BIT);
CREATE TABLE rehab_ai_config(id INT PRIMARY KEY,enable_plan_draft BIT NOT NULL DEFAULT b'1');
INSERT INTO rehab_ai_config VALUES(1,b'1');
CREATE TABLE rehab_patient(id INT PRIMARY KEY,marker VARCHAR(64));
INSERT INTO rehab_patient VALUES(1,'synthetic-preserve');
"""
        )
        for name in (
            "rehab-motion-workspace-v1.sql",
            "rehab-ai-plan-default-off-v1.sql",
        ):
            self.sql(
                "USE `" + self.database + "`;" + (ROOT / "sql/mysql" / name).read_text()
            )

    def cleanup(self):
        if not re.fullmatch(r"rehab_workspace_test_[a-f0-9]{32}", self.database):
            raise RuntimeError("unsafe test cleanup target")
        self.sql("DROP DATABASE `" + self.database + "`;")

    def test_permissions_and_existing_data(self):
        prefix = "USE `" + self.database + "`;"
        self.assertEqual(
            self.sql(
                prefix
                + "SELECT COUNT(*) FROM system_menu WHERE permission IN ('rehab:motion:workspace','rehab:motion:sync');"
            ),
            "2",
        )
        self.assertEqual(
            self.sql(prefix + "SELECT marker FROM rehab_patient WHERE id=1;"),
            "synthetic-preserve",
        )

    def test_ai_plan_stays_disabled_for_existing_and_new_configuration(self):
        prefix = "USE `" + self.database + "`;"
        self.sql(prefix + "INSERT INTO rehab_ai_config(id) VALUES(2);")
        self.assertEqual(
            self.sql(prefix + "SELECT SUM(enable_plan_draft+0) FROM rehab_ai_config;"),
            "0",
        )

    def test_inbox_unique_by_tenant_source_revision(self):
        prefix = "USE `" + self.database + "`;"
        statement = "INSERT INTO rehab_motion_workspace_inbox(tenant_id,assessment_id,patient_id,source_id,source_revision,core_revision,sha256,snapshot_json,received_by) VALUES(%s,2,3,'a-synthetic',%s,1,REPEAT('a',64),'{}',4);"
        self.sql(prefix + statement % (1, 1))
        self.sql(prefix + statement % (1, 1), ok=False)
        self.sql(prefix + statement % (2, 1))
        self.sql(prefix + statement % (1, 2))
        self.assertEqual(
            self.sql(
                prefix
                + "SELECT COUNT(*) FROM rehab_motion_workspace_inbox WHERE review_status='pending';"
            ),
            "3",
        )


if __name__ == "__main__":
    unittest.main()
