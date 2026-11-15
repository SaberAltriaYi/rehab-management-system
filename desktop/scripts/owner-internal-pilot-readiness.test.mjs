import assert from 'node:assert/strict'
import { test } from 'node:test'
import { evaluateInternalPilotReadiness } from './owner-internal-pilot-readiness.mjs'

const license = {
  status: 'APPROVED', provisioning_status: 'NOT_CONFIGURED', main_tenant_id: null,
  license_status: 'ACTIVE', seat_limit: 20, modules_json: '["rehab","motion","report"]'
}
const verified = { applicants: [license], planMatches: 1, packageMatches: 1,
  permissionMatches: 1, ownerCore: { ready: true } }

test('matching synthetic-only pilot reports technical readiness without authorizing writes', () => {
  const result = evaluateInternalPilotReadiness(verified)
  assert.equal(result.technicalPrerequisitesReady, true)
  assert.equal(result.permittedDataScope, 'SYNTHETIC_ONLY')
  assert.equal(result.actualPatientDataUseVerifiedByThisTool, false)
  assert.equal(result.productionWriteAuthorizedByThisTool, false)
  assert.equal(result.formalClinicalReleaseAuthorizedByThisTool, false)
  assert.deepEqual(result.blockers, [])
  assert.deepEqual(result.expectedQuotas, {
    users: 20, patients: 400, assessments: 2000, storageBytes: 5368709120
  })
})

test('Owner unlimited license is not silently interpreted as 20 authorized seats', () => {
  const result = evaluateInternalPilotReadiness({ ...verified,
    applicants: [{ ...license, seat_limit: null }] })
  assert.equal(result.technicalPrerequisitesReady, false)
  assert.deepEqual(result.blockers, ['ownerLicenseSeatLimit20'])
})

test('no or duplicate matching applications fail closed', () => {
  for (const applicants of [[], [license, license], null]) {
    const result = evaluateInternalPilotReadiness({ ...verified, applicants })
    assert.equal(result.checks.exactlyOneDesignatedApplication, false)
    assert.equal(result.technicalPrerequisitesReady, false)
  }
})

test('an approved license is not equivalent to an active Core tenant', () => {
  for (const edit of [
    { status: 'PENDING' }, { provisioning_status: 'ACTIVE', main_tenant_id: 8 },
    { license_status: 'SUSPENDED' }, { modules_json: '["motion"]' },
    { modules_json: 'not-json' }
  ]) {
    const result = evaluateInternalPilotReadiness({ ...verified,
      applicants: [{ ...license, ...edit }] })
    assert.equal(result.technicalPrerequisitesReady, false)
  }
})

test('incorrect Core quota, package or machine identity cannot produce ready', () => {
  for (const change of [
    { planMatches: 0 }, { packageMatches: 0 }, { permissionMatches: 0 },
    { ownerCore: { ready: false } }
  ]) {
    const result = evaluateInternalPilotReadiness({ ...verified, ...change })
    assert.equal(result.technicalPrerequisitesReady, false)
  }
})

test('enabled package without rehab query permission blocks a usable institution', () => {
  const result = evaluateInternalPilotReadiness({ ...verified,
    packageMatches: 1, permissionMatches: 0 })
  assert.equal(result.checks.effectiveCorePermissionPackageVerified, true)
  assert.equal(result.checks.corePermissionPackageHasRehabMotionQuery, false)
  assert.deepEqual(result.blockers, ['corePermissionPackageHasRehabMotionQuery'])
  assert.equal(result.technicalPrerequisitesReady, false)
  assert.equal(evaluateInternalPilotReadiness({ ...verified,
    permissionMatches: undefined }).technicalPrerequisitesReady, false)
})

test('reports never include application UUIDs, names, contact data or credentials', () => {
  const result = evaluateInternalPilotReadiness({
    ...verified, applicants: [{ ...license, id: 'private-uuid',
      organization: 'private-organization', email: 'private@example.invalid',
      machineToken: 'private-machine-token' }]
  })
  const serialized = JSON.stringify(result)
  for (const secret of ['private-uuid', 'private-organization', 'private@example.invalid',
    'private-machine-token']) assert.ok(!serialized.includes(secret))
})
