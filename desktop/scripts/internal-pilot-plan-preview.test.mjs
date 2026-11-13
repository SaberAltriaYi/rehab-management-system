import { test } from 'node:test'
import assert from 'node:assert/strict'
import { parseSyntheticPlanSeed, preparePilotDraft } from './internal-pilot-plan-preview.mjs'

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
