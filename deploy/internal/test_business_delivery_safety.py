#!/usr/bin/env python3
"""Fail-closed checks for the internal business-module release; no database required."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
CONFIG = 'org.jeecg.modules.jmreport.config.init.JimuReportConfiguration'
MINIDAO = 'org.jeecgframework.minidao.auto.MinidaoAutoConfiguration'
FRONT = ROOT / 'yudao-ui/yudao-ui-admin-vue3-app/src'


class InternalBusinessDeliverySafetyTest(unittest.TestCase):
    def test_third_party_auto_configuration_is_excluded_in_every_profile(self):
        resources = ROOT / 'yudao-server/src/main/resources'
        for profile in ('local', 'dev', 'internal'):
            with self.subTest(profile=profile):
                self.assertIn(CONFIG, (resources / f'application-{profile}.yaml').read_text())
                self.assertIn(MINIDAO, (resources / f'application-{profile}.yaml').read_text())
        self.assertNotIn('/jmreport/*', (resources / 'application.yaml').read_text())

    def test_jimu_is_not_permit_all_or_scanned_by_default(self):
        report = ROOT / 'yudao-module-report/src/main/java/cn/iocoder/yudao/module/report'
        own = (report / 'framework/jmreport/config/JmReportConfiguration.java').read_text()
        self.assertIn('ConditionalOnProperty(name = "yudao.report.third-party-enabled", havingValue = "true")', own)
        broad_security = report / 'framework/security/config/SecurityConfiguration.java'
        self.assertFalse(broad_security.exists(), 'Legacy permitAll security configuration must not return')

    def test_browser_whitelist_covers_reviewed_paths_only(self):
        for name in ('viewModules.ts', 'viewModules.internal.ts'):
            with self.subTest(name=name):
                text = (FRONT / 'utils' / name).read_text()
                for path in ('bpm/category', 'erp/product', 'erp/stock', 'member/signin', 'report/goview'):
                    self.assertIn("'../views/" + path + '/**/*.{vue,tsx}', text)
                for path in ('report/**/*', 'bpm/model/**/*', 'mall/**/*', 'ai/**/*'):
                    self.assertNotIn("'../views/" + path, text)
        page = (FRONT / 'views/report/goview/index.vue').read_text()
        self.assertNotIn('getRefreshToken', page)
        self.assertNotIn('<IFrame', page)
        self.assertNotIn('VITE_GOVIEW_URL', page)

    def test_menu_enablement_is_opt_in_and_scoped(self):
        script_name = 'sql/mysql/rehab-enable-reviewed-business-menus-opt-in-v1.sql'
        script = (ROOT / script_name).read_text()
        self.assertNotIn(script_name, (ROOT / 'deploy/internal/migrations.manifest').read_text())
        self.assertIn('selected.full_path IN (', script)
        self.assertNotIn("'/report/jimu-report'", script)
        self.assertNotIn("'/report/jimu-bi'", script)
        # Page routes without scoped button grants produce view-only menus.
        self.assertIn('INSERT INTO system_role_menu (role_id, menu_id', script)
        self.assertIn("role.code = 'super_admin'", script)
        self.assertIn('AND NOT EXISTS (', script)
        for permission in (
            'crm:customer:create', 'bpm:category:create', 'bpm:category:delete',
            'erp:product:create', 'erp:stock:query',
            'point:sign-in-config:update', 'report:go-view-project:delete',
        ):
            with self.subTest(permission=permission):
                self.assertIn("'" + permission + "'", script)
        self.assertNotIn('report:go-view-data:get-by-sql', script)
        self.assertNotIn('report:go-view-data:get-by-http', script)
        for path in (
            '/bpm/task/todo', '/bpm/task/done',
            '/erp/stock/warehouse', '/erp/stock/record',
            '/erp/stock/in', '/erp/stock/out',
        ):
            with self.subTest(path=path):
                self.assertIn("'" + path + "'", script)
        self.assertIn('CREATE TEMPORARY TABLE rehab_reviewed_extra_button', script)
        for permission in ('erp:stock-in:update-status', 'erp:stock-out:update-status'):
            self.assertIn("'" + permission + "'", script)
        for unreviewed in (
            "'/erp/stock/move'", "'/erp/stock/check'",
            'erp:stock-in:export', 'erp:stock-out:export',
        ):
            self.assertNotIn(unreviewed, script)


if __name__ == '__main__':
    unittest.main()
