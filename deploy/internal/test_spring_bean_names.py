"""静态守卫：康复模块 @Resource 字段名不得与仓库内其他 Spring Bean 名称冲突。

@Resource 在未显式指定 name 时先按字段名查找 Bean；若字段名恰好是另一个模块的 Bean 名
（例如 infra 模块的 FileMapper → Bean 名 fileMapper），Spring 启动时会抛出
BeanNotOfRequiredTypeException。Mockito 单元测试按类型注入，无法发现该问题，
因此在这里做全仓库源码级检查。
"""
import os
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STEREOTYPE = re.compile(r'@(Service|Component|Repository|Configuration|RestController|Controller|Mapper)\b(?:\(\s*"([^"]+)"\s*\))?')
MAPPER_IFACE = re.compile(r'interface\s+\w+\s+extends\s+BaseMapperX\b')
BEAN_METHOD = re.compile(r'@Bean(?:\(([^)]*)\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*public\s+[\w<>, ?.]+\s+(\w+)\s*\(')
RESOURCE_FIELD = re.compile(r'@Resource(\([^)]*\))?\s*(?:@\w+(?:\([^)]*\))?\s*)*private\s+(?:final\s+)?([\w<>.,? ]+?)\s+(\w+)\s*;')


def _java_sources(base):
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames[:] = [d for d in dirnames if d not in {'node_modules', 'target', '.git', 'dist', 'test'}]
        for name in filenames:
            if name.endswith('.java') and not name.startswith('._'):
                yield Path(dirpath) / name


def _decap(name):
    return name[:1].lower() + name[1:]


def collect_beans(root):
    beans = {}
    for path in _java_sources(root):
        if '/src/main/java/' not in path.as_posix():
            continue
        text = path.read_text(encoding='utf-8', errors='ignore')
        cls = path.stem
        m = STEREOTYPE.search(text)
        if m or MAPPER_IFACE.search(text):
            bean = m.group(2) if m and m.group(2) else _decap(cls)
            beans.setdefault(bean, set()).add(cls)
        for bm in BEAN_METHOD.finditer(text):
            explicit = re.search(r'"([^"]+)"', bm.group(1) or '')
            beans.setdefault(explicit.group(1) if explicit else bm.group(2), set()).add('@Bean:' + cls)
    return beans


def find_collisions(root, module='yudao-module-rehab'):
    beans = collect_beans(root)
    collisions = []
    for path in _java_sources(root / module):
        if '/src/main/java/' not in path.as_posix():
            continue
        text = path.read_text(encoding='utf-8', errors='ignore')
        for m in RESOURCE_FIELD.finditer(text):
            if m.group(1) and 'name' in m.group(1):
                continue
            field_type = m.group(2).split('<')[0].split('.')[-1].strip()
            field = m.group(3)
            owners = {o for o in beans.get(field, set()) if o != field_type and not o.startswith(field_type)}
            if owners:
                collisions.append(f'{path.name}: {field_type} {field} -> {sorted(owners)}')
    return collisions


class SpringBeanNameTest(unittest.TestCase):
    def test_scanner_detects_known_collision_shape(self):
        beans = collect_beans(ROOT)
        # infra 模块的 FileMapper 注册为 fileMapper；这正是曾导致容器启动失败的名称。
        self.assertIn('FileMapper', beans.get('fileMapper', set()))

    def test_rehab_resource_fields_do_not_shadow_other_beans(self):
        self.assertEqual([], find_collisions(ROOT))


if __name__ == '__main__':
    unittest.main()
