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


if __name__ == '__main__':
    unittest.main()
