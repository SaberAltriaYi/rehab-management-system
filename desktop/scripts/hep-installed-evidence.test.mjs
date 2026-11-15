import { test } from 'node:test'
import assert from 'node:assert/strict'
import { observeInstalledHep, parseMigrationEvidence } from './hep-installed-evidence.mjs'

const token = 'a'.repeat(40)
const base = '/tmp/synthetic-home'
const inputs = {
  home: base,
  read: path => path.endsWith('VERSION.json')
    ? '{"migrationLedger":"001-037"}'
    : `HEP_CORE_BASE_URL=https://127.0.0.1:18443\nHEP_CORE_INTEGRATION_TOKEN=${token}\nHEP_JWT_ISSUER=http://127.0.0.1:18080\nHEP_JWT_JWKS_URI=https://127.0.0.1:18443/admin-api/rehab/hep-auth/jwks`,
  run: (cmd, args) => {
    assert.equal(cmd, 'docker')
    if (args[0] === 'inspect') return JSON.stringify([
      'REHAB_HEP_BRIDGE_ENABLED=true', `REHAB_HEP_BRIDGE_TOKEN=${token}`,
      'REHAB_HEP_JWT_ISSUER=http://127.0.0.1:18080'
    ])
    if (args[1] === 'rehab-studio-internal-mysql-1') return '37\t037\n10\n10\n'
    if (args[1] === 'rehab-studio-internal-server-1') return 'yes'
    throw new Error('unexpected docker request')
  }
}

test('migration evidence parses fixed read-only fields and rejects malformed responses', () => {
  assert.deepEqual(parseMigrationEvidence('36\t036\n0\n0\n'),
    { count: 36, version: '036', hepTables: 0, hepMenus: 0 })
  assert.throws(() => parseMigrationEvidence('not a query'), /MIGRATION_QUERY_FORMAT_INVALID/)
})

test('live evidence captures actual local integration without exposing shared secret', () => {
  const observed = observeInstalledHep(inputs)
  assert.equal(observed.deployedLedger, '001-037')
  assert.equal(Object.values(observed.checks).every(Boolean), true)
  assert.equal(JSON.stringify(observed).includes(token), false)
})

test('HEP and Core different machine secrets are not accepted', () => {
  const observed = observeInstalledHep({ ...inputs, read: path => path.endsWith('VERSION.json')
    ? '{"migrationLedger":"001-037"}'
    : `HEP_CORE_BASE_URL=https://127.0.0.1:18443\nHEP_CORE_INTEGRATION_TOKEN=${'b'.repeat(40)}\nHEP_JWT_ISSUER=http://127.0.0.1:18080\nHEP_JWT_JWKS_URI=https://127.0.0.1:18443/admin-api/rehab/hep-auth/jwks` })
  assert.equal(observed.checks.bridgeSecretMatched, false)
})

test('outdated installed runtime and absent HEP tables fail closed', () => {
  const observed = observeInstalledHep({ ...inputs, read: path => path.endsWith('VERSION.json')
    ? '{"migrationLedger":"001-036"}' : inputs.read(path),
    run: (cmd, args) => args[1] === 'rehab-studio-internal-mysql-1'
      ? '36\t036\n0\n0\n' : inputs.run(cmd, args) })
  assert.equal(observed.checks.hepSchemaReady, false)
  assert.equal(observed.deployedLedger, '001-036')
})
