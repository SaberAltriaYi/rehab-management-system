"""Synthetic, offline contracts for explicit native legacy adoption."""

import copy
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

import native_schema_audit as audit
from test_migration_guard import GuardFixture


class NativeSchemaAuditTests(unittest.TestCase):
    def setUp(self):
        self.schema = {
            "sample": {
                "columns": [
                    {
                        "name": "id",
                        "ordinal": 1,
                        "type": "bigint",
                        "nullable": "NO",
                        "default": None,
                        "extra": "auto_increment",
                        "collation": None,
                    }
                ],
                "indexes": [
                    {
                        "name": "PRIMARY",
                        "seq": 1,
                        "column": "id",
                        "non_unique": 0,
                        "sub_part": None,
                        "type": "BTREE",
                    }
                ],
                "foreign_keys": [],
                "options": {"engine": "InnoDB", "collation": "utf8mb4_unicode_ci"},
            }
        }

    def test_exact_metadata_passes(self):
        self.assertEqual(audit.differences(self.schema, self.schema), [])

    def test_additional_legacy_fields_are_preserved(self):
        current = copy.deepcopy(self.schema)
        current["sample"]["columns"].append({"name": "custom_field"})
        self.assertEqual(audit.differences(self.schema, current), [])

    def test_required_type_default_nullability_index_and_engine_fail_closed(self):
        for section, key, value in (
            ("columns", "type", "varchar(10)"),
            ("columns", "nullable", "YES"),
            ("columns", "default", "0"),
            ("indexes", "non_unique", 1),
        ):
            current = copy.deepcopy(self.schema)
            current["sample"][section][0][key] = value
            self.assertTrue(audit.differences(self.schema, current))
        current = copy.deepcopy(self.schema)
        current["sample"]["options"]["engine"] = "MyISAM"
        self.assertTrue(audit.differences(self.schema, current))

    def test_missing_foreign_key_fails(self):
        reference = copy.deepcopy(self.schema)
        reference["sample"]["foreign_keys"] = [
            {
                "name": "fk",
                "column": "parent_id",
                "referenced_table": "parent",
                "referenced_column": "id",
            }
        ]
        self.assertTrue(audit.differences(reference, self.schema))

    def test_equivalent_index_label_is_not_a_schema_change(self):
        current = copy.deepcopy(self.schema)
        current["sample"]["indexes"][0]["name"] = "legacy_primary_label"
        self.assertEqual(audit.differences(self.schema, current), [])

    def test_adoption_never_accepts_original_business_database(self):
        with self.assertRaises(ValueError):
            audit.audit(
                None, "ruoyi-vue-pro", "rehab_reference_synthetic", "baseline", "unused"
            )

    def test_client_configuration_rejects_world_readable_and_symlink(self):
        with tempfile.TemporaryDirectory() as tmp:
            config = Path(tmp) / "client.cnf"
            config.write_text("[client]\nuser=synthetic\n")
            config.chmod(0o644)
            with self.assertRaises(ValueError):
                audit.Mysql(config)
            config.chmod(0o600)
            link = Path(tmp) / "link.cnf"
            link.symlink_to(config)
            with self.assertRaises(ValueError):
                audit.Mysql(link)

    def test_client_uses_loopback_argument_array_and_redacts_errors(self):
        with tempfile.TemporaryDirectory() as tmp:
            config = Path(tmp) / "client.cnf"
            config.write_text("[client]\nuser=synthetic\npassword=synthetic-not-real\n")
            config.chmod(0o600)
            client = audit.Mysql(config)
            with patch("native_schema_audit.subprocess.run") as run:
                run.return_value.returncode = 1
                run.return_value.stderr = "patient-and-password-must-not-escape"
                with self.assertRaisesRegex(
                    RuntimeError, "private operator log"
                ) as error:
                    client.sql("rehab_upgrade_synthetic", "SELECT 1;")
                self.assertNotIn("patient-and-password", str(error.exception))
                argv = run.call_args.args[0]
                self.assertIn("--host=127.0.0.1", argv)
                self.assertIn("--protocol=TCP", argv)
                self.assertFalse(any("synthetic-not-real" in arg for arg in argv))


class NativeMigrationAdapterTests(unittest.TestCase):
    def setUp(self):
        self.fixture = GuardFixture()
        self.addCleanup(self.fixture.close)
        self.config = self.fixture.root / "mysql-client.cnf"
        self.config.write_text("[client]\nuser=synthetic\n")
        self.config.chmod(0o600)
        shutil.copyfile(self.fixture.bin / "docker", self.fixture.bin / "mysql")
        (self.fixture.bin / "mysql").chmod(0o700)
        self.env = {
            "REHAB_NATIVE_MYSQL_CLIENT_FILE": str(self.config),
            "REHAB_NATIVE_MYSQL_DATABASE": "rehab_upgrade_synthetic",
        }

    def test_native_status_keeps_the_same_fixed_ledger_checks(self):
        result = self.fixture.run("status", **self.env)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.fixture.assert_read_only(self)

    def test_fake_adapter_never_inherits_operator_native_target(self):
        with patch.dict(
            os.environ,
            {
                "REHAB_NATIVE_MYSQL_CLIENT_FILE": "/must-not-be-used/client.cnf",
                "REHAB_NATIVE_MYSQL_DATABASE": "must_not_open_real_database",
                "REHAB_NATIVE_MYSQL_PORT": "3306",
            },
        ):
            isolated = GuardFixture()
        self.addCleanup(isolated.close)
        for key in self.env:
            self.assertNotIn(key, isolated.env)
        self.assertNotIn("REHAB_NATIVE_MYSQL_PORT", isolated.env)
        result = isolated.run("status")
        self.assertEqual(result.returncode, 0, result.stderr)
        isolated.assert_read_only(self)

    def test_native_missing_ledger_does_not_create_or_baseline(self):
        self.fixture.set_state(ledger=False)
        result = self.fixture.run("apply", **self.env)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("账本缺失", result.stderr)
        self.fixture.assert_read_only(self)

    def test_native_insecure_config_and_invalid_database_fail_before_mysql(self):
        self.config.chmod(0o644)
        self.assertNotEqual(self.fixture.run("status", **self.env).returncode, 0)
        self.assertFalse(self.fixture.log.exists())
        self.config.chmod(0o600)
        self.env["REHAB_NATIVE_MYSQL_DATABASE"] = "database;DROP DATABASE"
        self.assertNotEqual(self.fixture.run("status", **self.env).returncode, 0)
        self.assertFalse(self.fixture.log.exists())


if __name__ == "__main__":
    unittest.main()
