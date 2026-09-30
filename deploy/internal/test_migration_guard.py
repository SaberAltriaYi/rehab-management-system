#!/usr/bin/env python3
"""Migration guard contract tests; no real DB by default.

Opt-in MySQL integration: REHAB_TEST_MYSQL_CONTAINER=<dedicated-test-container>
python3 -m unittest discover -s deploy/internal -p 'test_migration_guard.py' -v

The integration tests create uniquely named synthetic databases in that container,
never open its application database, and remove only their own test databases.
"""
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[2]


class GuardFixture:
    def __init__(self):
        self.temp = tempfile.TemporaryDirectory(prefix='rehab-guard-')
        self.root = Path(self.temp.name)
        self.deploy = self.root / 'deploy/internal'
        self.deploy.mkdir(parents=True)
        for name in ('migrate.sh', 'migrations.manifest', 'init-schema-history.sql'):
            shutil.copyfile(ROOT / 'deploy/internal' / name, self.deploy / name)
        self.rows = []
        for line in (self.deploy / 'migrations.manifest').read_text().splitlines():
            if not line or line.startswith('#'):
                continue
            version, checksum, path, description = line.split('|')
            dest = self.root / path
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / path, dest)
            self.rows.append((version, checksum, path, description))
        (self.deploy / '.env').write_text('# synthetic test fixture; no credentials\n')
        (self.deploy / 'docker-compose.yml').write_text('# test adapter only\n')
        self.bin = self.root / 'bin'
        self.bin.mkdir()
        self.state = self.root / 'state.json'
        self.log = self.root / 'queries.txt'
        self.set_state()
        self.env = dict(os.environ)
        self.env.pop('CONFIRM_BASELINE', None)
        self.env['ENV_FILE_OVERRIDE'] = str(self.deploy / '.env')
        self.env['PATH'] = str(self.bin) + os.pathsep + os.environ['PATH']
        self.env['GUARD_STATE'] = str(self.state)
        self.env['GUARD_LOG'] = str(self.log)
        self.install_adapter('''import json, os, sys
sql = sys.stdin.read()
with open(os.environ['GUARD_LOG'], 'a') as out:
    out.write(sql + '\\n')
state = json.load(open(os.environ['GUARD_STATE']))
if 'information_schema.tables' in sql:
    print(1 if state['ledger'] else 0)
elif sql.strip() == 'SELECT version, checksum FROM internal_schema_history ORDER BY version;':
    for version, checksum in state['rows']:
        print(version + '\\t' + checksum)
else:
    print('Unexpected SQL write in read-only fixture', file=sys.stderr)
    sys.exit(99)
''')

    def install_adapter(self, source):
        target = self.bin / 'docker'
        target.write_text('#!/usr/bin/env python3\n' + source)
        target.chmod(0o700)

    def set_state(self, ledger=True, missing=(), extra=()):
        rows = [[v, c] for v, c, _, _ in self.rows if v not in missing] + list(extra)
        self.state.write_text(json.dumps({'ledger': ledger, 'rows': rows}))

    def run(self, mode='status', *args, **env):
        return subprocess.run(['sh', str(self.deploy / 'migrate.sh'), mode, *args],
                              env={**self.env, **env}, text=True, capture_output=True, timeout=60)

    def assert_read_only(self, case):
        sql = self.log.read_text() if self.log.exists() else ''
        for statement in sql.split(';'):
            if statement.strip():
                case.assertTrue(statement.strip().startswith('SELECT '), statement)

    def close(self):
        self.temp.cleanup()


