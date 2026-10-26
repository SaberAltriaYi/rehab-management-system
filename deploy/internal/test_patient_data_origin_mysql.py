"""Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT.

Explicit MySQL 8.4 migration proof, synthetic data only. No ports, network or
persistent volumes; never uses clinic configuration or native database targets.
"""
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import unittest

IMAGE = "mysql:8.4.10@sha256:8dbcf531a03aade657e181b9cf2f1d1803ce621a1d55610cb44cb531ab7d7db6"
ROOT = Path(__file__).resolve().parents[2]


@unittest.skipUnless(os.environ.get("REHAB_TEST_MYSQL") == "1", "explicit isolated MySQL opt-in required")
class PatientDataOriginMySQLTest(unittest.TestCase):
    @classmethod
    def command(cls, args, data=None, check=True, timeout=60):
        env = dict(os.environ, MYSQL_PWD=cls.password, MYSQL_ROOT_PASSWORD=cls.password)
        result = subprocess.run(["docker", *args], input=data, capture_output=True,
                                text=True, env=env, timeout=timeout)
        if check and result.returncode:
            # Do not include SQL, dump, environment, or possibly sensitive error text.
            raise RuntimeError(f"isolated MySQL command failed (exit {result.returncode})")
        return result

    @classmethod
    def setUpClass(cls):
        cls.password = secrets.token_urlsafe(40)
        host = os.environ.get("DOCKER_HOST", "")
        if host and not host.startswith(("unix://", "npipe://")):
            raise RuntimeError("test refuses a remote Docker daemon")
        context = cls.command(["context", "inspect"]).stdout
        endpoint = json.loads(context)[0]["Endpoints"]["docker"]["Host"]
        if not endpoint.startswith(("unix://", "npipe://")):
            raise RuntimeError("test requires a local Docker socket")
        cls.container = "rehab-origin-mysql-" + secrets.token_hex(6)
        cls.command(["run", "--detach", "--name", cls.container, "--network", "none",
                     "--tmpfs", "/var/lib/mysql:rw,size=512m", "--env", "MYSQL_ROOT_PASSWORD", IMAGE], timeout=180)
        cls.addClassCleanup(lambda: cls.command(["rm", "--force", cls.container], check=False))
        info = json.loads(cls.command(["inspect", cls.container]).stdout)[0]
        if (info["Name"] != "/" + cls.container or info["HostConfig"]["NetworkMode"] != "none"
                or info["HostConfig"].get("Binds") or info["HostConfig"].get("PortBindings")
                or any(m["Type"] != "tmpfs" for m in info.get("Mounts", []))):
            raise RuntimeError("unexpected test container identity or persistent/network configuration")
        for _ in range(90):
            # The image starts a temporary socket server during initialization.
            # mysqladmin ping can succeed before that server is shut down again.
            # Only the final mysqld replaces PID 1; require an authenticated query too.
            process = cls.command(["exec", cls.container, "cat", "/proc/1/comm"], check=False, timeout=10)
            result = cls.command(["exec", "--env", "MYSQL_PWD", cls.container,
                                  "mysql", "-uroot", "--batch", "--skip-column-names",
                                  "--execute", "SELECT 1"], check=False, timeout=10)
            if process.stdout.strip() == "mysqld" and result.returncode == 0 and result.stdout.strip() == "1":
                break
            time.sleep(1)
        else:
            raise RuntimeError("synthetic MySQL startup timeout")
        cls.migration = (ROOT / "sql/mysql/rehab-patient-data-origin-v1.sql").read_text()

    def sql(self, value, database=None, check=True):
        args = ["exec", "--interactive", "--env", "MYSQL_PWD", self.container,
                "mysql", "-uroot", "--default-character-set=utf8mb4", "--batch", "--skip-column-names", "--binary-mode"]
        if database:
            args.append(database)
        return self.command(args, value, check=check)

    def setUp(self):
        self.database = "origin_case_" + secrets.token_hex(4)
        self.sql(f"CREATE DATABASE `{self.database}` CHARACTER SET utf8mb4;")
        self.sql("""
CREATE TABLE rehab_patient (id BIGINT PRIMARY KEY, tenant_id BIGINT NOT NULL,
 patient_no VARCHAR(64),name VARCHAR(64),clinical_payload VARCHAR(128),
 deleted BIT(1) DEFAULT b'0',update_time DATETIME DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE system_menu (id BIGINT PRIMARY KEY,name VARCHAR(128),permission VARCHAR(128),
 type INT,sort INT,parent_id BIGINT,path VARCHAR(64),icon VARCHAR(64),component VARCHAR(128),
 component_name VARCHAR(128),status INT,visible BIT(1),keep_alive BIT(1),always_show BIT(1),
 creator VARCHAR(64),updater VARCHAR(64),deleted BIT(1));
CREATE TABLE system_role_menu (role_id BIGINT,menu_id BIGINT);
INSERT INTO system_role_menu VALUES (71,9002);
INSERT INTO rehab_patient(id,tenant_id,patient_no,name,clinical_payload) VALUES
 (801,71,'SYNTHETIC-801','【QA测试】业务分类夹具','SYNTHETIC_ORIGINAL_A'),
 (802,72,'SYNTHETIC-802','普通姓名夹具','SYNTHETIC_ORIGINAL_B');
""", self.database)

    def original_fields(self):
        return self.sql("SELECT id,tenant_id,patient_no,name,clinical_payload,HEX(deleted),update_time "
                        "FROM rehab_patient ORDER BY id;", self.database).stdout

    def migrate(self, check=True):
        return self.sql(self.migration, self.database, check)

    def test_upgrade_preserves_every_original_field_and_does_not_infer_from_name(self):
        before = self.original_fields()
        self.migrate()
        self.assertEqual(before, self.original_fields())
        self.assertEqual("REAL\nREAL\n", self.sql("SELECT data_origin FROM rehab_patient ORDER BY id;", self.database).stdout)
        self.assertEqual("1\n", self.sql("SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                         "WHERE table_schema=DATABASE() AND table_name='rehab_patient' "
                         "AND index_name='idx_rehab_patient_origin';", self.database).stdout)

    def test_idempotency_and_no_automatic_role_grants(self):
        self.migrate()
        self.migrate()
        self.assertEqual("71\t9002\n", self.sql("SELECT * FROM system_role_menu;", self.database).stdout)
        self.assertEqual("9895\t9002\n", self.sql("SELECT id,parent_id FROM system_menu "
                         "WHERE permission='rehab:patient:data-governance';", self.database).stdout)

    def test_constraint_rejects_unknown_origin_and_new_rows_default_to_business(self):
        self.migrate()
        invalid = self.sql("UPDATE rehab_patient SET data_origin='UNTRUSTED' WHERE id=801;", self.database, False)
        self.assertNotEqual(0, invalid.returncode)
        self.sql("INSERT INTO rehab_patient(id,tenant_id,patient_no) VALUES (803,71,'SYNTHETIC-803');", self.database)
        self.assertEqual("REAL\n", self.sql("SELECT data_origin FROM rehab_patient WHERE id=803;", self.database).stdout)

    def test_incompatible_existing_column_blocks_instead_of_silently_excluding_rows(self):
        before = self.original_fields()
        self.sql("ALTER TABLE rehab_patient ADD COLUMN data_origin VARCHAR(32) NULL;", self.database)
        self.assertNotEqual(0, self.migrate(False).returncode)
        self.assertEqual(before, self.original_fields())
        self.assertEqual("0\n", self.sql("SELECT COUNT(*) FROM system_menu;", self.database).stdout)

    def test_permission_id_collision_never_overwrites_another_permission(self):
        self.sql("INSERT INTO system_menu(id,permission) VALUES (9895,'synthetic:existing:permission');", self.database)
        self.assertNotEqual(0, self.migrate(False).returncode)
        self.assertEqual("synthetic:existing:permission\n", self.sql("SELECT permission FROM system_menu WHERE id=9895;", self.database).stdout)

    def test_origin_and_original_fields_survive_dump_and_restore(self):
        self.migrate()
        self.sql("UPDATE rehab_patient SET data_origin='SYNTHETIC_ARCHIVED' WHERE id=801;", self.database)
        before = self.original_fields()
        dump = self.command(["exec", "--env", "MYSQL_PWD", self.container, "mysqldump", "-uroot",
                             "--no-tablespaces", "--set-gtid-purged=OFF", "--default-character-set=utf8mb4", self.database]).stdout
        restored = "origin_restored_" + secrets.token_hex(4)
        self.sql(f"CREATE DATABASE `{restored}` CHARACTER SET utf8mb4;")
        self.sql(dump, restored)
        self.assertEqual("SYNTHETIC_ARCHIVED\nREAL\n", self.sql("SELECT data_origin FROM rehab_patient ORDER BY id;", restored).stdout)
        self.assertEqual(before, self.sql("SELECT id,tenant_id,patient_no,name,clinical_payload,HEX(deleted),update_time "
                                         "FROM rehab_patient ORDER BY id;", restored).stdout)


if __name__ == "__main__":
    unittest.main()
