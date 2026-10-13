import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { resolveLoginTenant } from '../src/utils/loginTenant.ts'

test('trim organization name and retain server-issued id', async () => {
  assert.equal(await resolveLoginTenant('  SaaS合成平台  ', async name => {
    assert.equal(name, 'SaaS合成平台'); return 12
  }), 12)
})
test('blank organization never calls lookup', async () => {
  let called = false
  await assert.rejects(resolveLoginTenant('  ', async () => { called = true; return 1 }), /请输入机构名称/)
  assert.equal(called, false)
})
for (const [label, value] of Object.entries({ null: null, undefined: undefined, zero: 0, negative: -1, string: '1', object: {}, nan: NaN, infinity: Infinity })) {
  test(`reject invalid tenant result: ${label}`, async () => {
    await assert.rejects(resolveLoginTenant('synthetic', async () => value), /未找到该机构/)
  })
}
test('lookup failure cannot become a default tenant', async () => {
  await assert.rejects(resolveLoginTenant('synthetic', async () => { throw new Error('offline') }), /offline/)
})
test('installer declares UTF-8 before owner bootstrap writes', () => {
  const installer = readFileSync(new URL('../../../deploy/saas/install-synthetic.sh', import.meta.url), 'utf8')
  const owner = installer.split('cat > "$STATE/owner.sql" <<SQL')[1].split('\nSQL')[0]
  assert.ok(owner.indexOf('SET NAMES utf8mb4;') >= 0)
  assert.ok(owner.indexOf('SET NAMES utf8mb4;') < owner.indexOf('START TRANSACTION;'))
})
test('installer refuses state below the source tree', () => {
  const installer = readFileSync(new URL('../../../deploy/saas/install-synthetic.sh', import.meta.url), 'utf8')
  assert.match(installer, /Refusing state path inside the source tree/)
  assert.match(installer, /case "\$STATE\/" in/)
  assert.match(installer, /"\$ROOT\/"\*\)/)
})
test('installer refuses symlinked state parents', () => {
  const installer = readFileSync(new URL('../../../deploy/saas/install-synthetic.sh', import.meta.url), 'utf8')
  assert.match(installer, /Refusing state path below a symlinked parent/)
  assert.match(installer, /\[\[ ! -L "\$\(dirname "\$STATE"\)" \]\]/)
})
test('organization lookup uses encoded params instead of URL concatenation', () => {
  const api = readFileSync(new URL('../src/api/login/index.ts', import.meta.url), 'utf8')
  assert.match(api, /url: '\/system\/tenant\/get-id-by-name', params: \{ name: normalized \}/)
})
