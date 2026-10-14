"""Static release contract; runtime/API evidence is separate."""
import json
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
class BusinessModulesTest(unittest.TestCase):
    def test_manifest_pages_exist(self):
        pages = json.loads((ROOT/'deploy/saas/business-modules.json').read_text())['pages']
        self.assertEqual(len(pages), len(set(pages)))
        for page in pages:
            source = ROOT/'yudao-ui/yudao-ui-admin-vue3-app/src/views'/page
            self.assertTrue((source if page.endswith('.vue') else source.with_suffix('.vue')).is_file(), page)
    def test_opt_in_and_no_role_expansion(self):
        sql = (ROOT/'deploy/saas/enable-business-menus.sql').read_text()
        self.assertIn('@saas_business_opt_in=1', sql)
        self.assertNotIn('system_role_menu', sql)
        self.assertNotIn('enable-business-menus.sql', (ROOT/'deploy/internal/migrations.manifest').read_text())
        self.assertIn("'report:go-view-data:get-by-sql'", sql)
    def test_exact_page_allowlist(self):
        sql = (ROOT/'deploy/saas/enable-business-menus.sql').read_text()
        for page in json.loads((ROOT/'deploy/saas/business-modules.json').read_text())['pages']:
            self.assertIn("'"+page+"'", sql)
        for blocked in ['bpm/model/index','crm/contract/index','crm/receivable/index','report/jmreport/index','bpm/task/todo/index','bpm/task/done/index','member/user/index']:
            self.assertNotIn("'"+blocked+"'", sql)
    def test_build_profile_is_explicit(self):
        script = (ROOT/'deploy/internal/build-server-isolated.sh').read_text()
        self.assertIn('BUSINESS_MODULES=false', script)
        self.assertIn('"$1" == "--business"', script)
        self.assertIn('mvn -Prehab-business-integration', script)
        self.assertIn('mvn -pl yudao-server -am -DskipTests package', script)
if __name__ == '__main__':
    unittest.main()
