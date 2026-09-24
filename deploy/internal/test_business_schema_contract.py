#!/usr/bin/env python3
"""Static column/type coverage for additive business MySQL migrations.

This is NOT an application MyBatis/tenant-permission integration test.
"""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
CANDIDATES = {
    'report': ('sql/mysql/rehab-report-go-view-project-v1.sql', 1),
    'member': ('sql/mysql/rehab-member-remaining-v1.sql', 7),
    'bpm': ('sql/mysql/rehab-bpm-core-v1.sql', 8),
    'erp': ('sql/mysql/rehab-erp-core-v1.sql', 33),
}
AUDIT = {'creator', 'create_time', 'updater', 'update_time', 'deleted', 'tenant_id'}


def snake(word):
    return re.sub(r'(?<!^)(?=[A-Z])', '_', word).lower()


class BusinessSchemaContractTest(unittest.TestCase):
    def test_do_fields_and_types_covered_by_candidate_mysql_ddl(self):
        total = 0
        for module, (path, count) in CANDIDATES.items():
            text = (ROOT / path).read_text()
            self.assertNotRegex(text, r'(?im)^\s*(DROP|TRUNCATE|DELETE|UPDATE|REPLACE)\s')
            self.assertNotIn('CREATE TABLE IF NOT EXISTS', text)
            tables = dict(re.findall(r'CREATE TABLE `([^`]+)` \((.*?)\n\) ENGINE=', text, re.S))
            self.assertEqual(len(tables), count, module)
            sources = {}
            for file in (ROOT / ('yudao-module-' + module) / 'src/main/java').rglob('*DO.java'):
                source = file.read_text()
                annotation = re.search(r'@TableName\(\s*(?:value\s*=\s*)?"([^"]+)"', source)
                if annotation and annotation.group(1) in tables:
                    sources[annotation.group(1)] = source
            self.assertEqual(set(sources), set(tables), module)
            for table, body in tables.items():
                with self.subTest(table=table):
                    columns = dict(re.findall(r'^\s{2}`([a-z_]+)`\s+([A-Z]+)(?:\([^)]+\))?', body, re.M))
                    self.assertTrue(AUDIT <= set(columns), table)
                    self.assertEqual(columns.get('id'), 'BIGINT', table)
                    self.assertEqual(columns['tenant_id'], 'BIGINT', table)
                    fields = re.findall(r'private\s+([^\s;]+)\s+(\w+)\s*;', sources[table])
                    self.assertTrue(fields, table)
                    for java_type, name in fields:
                        column = snake(name)
                        self.assertIn(column, columns, (table, name))
                        sql_type = columns[column]
                        if java_type == 'BigDecimal':
                            self.assertEqual(sql_type, 'DECIMAL', (table, name))
                        elif java_type == 'Long':
                            self.assertEqual(sql_type, 'BIGINT', (table, name))
                        elif java_type == 'Integer':
                            self.assertIn(sql_type, ('INT', 'TINYINT'), (table, name))
                        elif java_type == 'Boolean':
                            self.assertEqual(sql_type, 'BIT', (table, name))
                        elif java_type == 'LocalDateTime':
                            self.assertEqual(sql_type, 'DATETIME', (table, name))
                        elif java_type == 'String':
                            self.assertIn(sql_type, ('VARCHAR', 'TEXT', 'LONGTEXT'), (table, name))
                        elif java_type.startswith(('List<', 'Set<', 'BpmModelMetaInfoVO.')):
                            self.assertIn(sql_type, ('TEXT', 'LONGTEXT'), (table, name))
                        else:
                            self.fail((table, name, java_type))
                    total += 1
        self.assertEqual(total, 49)


if __name__ == '__main__':
    unittest.main()