class MigrationGuardTests(unittest.TestCase):
    def setUp(self):
        self.fixture = GuardFixture()
        self.addCleanup(self.fixture.close)

    def test_manifest_checksums_unchanged(self):
        result = self.fixture.run('verify-files')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertFalse(self.fixture.log.exists())

    def test_fresh_database_ledger_contains_only_immutable_baseline(self):
        ledger = (ROOT / 'deploy/internal/init-schema-history.sql').read_text()
        versions = re.findall(r"^\s*\('(\d{3})',", ledger, re.MULTILINE)
        self.assertEqual(versions, [f'{version:03d}' for version in range(1, 20)])

    def test_status_complete_is_read_only(self):
        result = self.fixture.run()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.fixture.assert_read_only(self)

    def test_status_missing_ledger_does_not_create_it(self):
        self.fixture.set_state(ledger=False)
        result = self.fixture.run()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('账本缺失', result.stderr)
        self.fixture.assert_read_only(self)

    def test_apply_missing_ledger_fails_closed(self):
        self.fixture.set_state(ledger=False)
        self.assertNotEqual(self.fixture.run('apply').returncode, 0)
        self.fixture.assert_read_only(self)

    def test_apply_missing_cleanup_never_executes_it(self):
        self.fixture.set_state(missing=('015',))
        result = self.fixture.run('apply')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('禁止升级重放初始化迁移 015', result.stderr)
        self.fixture.assert_read_only(self)

    def test_apply_missing_early_seed_fails_before_any_write(self):
        self.fixture.set_state(missing=('003', '015'))
        result = self.fixture.run('apply')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('初始化迁移 003', result.stderr)
        self.fixture.assert_read_only(self)

    def test_all_historical_versions_block_replay(self):
        for version, _, _, _ in self.fixture.rows:
            if int(version) > 19:  # 020+ are separately reviewed additive upgrades
                continue
            with self.subTest(version=version):
                self.fixture.set_state(missing=(version,))
                result = self.fixture.run('apply')
                self.assertNotEqual(result.returncode, 0)
                self.assertIn('初始化迁移 ' + version, result.stderr)
        self.fixture.assert_read_only(self)

    def test_new_versions_cannot_be_false_baselined(self):
        result = self.fixture.run('baseline', '023', CONFIRM_BASELINE='BASELINE-REHAB-INTERNAL')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('001-019', result.stderr)
        self.assertFalse(self.fixture.log.exists())

    def test_new_erp_migration_is_pending_when_not_installed(self):
        self.fixture.set_state(missing=('023',))
        result = self.fixture.run('status')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('PENDING: 023', result.stdout)
        self.fixture.assert_read_only(self)

    def test_new_bpm_migration_is_pending_when_not_installed(self):
        self.fixture.set_state(missing=('022',))
        result = self.fixture.run('status')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('PENDING: 022', result.stdout)
        self.fixture.assert_read_only(self)

    def test_new_member_migration_is_pending_when_not_installed(self):
        self.fixture.set_state(missing=('021',))
        result = self.fixture.run('status')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('PENDING: 021', result.stdout)
        self.fixture.assert_read_only(self)

    def test_new_report_migration_is_pending_when_not_installed(self):
        self.fixture.set_state(missing=('020',))
        result = self.fixture.run('status')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('PENDING: 020', result.stdout)
        self.fixture.assert_read_only(self)

    def test_apply_current_database_is_noop(self):
        result = self.fixture.run('apply')
        self.assertEqual(result.returncode, 0, result.stderr)
        self.fixture.assert_read_only(self)

    def test_unknown_database_version_blocks_apply(self):
        self.fixture.set_state(extra=[['999', 'a' * 64]])
        result = self.fixture.run('apply')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('不认识的迁移', result.stderr)
        self.fixture.assert_read_only(self)

    def test_history_checksum_drift_blocks_apply(self):
        self.fixture.set_state(missing=('019',), extra=[['019', '0' * 64]])
        result = self.fixture.run('apply')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('校验和漂移', result.stderr)
        self.fixture.assert_read_only(self)

    def test_changed_sql_stops_before_database_access(self):
        (self.fixture.deploy / 'clean-demo-rehab-data.sql').write_text('SELECT 1;\n')
        result = self.fixture.run('apply')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('校验和漂移', result.stderr)
        self.assertFalse(self.fixture.log.exists())

    def test_baseline_requires_explicit_acknowledgement(self):
        result = self.fixture.run('baseline', '015')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('CONFIRM_BASELINE', result.stderr)
        self.assertFalse(self.fixture.log.exists())

    def test_baseline_cannot_create_missing_ledger(self):
        self.fixture.set_state(ledger=False)
        result = self.fixture.run('baseline', '015', CONFIRM_BASELINE='BASELINE-REHAB-INTERNAL')
        self.assertNotEqual(result.returncode, 0)
        self.fixture.assert_read_only(self)

    def test_invalid_baseline_rejected_before_database_access(self):
        result = self.fixture.run('baseline', '015;DROP')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.fixture.log.exists())


