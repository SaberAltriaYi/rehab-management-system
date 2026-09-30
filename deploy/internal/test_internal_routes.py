"""内部版前端只加载 remaining.internal.ts；康复模块的隐藏详情路由必须同步到内部路由表。"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'yudao-ui/yudao-ui-admin-vue3-app/src'


def rehab_child_paths(text):
    block = text[text.index("path: '/rehab',"):]
    block = block[:block.index("\n  }", 0)]
    return set(re.findall(r"path: '([^'/][^']*)'", block))


class InternalRoutesTest(unittest.TestCase):
    def test_router_uses_internal_route_table(self):
        self.assertIn("from './modules/remaining.internal'", (APP / 'router/index.ts').read_text())

    def test_rehab_hidden_routes_are_available_in_internal_build(self):
        full = rehab_child_paths((APP / 'router/modules/remaining.ts').read_text())
        internal = rehab_child_paths((APP / 'router/modules/remaining.internal.ts').read_text())
        self.assertIn('motion/detail/:id', internal)
        self.assertEqual(sorted(full - internal), [])

    def test_views_only_push_to_registered_rehab_detail_routes(self):
        internal = rehab_child_paths((APP / 'router/modules/remaining.internal.ts').read_text())
        patterns = [re.compile('^' + re.sub(r':\w+', r'[^/]+', p) + '$') for p in internal]
        missing = []
        for vue in (APP / 'views/rehab').rglob('*.vue'):
            for target in re.findall(r"push\(`/rehab/([^`?]+?)(?:\?[^`]*)?`\)", vue.read_text(encoding='utf-8')):
                probe = re.sub(r'\$\{[^}]+\}', 'X', target)
                if '/' in probe and not any(p.match(probe) for p in patterns):
                    missing.append(f'{vue.name}: /rehab/{target}')
        self.assertEqual(missing, [])


if __name__ == '__main__':
    unittest.main()
