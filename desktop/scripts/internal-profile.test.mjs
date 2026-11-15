import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const root = new URL('../../', import.meta.url)
test('internal overlay is distinct and does not alter the commercial identifier', () => {
  const internal = JSON.parse(readFileSync(new URL('desktop/launcher/src-tauri/tauri.internal.conf.json', root)))
  const normal = JSON.parse(readFileSync(new URL('desktop/launcher/src-tauri/tauri.conf.json', root)))
  assert.equal(normal.identifier, 'com.saberaltriayi.rehab')
  assert.equal(internal.identifier, 'com.saberaltriayi.rehab.internal')
  assert.match(internal.productName, /内部/)
  assert.notEqual(internal.identifier, normal.identifier)
})
test('compose retains compatible defaults but isolates explicitly selected images and all volumes', () => {
  const compose = readFileSync(new URL('desktop/runtime-template/docker-compose.yml', root), 'utf8')
  assert.ok(compose.includes('${REHAB_SERVER_IMAGE:-rehab-desktop-server:1.0.0}'))
  assert.ok(compose.includes('${REHAB_ADMIN_IMAGE:-rehab-desktop-admin:1.0.0}'))
  for (const suffix of ['mysql-data', 'redis-data', 'rehab-data', 'server-logs']) {
    assert.ok(compose.includes('name: ${REHAB_VOLUME_PREFIX:-rehab-desktop}-' + suffix))
  }
  assert.ok(!compose.includes(':latest'))
  assert.ok(!compose.includes('0.0.0.0'))
})