MYSQL_STUB = r'''#!/usr/bin/env python3
import json, os, sys
sql = sys.stdin.read()
with open(os.environ['INIT_LOG'], 'a') as out:
    out.write(json.dumps({'args': sys.argv[1:], 'sql': sql[:400], 'len': len(sql.encode())}) + '\n')
fail = os.environ.get('INIT_FAIL_MARKER')
if fail and fail in sql:
    sys.exit(1)
'''


class FreshInitIncrementalMigrationTests(unittest.TestCase):
    """全新数据卷：020+ 由 111-incremental-migrations.sh 实际执行后登记，不预登记、不直接挂载。"""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='rehab-init-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'opt'
        (self.root / 'sql/mysql').mkdir(parents=True)
        shutil.copyfile(ROOT / 'deploy/internal/migrations.manifest', self.root / 'migrations.manifest')
        self.rows = []
        for line in (self.root / 'migrations.manifest').read_text().splitlines():
            if not line or line.startswith('#'):
                continue
            version, checksum, path, description = line.split('|')
            self.rows.append((version, checksum, path, description))
            if int(version) > 19:
                shutil.copyfile(ROOT / path, self.root / path)
        self.bin = Path(self.temp.name) / 'bin'
        self.bin.mkdir()
        self.log = Path(self.temp.name) / 'calls.jsonl'
        stub = self.bin / 'mysql'
        stub.write_text(MYSQL_STUB)
        stub.chmod(0o700)

    def run_init(self, **env):
        full = dict(os.environ)
        full.update(REHAB_MIGRATION_ROOT=str(self.root), REHAB_MYSQL_BIN=str(self.bin / 'mysql'),
                    INIT_LOG=str(self.log), MYSQL_DATABASE='synthetic', MYSQL_ROOT_PASSWORD='synthetic-not-secret')
        full.update(env)
        return subprocess.run(['bash', str(ROOT / 'deploy/internal/init-incremental-migrations.sh')],
                              env=full, text=True, capture_output=True, timeout=60)

    def calls(self):
        if not self.log.exists():
            return []
        return [json.loads(line) for line in self.log.read_text().splitlines()]

    def incremental(self):
        return [row for row in self.rows if int(row[0]) > 19]

    def test_compose_runs_ledgered_init_after_baseline_and_never_mounts_incremental_sql(self):
        compose = (ROOT / 'deploy/internal/docker-compose.yml').read_text()
        self.assertIn('./init-schema-history.sql:/docker-entrypoint-initdb.d/110-schema-history.sql:ro', compose)
        self.assertIn('./init-incremental-migrations.sh:/docker-entrypoint-initdb.d/111-incremental-migrations.sh:ro',
                      compose)
        self.assertIn('./migrations.manifest:/opt/rehab-migrations/migrations.manifest:ro', compose)
        self.assertIn('../../sql/mysql:/opt/rehab-migrations/sql/mysql:ro', compose)
        mounted = re.findall(r'(\S+\.sql):/docker-entrypoint-initdb.d/', compose)
        for _, _, path, _ in self.incremental():
            self.assertNotIn('../../' + path, mounted, path)

    def test_applies_every_incremental_migration_in_order_then_registers_it(self):
        result = self.run_init()
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.calls()
        expected = self.incremental()
        self.assertTrue(expected)
        self.assertEqual(len(calls), 2 * len(expected))
        for index, (version, checksum, path, description) in enumerate(expected):
            body, ledger = calls[2 * index], calls[2 * index + 1]
            self.assertEqual(body['len'], (ROOT / path).stat().st_size, path)
            self.assertIn("'%s','%s','%s','%s','docker-init',b'0'," % (version, checksum, path, description),
                          ledger['sql'])
            self.assertIn('--protocol=socket', body['args'])
        self.assertIn('PASS', result.stdout)

    def test_checksum_drift_fails_before_executing_anything(self):
        _, _, path, _ = self.incremental()[-1]
        with open(self.root / path, 'a') as handle:
            handle.write('\n-- drift\n')
        result = self.run_init()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('校验和漂移', result.stderr)
        self.assertEqual(self.calls(), [])

    def test_failed_migration_is_not_registered_and_stops_later_ones(self):
        first = self.incremental()[0]
        marker = (ROOT / first[2]).read_text().strip().splitlines()[0]
        result = self.run_init(INIT_FAIL_MARKER=marker)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual([c for c in self.calls() if 'internal_schema_history' in c['sql']], [])
        self.assertEqual(len(self.calls()), 1)

    def test_fresh_ledger_after_init_equals_manifest(self):
        baseline = re.findall(r"^\s*\('(\d{3})',", (ROOT / 'deploy/internal/init-schema-history.sql').read_text(),
                              re.MULTILINE)
        result = self.run_init()
        self.assertEqual(result.returncode, 0, result.stderr)
        registered = re.findall(r"VALUES \('(\d{3})'", ''.join(c['sql'] for c in self.calls()))
        self.assertEqual(baseline + registered, [row[0] for row in self.rows])

    def test_new_motion_migration_is_pending_when_not_installed(self):
        fixture = GuardFixture()
        self.addCleanup(fixture.close)
        latest = self.incremental()[-1][0]
        fixture.set_state(missing=(latest,))
        result = fixture.run('status')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('PENDING: ' + latest, result.stdout)
        fixture.assert_read_only(self)


