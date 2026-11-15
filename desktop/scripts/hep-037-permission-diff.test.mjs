import { test } from 'node:test'
import assert from 'node:assert/strict'
import { evaluate037PermissionImpact } from './hep-037-permission-diff.mjs'

const baseline = Object.freeze({
  ledger: '036:36', hepTables: 0, existingMenus: 0,
  existingMenuGrants: 0, parentMenuCount: 1,
  superAdminRoles: 1, therapistRoles: 1, sourceVerified: true
})

test('schema readiness does not imply permission escalation can be unattended', () => {
  const result = evaluate037PermissionImpact(baseline)
  assert.equal(result.schemaPreconditionsVerified, true)
  assert.equal(result.requiresRoleReviewBeforeLive037, true)
  assert.equal(result.expectedNewAdminGrants, 10)
  assert.equal(result.expectedNewTherapistGrants, 8)
  assert.equal(result.expectedNewTotalGrants, 18)
})

test('checksum, ledger, menu or role-scope drift fails closed', () => {
  for (const difference of [
    { ledger: '037:37' }, { hepTables: 11 }, { existingMenus: 1 },
    { existingMenuGrants: 1 }, { parentMenuCount: 0 },
    { superAdminRoles: 2 }, { therapistRoles: 0 }, { sourceVerified: false }
  ]) {
    assert.equal(evaluate037PermissionImpact({ ...baseline, ...difference }).schemaPreconditionsVerified, false)
  }
})

test('read-only output contains no identities or medical details', () => {
  const result = JSON.stringify(evaluate037PermissionImpact(baseline))
  for (const privateField of ['tenantId','userId','patientId','roleId','patientName']) {
    assert.ok(!result.includes(privateField))
  }
})
