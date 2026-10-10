import json
import tempfile
import unittest
from pathlib import Path

from l5_sbom_inventory import component, make_bom, maven_components, pnpm_components


class L5LockfileInventoryTests(unittest.TestCase):
    def test_real_lockfile_inventory_has_three_sources_and_no_source_secrets(self):
        result = make_bom()
        self.assertEqual(result["bomFormat"], "CycloneDX")
        self.assertEqual(result["specVersion"], "1.5")
        self.assertGreater(len(result["components"]), 100)
        self.assertEqual(len({c["bom-ref"] for c in result["components"]}), len(result["components"]))
        value = json.dumps(result)
        self.assertNotIn("password", value.lower())
        self.assertNotIn("private key", value.lower())
        self.assertNotIn("/Users/saber", value)
        self.assertIn("provisional-lockfile-only", value)

    def test_scoped_npm_component_has_canonical_escaped_purl(self):
        item = component("npm", "@types/node", "22.15.3")
        self.assertEqual(item["purl"], "pkg:npm/%40types/node@22.15.3")

    def test_maven_tree_only_reads_existing_file(self):
        self.assertIn("MAVEN_TRANSITIVE_TREE_MISSING",
                      maven_components(Path("/nonexistent-l5-maven-fixture"))[1])

    def test_pnpm_poisoned_metadata_and_wrong_versions_fail(self):
        with tempfile.TemporaryDirectory(prefix="l5-lock-") as path:
            file = Path(path) / "pnpm-lock.yaml"
            file.write_text('packages:\n  bad@<script>: {}\n')
            with self.assertRaises(ValueError):
                pnpm_components(file)


if __name__ == "__main__":
    unittest.main()
