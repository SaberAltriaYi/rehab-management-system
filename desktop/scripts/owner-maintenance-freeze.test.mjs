import { test } from 'node:test'
import assert from 'node:assert/strict'
import { inspectOwnerFreeze } from './owner-maintenance-freeze.mjs'

const env = `REHAB_MAIN_PROVISIONING_URL=https://127.0.0.1:18443
REHAB_MAIN_PROVISIONING_ENABLED=false
REHAB_MAIN_PROVISIONING_TOKEN=synthetic-not-real-test-secret`

test('maintenance safely accepts a repaired HTTPS target only while disabled', () => {
  const result = inspectOwnerFreeze(env)
  assert.equal(result.safeForPreMigration, true)
  assert.deepEqual(result.blockers, [])
  assert.deepEqual(result.checks, { explicitDisabled: true, fixedHttpsLoopback: true })
})

test('enabling Owner, using old HTTP or omitting flag blocks live migration', () => {
  for (const altered of [
    env.replace('ENABLED=false', 'ENABLED=true'),
    env.replace('URL=https://127.0.0.1:18443', 'URL=http://127.0.0.1:48080'),
    env.replace('REHAB_MAIN_PROVISIONING_ENABLED=false\n', ''),
    env.replace('URL=https://127.0.0.1:18443', 'URL=https://public.example/tenant')
  ]) assert.equal(inspectOwnerFreeze(altered).safeForPreMigration, false)
})

test('duplicate activation lines are rejected even if the last value is false', () => {
  assert.throws(() => inspectOwnerFreeze(
    'REHAB_MAIN_PROVISIONING_ENABLED=true\n' + env), /DUPLICATE_OWNER_ENV_KEY/)
})
