import { test } from 'node:test'
import assert from 'node:assert/strict'
import { evaluateProvisioningReadiness, provisioningMetadataSql } from './owner-core-provisioning-preflight.mjs'

const pass = {
  ownerFeatureEnabled: true,
  ownerConfigured: true,
  targetMatchesCurrentCore: true,
  targetReachable: true,
  ownerToCoreTlsTrusted: true,
  coreProvisioningEnabled: true,
  coreTokenMatched: true,
  schemaV3Ready: true,
  schemaV4Ready: true,
  livePlanPackageVerified: true
}

test('fully verified readiness passes only the technical preflight', () => {
  assert.equal(evaluateProvisioningReadiness(pass).ready, true)
})

test('stale Owner activation cannot be inferred from a configured URL and machine token', () => {
  const result = evaluateProvisioningReadiness({ ...pass, ownerFeatureEnabled: false })
  assert.equal(result.ready, false)
  assert.deepEqual(result.blockers, ['OWNER_CORE_PROVISIONING_NOT_ACTIVATED'])
  assert.equal(result.checks.ownerFeatureEnabled, false)
  const unspecified = { ...pass }
  delete unspecified.ownerFeatureEnabled
  assert.deepEqual(evaluateProvisioningReadiness(unspecified).blockers,
    ['OWNER_CORE_PROVISIONING_NOT_ACTIVATED'])
})

test('missing any independent readiness check fails closed', () => {
  for (const key of Object.keys(pass)) {
    const state = evaluateProvisioningReadiness({ ...pass, [key]: false })
    assert.equal(state.ready, false, key)
    assert.equal(state.blockers.length, 1, key)
  }
})

test('no endpoint, token, tenant name or any credential enters the preflight result', () => {
  const report = evaluateProvisioningReadiness({
    ...pass, ownerToken: 'synthetic-secret-that-must-never-leak',
    tenantName: 'synthetic test tenant', url: 'https://synthetic.invalid'
  })
  const serialized = JSON.stringify(report)
  for (const word of ['synthetic', 'ownerToken', 'tenantName', 'url']) {
    assert.ok(!serialized.includes(word), word)
  }
})

test('Core provisioning preflight requires an active plan AND an enabled package', () => {
  const sql = provisioningMetadataSql(2, 113)
  assert.match(sql, /saas_plans WHERE id=2 AND status=1/)
  assert.match(sql, /system_tenant_package WHERE id=113 AND status=0 AND deleted=0/)
  assert.ok(!sql.includes('rehab_patient'))
  for (const invalid of [0, -1, Number.NaN, 1.2, Number.MAX_SAFE_INTEGER + 1, '2']) {
    assert.throws(() => provisioningMetadataSql(invalid, 113), /INVALID_PROVISIONING_METADATA_IDS/)
    assert.throws(() => provisioningMetadataSql(2, invalid), /INVALID_PROVISIONING_METADATA_IDS/)
  }
})

test('TCP reachability without Node TLS certificate trust remains NO-GO', () => {
  const result = evaluateProvisioningReadiness({
    ...pass, targetReachable: true, ownerToCoreTlsTrusted: false
  })
  assert.equal(result.ready, false)
  assert.deepEqual(result.blockers, ['OWNER_TO_CORE_TLS_NOT_TRUSTED'])
  assert.equal(result.checks.ownerToCoreTlsTrusted, false)
})
