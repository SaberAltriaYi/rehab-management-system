import { test } from 'node:test'
import assert from 'node:assert/strict'
import { evaluateProvisioningReadiness } from './owner-core-provisioning-preflight.mjs'

const pass = {
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
