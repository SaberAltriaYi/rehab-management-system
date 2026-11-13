import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const read = path => readFileSync(new URL(path, import.meta.url), 'utf8')

test('recovery maintenance bundle is separate, contains no clinical runtime and no remote web assets', () => {
  const config = JSON.parse(read('../src-tauri/tauri.recovery-maintenance.conf.json'))
  assert.equal(config.identifier, 'com.saberaltriayi.rehab.adminrecovery')
  assert.deepEqual(config.bundle.resources, [])
  assert.deepEqual(config.app.security.capabilities, ['recovery-maintenance'])
  assert.match(config.app.security.csp, /script-src 'self'/)
  assert.doesNotMatch(config.app.security.csp, /'unsafe-eval'/)
})

test('capabilities do not permit destructive file, shell or business operations', () => {
  const capability = JSON.parse(read('../src-tauri/capabilities/recovery-maintenance.json'))
  assert.deepEqual(capability.permissions, ['core:default'])
})

test('maintenance native IPC registers only status, challenge and authorized recovery', () => {
  const src = read('../src-tauri/src/lib.rs')
  const handler = src.split('pub fn run() {').at(-1).split('.invoke_handler(')[1].split('])')[0]
  assert.match(handler, /recovery_maintenance_status/)
  assert.match(handler, /begin_admin_recovery/)
  assert.match(handler, /recovery_maintenance_apply/)
  for (const forbidden of ['delete_all_data', 'update_admin_credentials', 'start_services',
    'import_full_transfer', 'save_settings', 'renew_license_online', 'get_overview']) {
    assert.ok(!handler.includes(forbidden), forbidden)
  }
})

test('maintenance UI never invokes unrelated commands or exports credentials', () => {
  const source = read('./main.ts')
  const invoked = [...source.matchAll(/invoke(?:<[^>]+>)?\('([^']+)'/g)].map(match => match[1])
  assert.deepEqual([...new Set(invoked)].sort(), [
    'begin_admin_recovery', 'recovery_maintenance_apply', 'recovery_maintenance_status'
  ])
  assert.match(source, /password\.value = ''/)
  assert.match(source, /confirmation\.value = ''/)
  assert.match(source, /approval\.value = ''/)
  assert.doesNotMatch(source, /console\.(log|error|warn)|localStorage|sessionStorage|fetch\(/)
})

test('ordinary clinical launcher MVP release gate remains untouched', () => {
  const pkg = JSON.parse(read('../package.json'))
  assert.match(pkg.scripts['package:mvp:dmg'], /prepackage:mvp/)
  assert.match(pkg.scripts['prepackage:mvp'], /mvp306-release-gate/)
})

test('recovery action is never silently disabled by absent challenge or health state', () => {
  const source = read('./main.ts')
  assert.match(source, /applyButton\.disabled = working\b/)
  assert.doesNotMatch(source, /applyButton\.disabled = working \|\| !healthy/)
  assert.match(source, /if \(!healthy\) \{\s*report\('无法恢复：/)
  assert.match(source, /无法恢复：请先在本维护工具点击/)
  assert.match(source, /id="action-guide"/)
})
