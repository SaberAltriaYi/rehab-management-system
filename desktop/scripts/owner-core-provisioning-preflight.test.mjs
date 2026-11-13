import { test } from 'node:test'
import assert from 'node:assert/strict'
import { evaluateProvisioningReadiness } from './owner-core-provisioning-preflight.mjs'

const pass = {
  ownerFeatureEnabled: true,
  ownerConfigured: true,
  targetMatchesCurrentCore: true,
  targetReachable: true,
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
