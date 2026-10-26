"""Synthetic S0 inventory checks; no business service or patient access.

Copyright (c) 2026 杨玺龙. SPDX-License-Identifier: MIT
"""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import subprocess
import sys


spec = importlib.util.spec_from_file_location("s0_inventory", Path(__file__).with_name("s0_inventory.py"))
s0 = importlib.util.module_from_spec(spec)
spec.loader.exec_module(s0)
filter_spec = importlib.util.spec_from_file_location(
    "sensitive_findings", Path(__file__).with_name("sensitive_findings.py")
)
findings_filter = importlib.util.module_from_spec(filter_spec)
filter_spec.loader.exec_module(findings_filter)


class S0InventoryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / "README.md").write_text("synthetic workspace")

    def test_registry_paths_cannot_escape_or_use_absolute_targets(self):
        for value in ("", "../outside", "/tmp", "a/../../b", "a\\b"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                s0.scoped(self.root, value)

    def test_symlinked_input_is_refused(self):
        (self.root / "link").symlink_to(self.root / "README.md")
        with self.assertRaises(ValueError):
            s0.scoped(self.root, "link")

    def test_no_git_metadata_is_not_misreported_as_a_repository(self):
        self.assertEqual(s0.git_metadata(self.root), {"state": "not_git"})

    def test_workspace_readme_symlink_cannot_export_an_external_file_hash(self):
        (self.root / "README.md").unlink()
        (self.root / "README.md").symlink_to(self.root / "external-secret")
        with self.assertRaises(ValueError):
            s0.collect(self.root, {"assets": []})

    def test_detached_and_dirty_are_recorded_without_remote_or_untracked_names(self):
        (self.root / ".git").mkdir()
        values = {("rev-parse", "--show-toplevel"): str(self.root),
                  ("status", "--porcelain=v1", "--untracked-files=no"): " M README.md",
                  ("rev-parse", "HEAD"): "1" * 40, ("branch", "--show-current"): ""}
        with patch.object(s0, "git_read", side_effect=lambda p, args: values[tuple(args)]):
            result = s0.git_metadata(self.root)
        self.assertEqual(result["branch"], "DETACHED")
        self.assertTrue(result["trackedWorktreeDirty"])
        self.assertNotIn("remote", result)

    def test_source_comparison_detects_additions_and_changes_not_data(self):
        left, right = self.root / "left", self.root / "right"
        for root in (left, right):
            (root / "src").mkdir(parents=True)
            (root / "src/a.py").write_text("same")
            (root / "README.md").write_text(root.name)
            (root / "data").mkdir()
            (root / "data/private.txt").write_text("must not be read")
        (right / "src/new.py").write_text("new")
        result = s0.compare_sources(left, right)
        self.assertEqual((result["same"], result["different"]), (1, 1))
        self.assertEqual(result["rightOnly"], ["src/new.py"])
        self.assertNotIn("private", json.dumps(result))

    def test_source_symlink_refused_and_cache_not_read(self):
        (self.root / "src").mkdir()
        (self.root / "src/private.py").symlink_to(self.root / "README.md")
        with self.assertRaises(ValueError):
            s0.source_hashes(self.root)

    def test_private_assets_are_existence_only_and_collect_writes_nothing(self):
        private = self.root / "runtime"
        private.mkdir()
        secret = private / ".env"
        secret.write_text("private-synthetic-value")
        registry = {"assets": [{"id": "private", "path": "runtime", "git": False}]}
        before = secret.read_bytes()
        result = s0.collect(self.root, registry)
        self.assertTrue(result["assets"][0]["exists"])
        self.assertNotIn("private-synthetic-value", json.dumps(result))
        self.assertEqual(before, secret.read_bytes())

    def test_duplicate_asset_ids_and_paths_fail(self):
        item = {"id": "x", "path": "one", "git": False}
        with self.assertRaises(ValueError):
            s0.collect(self.root, {"assets": [item, item]})

    def test_loopback_probe_allowlist_rejects_credentials_remote_and_redirect_destinations(self):
        for value in ("http://example.com/", "http://localhost:8088/health", "http://127.0.0.1:3306/",
                      "http://127.0.0.1:8088/health?x=1", "https://127.0.0.1:8088/health",
                      "http://user:password@127.0.0.1:8088/health"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                s0.validate_probe_url(value)
        for url in s0.PROBES.values():
            s0.validate_probe_url(url)
        self.assertIsNone(s0.NoRedirect().redirect_request(None, None, 302, "", {}, "http://example.com"))

    def test_probe_disabled_means_no_network_calls(self):
        with patch.object(s0, "probe", side_effect=AssertionError("network forbidden")):
            result = s0.collect(self.root, {"assets": []})
        self.assertEqual(result["loopbackProbes"]["state"], "not_requested")

    def test_known_artifact_reports_modules_and_hash_not_binary_payload(self):
        jar = self.root / "rehab-runtime/core-local/releases/20261026-motion-v1/yudao-server.jar"
        jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(jar, "w") as out:
            out.writestr("BOOT-INF/lib/yudao-module-rehab-1.0.0.jar", "synthetic")
            out.writestr("BOOT-INF/lib/yudao-module-member-1.0.0.jar", "synthetic")
        result = s0.artifact_metadata(self.root)
        self.assertEqual(result["modules"], ["yudao-module-member", "yudao-module-rehab"])
        self.assertEqual(len(result["sha256"]), 64)
        self.assertEqual(result["activeProcess"], "not_verified_by_this_read_only_tool")


class SensitiveFindingTests(unittest.TestCase):
    def record(self, source, path=None):
        return f"{path or findings_filter.PARSER_PATH}:35:        {source}\n"

    def test_only_exact_header_stripping_expression_is_allowed(self):
        self.assertEqual(findings_filter.review(self.record(findings_filter.PARSER_LITERAL)), [])

    def test_identical_expression_in_another_file_is_not_whitelisted(self):
        result = findings_filter.review(self.record(findings_filter.PARSER_LITERAL, "other.java"))
        self.assertEqual(result, [("other.java", 35)])

    def test_key_marker_elsewhere_in_same_file_stays_blocked(self):
        source = f'String key="{findings_filter.KEY_MARKER}";'
        result = findings_filter.review(self.record(source))
        self.assertEqual(len(result), 1)

    def test_appended_or_same_file_access_credential_is_never_exempted(self):
        value = "gho_" + "S" * 32
        for source in (findings_filter.PARSER_LITERAL + value, value):
            self.assertEqual(len(findings_filter.review(self.record(source))), 1)

    def test_safe_literal_does_not_hide_another_line(self):
        data = self.record(findings_filter.PARSER_LITERAL) + self.record("synthetic-sensitive-payload")
        self.assertEqual(len(findings_filter.review(data)), 1)

    def test_malformed_or_excessive_input_fails_closed(self):
        for value in ("unparseable", "x" * (findings_filter.MAX_BYTES + 1)):
            with self.subTest(size=len(value)), self.assertRaises(ValueError):
                findings_filter.review(value)

    def test_command_output_redacts_payload_and_preserves_failure_exit(self):
        value = "gho_" + "S" * 32
        result = subprocess.run(
            [sys.executable, "-B", str(Path(__file__).with_name("sensitive_findings.py"))],
            input=self.record(value), text=True, capture_output=True, check=False,
        )
        self.assertEqual(result.returncode, 1)
        self.assertNotIn(value, result.stdout + result.stderr)
        self.assertIn("[sensitive match redacted]", result.stdout)

    def test_command_accepts_only_literal_and_rejects_malformed_records(self):
        for data, expected in ((self.record(findings_filter.PARSER_LITERAL), 0), ("invalid", 2)):
            result = subprocess.run(
                [sys.executable, "-B", str(Path(__file__).with_name("sensitive_findings.py"))],
                input=data, text=True, capture_output=True, check=False,
            )
            self.assertEqual(result.returncode, expected)


if __name__ == "__main__":
    unittest.main()
