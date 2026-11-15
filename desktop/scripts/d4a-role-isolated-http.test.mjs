import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { test } from 'node:test'

const root = dirname(fileURLToPath(import.meta.url))
const source = resolve(root, 'd4a-role-isolated-http.py')

test('D4-A HTTP fixture has explicit synthetic-only scope gate before any Docker work', () => {
  const code = readFileSync(source, 'utf8')
  assert.match(code, /if len\(sys\.argv\)!=2 or sys\.argv\[1\]!='--synthetic-only':/)
  assert.match(code, /docker\('network','create','--internal'/)
  assert.match(code, /'--tmpfs','\/var\/lib\/mysql/)
  assert.match(code, /'--tmpfs','\/app\/data\/rehab/)
  assert.doesNotMatch(code, /'--publish'|'--volume'|'--privileged'|'--network','host'/)
})

test('D4-A HTTP fixture asserts exact 400/403 business codes and no role mutation', () => {
  const code = readFileSync(source, 'utf8')
  assert.match(code, /tenant-admin-reviewer-role-request-400',business_code\(st,body,400\)/)
  assert.match(code, /tenant-admin-cross-tenant-user-403',business_code\(st,body,403\)/)
  assert.match(code, /tenant-admin-self-role-change-403',business_code\(st,body,403\)/)
  assert.match(code, /therapist-cannot-assign-staff-role-403',business_code\(st,body,403\)/)
  assert.match(code, /tenant-admin-invalid-target-id-400',business_code\(st,body,400\)/)
  assert.match(code, /cross-tenant-target-role-unchanged/)
  assert.match(code, /admin-self-role-unchanged/)
})

for (const args of [[], ['--help'], ['--live'], ['--synthetic-only', '--live']]) {
  test(`D4-A rejected invocation ${args.length ? args.join(' ') : '(empty)'}`, () => {
    const result = spawnSync('python3', ['-B', source, ...args], {
      cwd: resolve(root, '..', '..'), timeout: 6500, encoding: 'utf8'
    })
    assert.equal(result.status, 2)
    assert.match(result.stderr, /D4A_SYNTHETIC_ONLY_FLAG_REQUIRED/)
    assert.doesNotMatch(result.stdout, /PASS |docker |resource/)
  })
}