ADOPT_ADAPTER = r'''import json, os, re, sys
sql = sys.stdin.read()
with open(os.environ['GUARD_LOG'], 'a') as out:
    out.write(sql + '\n')
state = json.load(open(os.environ['GUARD_STATE']))
if 'information_schema.tables' in sql:
    print(1)
elif sql.strip() == 'SELECT version, checksum FROM internal_schema_history ORDER BY version;':
    for version, checksum in state['rows']:
        print(version + '\t' + checksum)
elif 'information_schema.columns' in sql:
    table = re.search(r"table_name = '([A-Za-z0-9_]+)'", sql).group(1)
    print(state['columns'].get(table, ''))
elif sql.startswith('INSERT INTO internal_schema_history'):
    pass
else:
    print('Unexpected SQL in adopt fixture', file=sys.stderr)
    sys.exit(99)
'''


def parse_create_tables(path):
    tables, current = {}, None
    for line in path.read_text().splitlines():
        match = re.match(r'^CREATE TABLE `([A-Za-z0-9_]+)` \($', line)
        if match:
            current = match.group(1)
            tables[current] = []
        elif current and line.startswith(')'):
            current = None
        elif current:
            column = re.match(r'^  `([A-Za-z0-9_]+)`', line)
            if column:
                tables[current].append(column.group(1))
    return {name: ','.join(columns) for name, columns in tables.items()}


