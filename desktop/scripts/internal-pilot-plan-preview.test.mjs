import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseSyntheticPlanSeed, preparePilotDraft, prepareSelectedPilotDraft } from './internal-pilot-plan-preview.mjs'

const sql = `INSERT INTO saas_plans (name,max_users,max_patients,motion_quota,storage_quota)
VALUES ('synthetic-beta',10,100,1000,1073741824)
ON DUPLICATE KEY UPDATE status=1;`
const choices = {
  plan: 'B', rolePermissions: 'B', historicalLinks: 'A',
  rollout: 'B', clinicianSignoff: 'A'
}

test('prepares a draft only from the explicit B/B/A/B/A decision set', () => {
  const quota = parseSyntheticPlanSeed(sql)
  assert.deepEqual(quota, {
    maxUsers: 10, maxPatients: 100,
    motionQuota: 1000, storageQuotaBytes: 1073741824
  })
  const draft = preparePilotDraft(quota, choices)
  assert.equal(draft.status, 'DRAFT_NOT_CREATED')
  assert.equal(draft.quotaApprovedForRealTenant, false)
  assert.equal(draft.liveSaasPlanId, null)
  assert.equal(draft.noSqlExecuted, true)
  assert.equal(draft.roles.adminMenuGrants, 10)
  assert.equal(draft.roles.therapistMenuGrants, 8)
  assert.equal(draft.roles.otherTenantsMenuGrants, 0)
  assert.equal(draft.formalReleaseApproved, false)
})

test('ambiguous or changed quota fixture fails instead of guessing a business plan', () => {
  assert.throws(() => parseSyntheticPlanSeed(''), /MISSING_OR_AMBIGUOUS/)
  assert.throws(() => parseSyntheticPlanSeed(sql + '\n' + sql), /MISSING_OR_AMBIGUOUS/)
  assert.throws(() => parseSyntheticPlanSeed(sql.replace(',1000,',',0,')), /INVALID_SYNTHETIC_QUOTA/)
  assert.throws(() => preparePilotDraft(parseSyntheticPlanSeed(sql),
    { ...choices, rolePermissions: 'A' }), /USER_DECISION_SET_NOT_CONFIRMED/)
  assert.throws(() => preparePilotDraft({ maxUsers: -1, maxPatients: 100,
    motionQuota: 1000, storageQuotaBytes: 1000 }, choices), /INVALID_DRAFT/)
})

test('user-approved 20/400/2000 overrides old synthetic limits without inventing storage consent', () => {
  const selected = prepareSelectedPilotDraft(parseSyntheticPlanSeed(sql), choices, {
    maxUsers: 20, maxPatients: 400, motionQuota: 2000, storageQuotaBytes: null
  })
  assert.deepEqual(selected.selectedQuota, {
    maxUsers: 20, maxPatients: 400, motionQuota: 2000, storageQuotaBytes: null
  })
  assert.equal(selected.selectedQuotaApproved.storageQuotaBytes, false)
  assert.equal(selected.provisionalStorageBytesForSyntheticOnly, 1073741824)
  assert.equal(selected.productionPlanCreationAllowed, false)
  assert.equal(selected.storageRequiresOwnerDecision, true)
  assert.equal(selected.trueOwnerMfaCompleted, false)
  assert.equal(selected.formalSigning, 'QA_ONLY_NO_DEVELOPER_ID')
  assert.equal(selected.liveSaasPlanId, null)
  assert.equal(selected.noSqlExecuted, true)
})

test('custom pilot rejects silent storage approval or changed custom limits', () => {
  const baseline = parseSyntheticPlanSeed(sql)
  assert.throws(() => prepareSelectedPilotDraft(baseline, choices, {
    maxUsers: 20, maxPatients: 400, motionQuota: 2000, storageQuotaBytes: 1073741824
  }), /CUSTOM_PILOT_DECISION_NOT_VERIFIED/)
  assert.throws(() => prepareSelectedPilotDraft(baseline, choices, {
    maxUsers: 21, maxPatients: 400, motionQuota: 2000, storageQuotaBytes: null
  }), /CUSTOM_PILOT_DECISION_NOT_VERIFIED/)
})