class AdoptLegacyInitdbTests(unittest.TestCase):
    """旧版 Compose 已直接建表但未登记：adopt 只在列结构完全一致时登记，绝不执行建表 SQL。"""

    def setUp(self):
        self.fixture = GuardFixture()
        self.addCleanup(self.fixture.close)
        self.fixture.install_adapter(ADOPT_ADAPTER)
        self.incremental = [row for row in self.fixture.rows if int(row[0]) > 19]
        self.first = self.incremental[0]

    def state(self, missing, columns):
        rows = [[v, c] for v, c, _, _ in self.fixture.rows if v not in missing]
        self.fixture.state.write_text(json.dumps({'ledger': True, 'rows': rows, 'columns': columns}))

    def statements(self):
        return self.fixture.log.read_text() if self.fixture.log.exists() else ''

    def test_adopt_registers_matching_legacy_tables_without_executing_sql(self):
        version, checksum, path, description = self.first
        missing = tuple(row[0] for row in self.incremental)
        self.state(missing, parse_create_tables(ROOT / path))
        result = self.fixture.run('adopt', version, CONFIRM_ADOPT='ADOPT-REHAB-INITDB')
        self.assertEqual(result.returncode, 0, result.stderr)
        log = self.statements()
        self.assertNotIn('CREATE TABLE', log)
        self.assertIn("VALUES ('%s','%s','%s','%s','adopt-verified',b'0',0);" % (version, checksum, path, description),
                      log)

    def test_adopt_refuses_when_columns_differ(self):
        version, _, path, _ = self.first
        columns = parse_create_tables(ROOT / path)
        table = sorted(columns)[0]
        columns[table] = columns[table].rsplit(',', 1)[0]
        self.state(tuple(row[0] for row in self.incremental), columns)
        result = self.fixture.run('adopt', version, CONFIRM_ADOPT='ADOPT-REHAB-INITDB')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('列结构', result.stderr)
        self.assertNotIn('INSERT', self.statements())

    def test_adopt_refuses_when_table_missing(self):
        version = self.first[0]
        self.state(tuple(row[0] for row in self.incremental), {})
        result = self.fixture.run('adopt', version, CONFIRM_ADOPT='ADOPT-REHAB-INITDB')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('INSERT', self.statements())

    def test_adopt_requires_confirmation_and_incremental_version(self):
        result = self.fixture.run('adopt', self.first[0])
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('CONFIRM_ADOPT', result.stderr)
        result = self.fixture.run('adopt', '015', CONFIRM_ADOPT='ADOPT-REHAB-INITDB')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(self.fixture.log.exists())

    def test_adopt_must_follow_manifest_order(self):
        second = self.incremental[1]
        self.state(tuple(row[0] for row in self.incremental), parse_create_tables(ROOT / second[2]))
        result = self.fixture.run('adopt', second[0], CONFIRM_ADOPT='ADOPT-REHAB-INITDB')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('按顺序', result.stderr)
        self.assertNotIn('INSERT', self.statements())

    def test_adopt_rejects_scripts_that_are_not_create_only(self):
        non_create = [row for row in self.incremental
                      if any(line.startswith('INSERT') for line in (ROOT / row[2]).read_text().splitlines())]
        if not non_create:
            self.skipTest('no non-create-only incremental migration in manifest')
        version = non_create[0][0]
        earlier = tuple(row[0] for row in self.incremental if row[0] >= version)
        self.state(earlier, {})
        result = self.fixture.run('adopt', version, CONFIRM_ADOPT='ADOPT-REHAB-INITDB')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('不是纯建表脚本', result.stderr)
        self.assertNotIn('INSERT', self.statements())


@unittest.skipUnless(os.environ.get('REHAB_TEST_MYSQL_CONTAINER'), 'real MySQL explicitly opt-in')
class MigrationGuardMySQLTests(unittest.TestCase):
    def setUp(self):
        self.container = os.environ['REHAB_TEST_MYSQL_CONTAINER']
        self.docker = shutil.which('docker')
        self.database = 'rehab_guard_test_' + uuid.uuid4().hex
        self.fixture = GuardFixture()
        self.addCleanup(self.fixture.close)
        self.sql('CREATE DATABASE `' + self.database + '`;')
        self.addCleanup(self.drop_database)
        self.sql('CREATE TABLE rehab_patient(id BIGINT PRIMARY KEY, marker VARCHAR(80));'
                 "INSERT INTO rehab_patient VALUES (1, 'synthetic-do-not-delete');", use_database=True)
        self.fixture.env.update(GUARD_DOCKER=self.docker, GUARD_CONTAINER=self.container,
                                GUARD_DATABASE=self.database)
        self.fixture.install_adapter('''import os, subprocess, sys
sql = sys.stdin.read()
with open(os.environ['GUARD_LOG'], 'a') as out:
    out.write(sql + '\\n')
command = [os.environ['GUARD_DOCKER'], 'exec', '-i', os.environ['GUARD_CONTAINER'],
           'sh', '-c', 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot "$1" --batch --raw --skip-column-names',
           'guard-adapter', os.environ['GUARD_DATABASE']]
sys.exit(subprocess.run(command, input=sql, text=True).returncode)
''')

    def sql(self, text, use_database=False):
        script = 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot --batch --raw --skip-column-names'
        args = [self.docker, 'exec', '-i', self.container, 'sh', '-c',
                script + (' "$1"' if use_database else ''), 'guard-test']
        if use_database:
            args.append(self.database)
        result = subprocess.run(args, input=text, text=True, capture_output=True, timeout=30)
        self.assertEqual(result.returncode, 0, result.stderr)
        return result.stdout.strip()

    def drop_database(self):
        if not self.database.startswith('rehab_guard_test_'):
            raise RuntimeError('Refusing to drop an application database')
        self.sql('DROP DATABASE `' + self.database + '`;')

    def ledger(self, missing=()):
        self.sql('CREATE TABLE internal_schema_history(version VARCHAR(32) PRIMARY KEY, checksum CHAR(64));', True)
        for version, checksum, _, _ in self.fixture.rows:
            if version not in missing:
                self.sql("INSERT INTO internal_schema_history VALUES ('%s','%s');" % (version, checksum), True)

    def assert_sentinel(self):
        self.assertEqual(self.sql('SELECT marker FROM rehab_patient WHERE id=1;', True),
                         'synthetic-do-not-delete')
        self.fixture.assert_read_only(self)

    def test_real_missing_ledger_keeps_schema_and_data(self):
        for mode in ('status', 'apply'):
            self.assertNotEqual(self.fixture.run(mode).returncode, 0)
        self.assertEqual(self.sql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='internal_schema_history';", True), '0')
        self.assert_sentinel()

    def test_real_missing_cleanup_is_blocked(self):
        self.ledger(missing=('015',))
        result = self.fixture.run('apply')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('初始化迁移 015', result.stderr)
        self.assertEqual(self.sql('SELECT COUNT(*) FROM internal_schema_history;', True),
                         str(len(self.fixture.rows) - 1))
        self.assert_sentinel()

    def test_real_complete_history_is_noop(self):
        self.ledger()
        for mode in ('status', 'apply'):
            result = self.fixture.run(mode)
            self.assertEqual(result.returncode, 0, result.stderr)
        self.assert_sentinel()


class ShellPortabilityTests(unittest.TestCase):
    """macOS 自带 bash 3.2 会把紧跟变量名的非 ASCII 字节当作变量名一部分（如 $version（ → version\xef），
    在 set -u 下直接报 unbound variable，因此变量后紧跟中文/全角字符时必须写成 ${var}。"""

    def test_no_bare_variable_followed_by_non_ascii(self):
        pattern = re.compile(r'\$[A-Za-z_][A-Za-z0-9_]*[^\x00-\x7f]')
        for script in sorted((ROOT / 'deploy/internal').glob('*.sh')):
            for number, line in enumerate(script.read_text(encoding='utf-8').splitlines(), 1):
                self.assertIsNone(pattern.search(line), '%s:%d 需改为 ${var}' % (script.name, number))


if __name__ == '__main__':
    unittest.main()
